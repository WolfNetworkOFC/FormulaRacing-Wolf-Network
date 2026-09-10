package dev.EfraGroup.formulaRacing.Hologram;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.PlatformUtils;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import me.clip.placeholderapi.PlaceholderAPI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class HologramManager {

    private final JavaPlugin plugin;
    private final Map<String, List<Entity>> holograms = new ConcurrentHashMap<>();
    private final Map<String, Location> hologramLocations = new ConcurrentHashMap<>();
    private final Map<String, HologramBackend> hdHolograms = new ConcurrentHashMap<>();
    private final boolean useHolographicDisplays;

    private static final double LINE_SPACING = 0.25;
    private static final NamespacedKey HOLO_TAG = new NamespacedKey("formularacing", "hologram");

    private static String resolvePAPI(String text) {
        return PlaceholderAPI.setPlaceholders(null, text);
    }

    public HologramManager(JavaPlugin plugin) {
        this.plugin = plugin;
        boolean selected = false;
        if (plugin instanceof FormulaRacing) {
            var config = ((FormulaRacing) plugin).getConfig();
            if (config != null && config.isSet("holograms.backend")) {
                String backendType = config.getString("holograms.backend", "armorstand").toLowerCase();
                // HolographicDisplays is NOT Folia-compatible, so never enable it on Folia.
                boolean folia = isFolia();
                selected = "holographicdisplays".equals(backendType)
                    && !folia
                    && plugin.getServer().getPluginManager().getPlugin("HolographicDisplays") != null;
            }
        }
        this.useHolographicDisplays = selected;
    }

    private static boolean isFolia() {
        try {
            return PlatformUtils.isFoliaRuntime();
        } catch (Throwable t) {
            return false;
        }
    }

    public void createHologram(String name, Location loc, List<String> lines) {
        plugin.getLogger().info("[Hologram] Creating hologram '" + name + "' at " + loc + " with " + lines.size() + " lines");
        SchedulerHelper.runTaskAt(plugin, loc, () -> {
            createHologramSync(name, loc, lines);
        });
    }

    public void createHologramSync(String name, Location loc, List<String> lines) {
        if (loc == null || loc.getWorld() == null) {
            plugin.getLogger().warning("[Hologram] Cannot create hologram '" + name + "': location or world is null");
            return;
        }

        // Reuse existing stands: just update names in-place (avoids Folia deferred entity removal)
        List<Entity> stands = holograms.get(name);
        if (stands != null && !stands.isEmpty() && stands.get(0) != null && stands.get(0).isValid()) {
            plugin.getLogger().info("[Hologram] Reusing existing stands for '" + name + "'");
            updateHologramList(stands, name, loc, lines);
            return;
        }

        plugin.getLogger().info("[Hologram] Creating new stands for '" + name + "' at " + loc.getWorld().getName());

        // Ensure chunk is loaded so getNearbyEntities can find orphan stands from prior sessions
        if (!loc.getChunk().isLoaded()) {
            loc.getChunk().load();
        }

        // Remove any orphan ArmorStand tagged as ours at this location (left from crashes or failed shutdown cleanup)
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 0.5, 5.0, 0.5,
                e -> e instanceof ArmorStand && e.getPersistentDataContainer().has(HOLO_TAG, PersistentDataType.BYTE))) {
            try {
                entity.remove();
            } catch (Throwable ignored) {}
        }

        holograms.remove(name);
        hologramLocations.remove(name);

        stands = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Location standLoc = loc.clone().add(0, (lines.size() - 1 - i) * LINE_SPACING, 0);
            try {
                ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(standLoc, EntityType.ARMOR_STAND);
                stand.setVisible(false);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setCustomNameVisible(true);
                stand.setCustomName(resolvePAPI(lines.get(i)));
                stand.getPersistentDataContainer().set(HOLO_TAG, PersistentDataType.BYTE, (byte) 1);
                stands.add(stand);
            } catch (Exception e) {
                plugin.getLogger().warning("[Hologram] Failed to create stand " + i + " for '" + name + "': " + e.getMessage());
            }
        }
        holograms.put(name, stands);
        hologramLocations.put(name, loc.clone());
        plugin.getLogger().info("[Hologram] Created " + stands.size() + " stands for '" + name + "'");
    }

    private void updateHologramList(List<Entity> stands, String name, Location baseLoc, List<String> lines) {
        // Adjust count
        if (stands.size() > lines.size()) {
            for (int i = stands.size() - 1; i >= lines.size(); i--) {
                try { stands.get(i).remove(); } catch (Exception ignored) {}
                stands.remove(i);
            }
        } else if (stands.size() < lines.size()) {
            for (int i = stands.size(); i < lines.size(); i++) {
                Location standLoc = baseLoc.clone().add(0, (lines.size() - 1 - i) * LINE_SPACING, 0);
                ArmorStand stand = (ArmorStand) baseLoc.getWorld().spawnEntity(standLoc, EntityType.ARMOR_STAND);
                stand.setVisible(false);
                stand.setMarker(true);
                stand.setGravity(false);
                stand.setInvulnerable(true);
                stand.setCustomNameVisible(true);
                stand.getPersistentDataContainer().set(HOLO_TAG, PersistentDataType.BYTE, (byte) 1);
                stands.add(stand);
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            ArmorStand stand = (ArmorStand) stands.get(i);
            stand.setCustomName(resolvePAPI(lines.get(i)));
            Location expectedLoc = baseLoc.clone().add(0, (lines.size() - 1 - i) * LINE_SPACING, 0);
            if (!stand.getLocation().equals(expectedLoc)) {
                SchedulerHelper.teleport(stand, expectedLoc);
            }
        }
        holograms.put(name, stands);
        hologramLocations.put(name, baseLoc.clone());
    }

    /**
     * Creates a hologram using the configured backend. When HolographicDisplays is
     * selected and available, it is used; otherwise the built-in ArmorStand path
     * is used (default behaviour).
     */
    public void createHologramSelectable(String name, Location loc, List<String> lines) {
        if (useHolographicDisplays) {
            HolographicDisplaysBackend backend = new HolographicDisplaysBackend(name, loc);
            backend.create(name, loc, lines);
            hdHolograms.put(name, backend);
        } else {
            createHologram(name, loc, lines);
        }
    }

    public void updateHologram(String name, List<String> lines) {
        HologramBackend hd = hdHolograms.get(name);
        if (hd != null) {
            hd.updateLines(lines);
            return;
        }
        Location baseLoc = hologramLocations.get(name);
        if (baseLoc == null || baseLoc.getWorld() == null) return;

        SchedulerHelper.runTaskAt(plugin, baseLoc, () -> {
            List<Entity> stands = holograms.get(name);
            if (baseLoc.getWorld() == null) return;
            if (stands == null) return;

            // Adjust stand count to match line count
            if (stands.size() > lines.size()) {
                for (int i = stands.size() - 1; i >= lines.size(); i--) {
                    stands.get(i).remove();
                    stands.remove(i);
                }
            } else if (stands.size() < lines.size()) {
                for (int i = stands.size(); i < lines.size(); i++) {
                    Location standLoc = baseLoc.clone().add(0, (lines.size() - 1 - i) * LINE_SPACING, 0);
                    ArmorStand stand = (ArmorStand) baseLoc.getWorld().spawnEntity(standLoc, EntityType.ARMOR_STAND);
                    stand.setVisible(false);
                    stand.setMarker(true);
                    stand.setGravity(false);
                    stand.setInvulnerable(true);
                    stand.setCustomNameVisible(true);
                    stand.getPersistentDataContainer().set(HOLO_TAG, PersistentDataType.BYTE, (byte) 1);
                    stands.add(stand);
                }
            }

            for (int i = 0; i < lines.size(); i++) {
                Entity entity = stands.get(i);
                ArmorStand stand = (ArmorStand) entity;
                stand.setCustomName(resolvePAPI(lines.get(i)));

                // Reposition in case line count changed
                Location expectedLoc = baseLoc.clone().add(0, (lines.size() - 1 - i) * LINE_SPACING, 0);
                if (!stand.getLocation().equals(expectedLoc)) {
                    SchedulerHelper.teleport(stand, expectedLoc);
                }
            }
        });
    }

    public void deleteHologram(String name) {
        HologramBackend hd = hdHolograms.remove(name);
        if (hd != null) {
            hd.remove();
            hologramLocations.remove(name);
            return;
        }
        List<Entity> stands = holograms.remove(name);
        hologramLocations.remove(name);
        if (stands != null) {
            for (Entity entity : stands) {
                if (entity != null) {
                    if (plugin.isEnabled()) {
                        SchedulerHelper.runTaskFor(plugin, entity, () -> {
                            if (entity.isValid()) {
                                try {
                                    entity.remove();
                                } catch (Exception ignored) {
                                }
                            }
                        });
                    } else {
                        // During shutdown, scheduler tasks are retired — remove directly
                        try {
                            if (entity.isValid()) entity.remove();
                        } catch (Throwable ignored) {
                        }
                    }
                }
            }
        }
    }

    public void moveHologram(String name, Location newLoc) {
        SchedulerHelper.runTaskAt(plugin, newLoc, () -> {
            List<Entity> stands = holograms.get(name);
            if (stands == null || newLoc.getWorld() == null) return;

            hologramLocations.put(name, newLoc.clone());
            for (int i = 0; i < stands.size(); i++) {
                Location standLoc = newLoc.clone().add(0, (stands.size() - 1 - i) * LINE_SPACING, 0);
                SchedulerHelper.teleport(stands.get(i), standLoc);
            }
        });
    }

    public boolean hasHologram(String name) {
        return holograms.containsKey(name);
    }

    public Location getHologramLocation(String name) {
        return hologramLocations.get(name);
    }

    public void deleteAll() {
        for (HologramBackend hd : hdHolograms.values()) {
            hd.remove();
        }
        hdHolograms.clear();
        // Snapshot the lists to avoid concurrent modification
        List<Entity> allStands = new ArrayList<>();
        for (List<Entity> stands : holograms.values()) {
            allStands.addAll(stands);
        }
        holograms.clear();
        hologramLocations.clear();

        // Remove each entity on its own region thread
        for (Entity entity : allStands) {
            SchedulerHelper.runTaskFor(plugin, entity, () -> {
                if (entity.isValid()) {
                    entity.remove();
                }
            });
        }
    }

    /**
     * Scans all loaded worlds and removes every ArmorStand tagged as a FormulaRacing hologram.
     * Called on plugin shutdown to ensure no hologram is left behind even if it was
     * disabled (/te togglebedrock / togglejava) or somehow orphaned from the internal map.
     */
    public static void removeAllHologramStands() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (entity.getPersistentDataContainer().has(HOLO_TAG, PersistentDataType.BYTE)) {
                    if (entity.isValid()) {
                        try {
                            entity.remove();
                        } catch (Throwable ignored) {
                            // Folia: entity.remove() must be called from the owning region thread.
                            // During shutdown this may fail — the startup orphan scan handles it.
                        }
                    }
                }
            }
        }
    }

    public static void removeOrphanStands() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (entity.getPersistentDataContainer().has(HOLO_TAG, PersistentDataType.BYTE) && entity.isValid()) {
                    entity.getScheduler().run(FormulaRacing.getInstance(), t -> entity.remove(), null);
                }
            }
        }
    }
}
