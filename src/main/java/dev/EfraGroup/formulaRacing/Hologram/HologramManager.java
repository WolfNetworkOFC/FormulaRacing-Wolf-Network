package dev.EfraGroup.formulaRacing.Hologram;

import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
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

    private static final double LINE_SPACING = 0.25;
    private static final NamespacedKey HOLO_TAG = new NamespacedKey("formularacing", "hologram");

    public HologramManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void createHologram(String name, Location loc, List<String> lines) {
        SchedulerHelper.runTaskAt(plugin, loc, () -> {
            createHologramSync(name, loc, lines);
        });
    }

    public void createHologramSync(String name, Location loc, List<String> lines) {
        if (loc.getWorld() == null) return;

        // Remove ALL armor stands at this location tagged as FR holograms (covers orphans from crashes)
        for (Entity entity : loc.getWorld().getNearbyEntities(loc, 0.5, 5.0, 0.5,
                e -> e instanceof ArmorStand && e.getPersistentDataContainer().has(HOLO_TAG, PersistentDataType.BYTE))) {
            try {
                entity.remove();
            } catch (Exception ignored) {}
        }

        // Also remove from map tracking
        List<Entity> existing = holograms.remove(name);
        hologramLocations.remove(name);
        if (existing != null) {
            for (Entity entity : existing) {
                if (entity != null && entity.isValid()) {
                    try { entity.remove(); } catch (Exception ignored) {}
                }
            }
        }

        List<Entity> stands = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Location standLoc = loc.clone().add(0, (lines.size() - 1 - i) * LINE_SPACING, 0);
            ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(standLoc, EntityType.ARMOR_STAND);
            stand.setVisible(false);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setCustomNameVisible(true);
            stand.setCustomName(lines.get(i));
            stand.getPersistentDataContainer().set(HOLO_TAG, PersistentDataType.BYTE, (byte) 1);
            stands.add(stand);
        }
        holograms.put(name, stands);
        hologramLocations.put(name, loc.clone());
    }

    public void updateHologram(String name, List<String> lines) {
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
                stand.setCustomName(lines.get(i));

                // Reposition in case line count changed
                Location expectedLoc = baseLoc.clone().add(0, (lines.size() - 1 - i) * LINE_SPACING, 0);
                if (!stand.getLocation().equals(expectedLoc)) {
                    SchedulerHelper.teleport(stand, expectedLoc);
                }
            }
        });
    }

    public void deleteHologram(String name) {
        List<Entity> stands = holograms.remove(name);
        hologramLocations.remove(name);
        if (stands != null) {
            for (Entity entity : stands) {
                if (entity != null) {
                    SchedulerHelper.runTaskFor(plugin, entity, () -> {
                        if (entity.isValid()) {
                    try {
                        entity.remove();
                    } catch (Exception ignored) {
                    }
                        }
                    });
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
                    try {
                        entity.remove();
                    } catch (Exception ignored) {
                        // Folia: world data may already be null during shutdown
                    }
                }
            }
        }
    }
}
