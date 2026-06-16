package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Item Boxes (Mario Kart style) for heat races.
 *
 * <p>Each track can have multiple item box locations. When a heat loads,
 * item boxes spawn as glowing particle spheres. When a driver passes through
 * one, they receive a random power-up. The box disappears and respawns after
 * a configurable cooldown.</p>
 */
public class ItemBoxManager {

    private final FormulaRacing plugin;

    // ── Per-track item box data ──
    // trackNameWS → list of box entries
    private final Map<String, List<ItemBoxEntry>> trackBoxes = new ConcurrentHashMap<>();

    // ── Active heat box states ──
    // heatId → set of currently active (visible) box indices
    private final Map<Integer, Set<Integer>> activeBoxes = new ConcurrentHashMap<>();

    // ── Cooldown tracking ──
    // heatId → (boxIndex → respawnTask)
    private final Map<Integer, Map<Integer, FRTask>> respawnTasks = new ConcurrentHashMap<>();

    // ── Particle animation tasks ──
    private final Map<Integer, FRTask> animationTasks = new ConcurrentHashMap<>();

    // ── Player cooldowns (prevent double-collection) ──
    // UUID → last collection tick
    private final Map<UUID, Long> playerCooldowns = new ConcurrentHashMap<>();

    private static final long PLAYER_COOLDOWN_TICKS = 10; // 0.5s cooldown per player
    private static final double BOX_RADIUS = 2.0;
    private static final int ANIMATION_PERIOD = 5; // ticks between particle spawns

    public ItemBoxManager(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Data structures
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Represents a single item box location on a track.
     */
    public static class ItemBoxEntry {
        public final int dbId;
        public final Location location;
        public final double radius;

        public ItemBoxEntry(int dbId, Location location, double radius) {
            this.dbId = dbId;
            this.location = location;
            this.radius = radius;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Loading / Unloading
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Load item boxes for a track from the database.
     */
    public void loadTrackBoxes(String trackNameWS) {
        trackBoxes.remove(trackNameWS);
        List<double[]> raw = plugin.getDatabaseManager().getItemBoxes(trackNameWS);
        if (raw.isEmpty()) return;

        List<ItemBoxEntry> entries = new ArrayList<>();
        for (double[] data : raw) {
            World world = Bukkit.getWorld(trackNameWS); // best effort
            // We need the world name — stored in DB but not in the double array.
            // Re-fetch with world info via a dedicated query would be cleaner,
            // but for now we'll use the track's world from the first online player.
            // The actual world is resolved in spawnBoxesForHeat().
            Location loc = new Location(null, data[0], data[1], data[2]);
            entries.add(new ItemBoxEntry((int) data[4], loc, data[3]));
        }
        trackBoxes.put(trackNameWS, entries);
        logDebug("[ItemBox] Loaded " + entries.size() + " boxes for track " + trackNameWS);
    }

    /**
     * Spawn item boxes for a heat. Called from Heats.loadHeat().
     */
    public void spawnBoxesForHeat(Heats heat) {
        if (heat == null) return;
        String track = heat.getTrackNameWS();
        List<ItemBoxEntry> boxes = trackBoxes.get(track);
        if (boxes == null || boxes.isEmpty()) {
            // Try loading from DB
            loadTrackBoxes(track);
            boxes = trackBoxes.get(track);
            if (boxes == null || boxes.isEmpty()) return;
        }

        int heatId = heat.getId();
        Set<Integer> active = ConcurrentHashMap.newKeySet();

        // Resolve world from the heat's drivers
        World world = null;
        for (var driver : heat.getDrivers().values()) {
            Player p = Bukkit.getPlayer(driver.getUuid());
            if (p != null && p.isOnline()) {
                world = p.getWorld();
                break;
            }
        }
        if (world == null) {
            logDebug("[ItemBox] Cannot spawn boxes — no online players to determine world");
            return;
        }

        // Create properly located entries with the resolved world
        List<ItemBoxEntry> resolvedEntries = new ArrayList<>();
        for (ItemBoxEntry entry : boxes) {
            Location loc = new Location(world, entry.location.getX(), entry.location.getY(), entry.location.getZ());
            resolvedEntries.add(new ItemBoxEntry(entry.dbId, loc, entry.radius));
        }

        // Replace track entries with resolved ones
        trackBoxes.put(track, resolvedEntries);

        // Mark all as active
        for (int i = 0; i < resolvedEntries.size(); i++) {
            active.add(i);
        }
        activeBoxes.put(heatId, active);

        // Start animation task
        startAnimationTask(heatId, resolvedEntries);

        logDebug("[ItemBox] Spawned " + active.size() + " boxes for heat " + heatId);
    }

    /**
     * Remove all item boxes for a heat. Called from Heats.finishHeat().
     */
    public void clearBoxesForHeat(int heatId) {
        // Cancel animation
        FRTask anim = animationTasks.remove(heatId);
        if (anim != null) anim.cancel();

        // Cancel all respawn tasks
        Map<Integer, FRTask> tasks = respawnTasks.remove(heatId);
        if (tasks != null) {
            for (FRTask t : tasks.values()) {
                if (t != null) t.cancel();
            }
        }

        activeBoxes.remove(heatId);
        logDebug("[ItemBox] Cleared boxes for heat " + heatId);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Collection detection
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Check if a player is near any active item box and collect it.
     * Called from the movement listener.
     * Returns the power collected, or null if no box was collected.
     */
    public ItemPower checkCollection(Player player, int heatId, int totalDrivers) {
        if (player == null || !player.isOnline()) return null;

        // Player cooldown check
        long now = player.getWorld().getGameTime();
        Long lastCollect = playerCooldowns.get(player.getUniqueId());
        if (lastCollect != null && (now - lastCollect) < PLAYER_COOLDOWN_TICKS) {
            return null;
        }

        Set<Integer> active = activeBoxes.get(heatId);
        if (active == null || active.isEmpty()) return null;

        String track = plugin.getDriverLookup().getHeat(player.getUniqueId()) != null
                ? plugin.getDriverLookup().getHeat(player.getUniqueId()).getTrackNameWS() : null;
        if (track == null) return null;

        List<ItemBoxEntry> boxes = trackBoxes.get(track);
        if (boxes == null) return null;

        Location playerLoc = player.getLocation();
        for (int idx : active) {
            if (idx < 0 || idx >= boxes.size()) continue;
            ItemBoxEntry box = boxes.get(idx);
            if (box.location.getWorld() == null) continue;
            if (!box.location.getWorld().equals(playerLoc.getWorld())) continue;

            double distSq = playerLoc.distanceSquared(box.location);
            double r = box.radius > 0 ? box.radius : BOX_RADIUS;
            if (distSq <= r * r) {
                // COLLECT!
                active.remove(idx);
                playerCooldowns.put(player.getUniqueId(), now);

                // Schedule respawn
                int respawnTicks = getRespawnTicks(heatId);
                if (respawnTicks > 0) {
                    Map<Integer, FRTask> heatTasks = respawnTasks
                            .computeIfAbsent(heatId, k -> new ConcurrentHashMap<>());
                    FRTask old = heatTasks.get(idx);
                    if (old != null) old.cancel();

                    FRTask task = SchedulerHelper.runTaskLater(plugin, () -> {
                        Set<Integer> act = activeBoxes.get(heatId);
                        if (act != null) act.add(idx);
                        Map<Integer, FRTask> ht = respawnTasks.get(heatId);
                        if (ht != null) ht.remove(idx);
                        logDebug("[ItemBox] Box " + idx + " respawned for heat " + heatId);
                    }, respawnTicks);
                    heatTasks.put(idx, task);
                }

                // Determine power based on position
                var heat = plugin.getDriverLookup().getHeat(player.getUniqueId());
                int position = 1;
                if (heat != null) {
                    var driver = heat.getDriver(player.getUniqueId());
                    if (driver != null) position = driver.getPosition();
                }
                ItemPower power = ItemPower.randomPowerForPosition(position, totalDrivers);

                // Apply effect
                power.apply(player, plugin);

                // Visual feedback at box location
                spawnCollectionEffect(box.location);

                // Notify player
                player.sendMessage("§6§l[ITEM BOX] " + power.getDisplayName());
                player.sendMessage("§7  " + power.getEffectMessage());
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.5f, 2.0f);

                logDebug("[ItemBox] " + player.getName() + " collected " + power.name()
                        + " at box " + idx + " (heat " + heatId + ")");

                return power;
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Particle animation
    // ═══════════════════════════════════════════════════════════════════

    private void startAnimationTask(int heatId, List<ItemBoxEntry> boxes) {
        FRTask old = animationTasks.remove(heatId);
        if (old != null) old.cancel();

        FRTask task = SchedulerHelper.runTaskTimer(plugin, () -> {
            Set<Integer> active = activeBoxes.get(heatId);
            if (active == null) return;

            for (int idx : active) {
                if (idx < 0 || idx >= boxes.size()) continue;
                ItemBoxEntry box = boxes.get(idx);
                if (box.location.getWorld() == null) continue;
                if (!box.location.getWorld().isChunkLoaded(
                        box.location.getBlockX() >> 4, box.location.getBlockZ() >> 4)) continue;

                // Spinning particle effect — enchanted hit particles in a circle
                double r = box.radius > 0 ? box.radius : BOX_RADIUS;
                Location center = box.location.clone().add(0, 1.0, 0);
                long tick = center.getWorld().getGameTime();

                // Rotating particles
                for (int i = 0; i < 8; i++) {
                    double angle = (tick * 0.15) + (i * Math.PI * 2.0 / 8.0);
                    double px = center.getX() + Math.cos(angle) * r * 0.7;
                    double pz = center.getZ() + Math.sin(angle) * r * 0.7;
                    double py = center.getY() + Math.sin(tick * 0.1 + i) * 0.3;
                    center.getWorld().spawnParticle(Particle.ENCHANTED_HIT,
                            px, py, pz, 1, 0, 0, 0, 0);
                }

                // Center glow
                center.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                        center.getX(), center.getY(), center.getZ(),
                        2, 0.2, 0.2, 0.2, 0);

                // Occasional note sound
                if (tick % 40 == 0) {
                    for (Player p : center.getWorld().getPlayers()) {
                        if (p.getLocation().distanceSquared(center) < 64) {
                            p.playSound(center, Sound.BLOCK_NOTE_BLOCK_BELL, 0.3f, 1.5f);
                        }
                    }
                }
            }
        }, 0L, ANIMATION_PERIOD);

        animationTasks.put(heatId, task);
    }

    private void spawnCollectionEffect(Location loc) {
        if (loc.getWorld() == null) return;
        // Burst of particles when collected
        loc.getWorld().spawnParticle(Particle.FIREWORK, loc.clone().add(0, 1, 0),
                30, 0.5, 0.5, 0.5, 0.2);
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 1, 0),
                3, 0.2, 0.2, 0.2, 0);
        loc.getWorld().spawnParticle(Particle.ENCHANTED_HIT, loc.clone().add(0, 1, 0),
                20, 0.5, 0.5, 0.5, 0.5);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helpers
    // ═══════════════════════════════════════════════════════════════════

    private int getRespawnTicks(int heatId) {
        // Default 200 ticks = 10 seconds
        return 200;
    }

    public void setRespawnTicks(int heatId, int ticks) {
        // Stored per-heat in the heat's respawn config
        // For now, we use the heat's itemBoxRespawnTicks field
    }

    public int getBoxCount(String trackNameWS) {
        List<ItemBoxEntry> boxes = trackBoxes.get(trackNameWS);
        return boxes != null ? boxes.size() : 0;
    }

    public List<ItemBoxEntry> getBoxes(String trackNameWS) {
        return trackBoxes.getOrDefault(trackNameWS, Collections.emptyList());
    }

    /**
     * Add a box location to the in-memory cache (after saving to DB).
     */
    public void addBoxToCache(String trackNameWS, ItemBoxEntry entry) {
        trackBoxes.computeIfAbsent(trackNameWS, k -> new ArrayList<>()).add(entry);
    }

    /**
     * Remove a box from the in-memory cache.
     */
    public boolean removeBoxFromCache(String trackNameWS, int dbId) {
        List<ItemBoxEntry> boxes = trackBoxes.get(trackNameWS);
        if (boxes == null) return false;
        return boxes.removeIf(e -> e.dbId == dbId);
    }

    /**
     * Clear all cached data (on plugin disable).
     */
    public void shutdown() {
        for (FRTask t : animationTasks.values()) {
            if (t != null) t.cancel();
        }
        for (Map<Integer, FRTask> tasks : respawnTasks.values()) {
            for (FRTask t : tasks.values()) {
                if (t != null) t.cancel();
            }
        }
        animationTasks.clear();
        respawnTasks.clear();
        activeBoxes.clear();
        trackBoxes.clear();
        playerCooldowns.clear();
    }

    private void logDebug(String message) {
        DebugManager dm = plugin.getDebugManager();
        if (dm != null) dm.logRaceSystem(message);
    }
}
