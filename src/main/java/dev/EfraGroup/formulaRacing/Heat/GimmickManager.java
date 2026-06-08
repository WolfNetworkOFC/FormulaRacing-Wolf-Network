package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GimmickManager {

    private final FormulaRacing plugin;
    private final Map<Integer, List<GimmickConfig>> heatGimmicks = new ConcurrentHashMap<>();
    private final Map<UUID, GimmickConfig> playerClipboard = new ConcurrentHashMap<>();
    private final Map<String, Object> pasteOperations = new ConcurrentHashMap<>();

    public GimmickManager(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    public void setClipboard(Player player, GimmickConfig config) {
        playerClipboard.put(player.getUniqueId(), config);
    }

    public GimmickConfig getClipboard(Player player) {
        return playerClipboard.get(player.getUniqueId());
    }

    public void clearClipboard(Player player) {
        playerClipboard.remove(player.getUniqueId());
    }

    public void addGimmick(int heatId, GimmickConfig config) {
        heatGimmicks.computeIfAbsent(heatId, k -> Collections.synchronizedList(new ArrayList<>())).add(config);
    }

    public boolean removeGimmick(int heatId, String schematicName) {
        List<GimmickConfig> gimmicks = heatGimmicks.get(heatId);
        if (gimmicks == null) return false;
        return gimmicks.removeIf(g -> g.getSchematicName().equalsIgnoreCase(schematicName));
    }

    public List<GimmickConfig> getGimmicksForHeat(int heatId) {
        return heatGimmicks.getOrDefault(heatId, Collections.emptyList());
    }

    public void clearGimmicks(int heatId) {
        List<GimmickConfig> gimmicks = heatGimmicks.remove(heatId);
        if (gimmicks != null) {
            for (GimmickConfig g : gimmicks) {
                removeGimmickBlocks(g);
            }
        }
    }

    public void triggerGimmicks(int heatId, int currentLap) {
        List<GimmickConfig> gimmicks = heatGimmicks.get(heatId);
        if (gimmicks == null || gimmicks.isEmpty()) return;

        List<GimmickConfig> toTrigger = new ArrayList<>();
        List<GimmickConfig> toRemove = new ArrayList<>();

        synchronized (gimmicks) {
            for (GimmickConfig g : gimmicks) {
                if (!g.isEnabled()) continue;
                if (g.getTriggerLap() == currentLap) {
                    toTrigger.add(g);
                }
                if (g.shouldRemoveOnLap(currentLap)) {
                    toRemove.add(g);
                }
            }
        }

        for (GimmickConfig g : toTrigger) {
            pasteGimmick(g);
            broadcastAnnounce(g.getAnnounceMessage());
        }

        for (GimmickConfig g : toRemove) {
            removeGimmickBlocks(g);
            gimmicks.remove(g);
        }
    }

    public void pasteGimmick(GimmickConfig config) {
        if (config.getPasteLocation() == null || config.getSchematicName() == null) return;

        SchedulerHelper.runTask(plugin, () -> {
            try {
                Location loc = config.getPasteLocation();
                if (loc.getWorld() == null) return;

                File schematicFile = findSchematicFile(config.getSchematicName());
                if (schematicFile == null || !schematicFile.exists()) {
                    logDebug("[Gimmick] Schematic file not found: " + config.getSchematicName());
                    return;
                }

                Object operation = pasteSchematic(schematicFile, loc);
                if (operation != null) {
                    pasteOperations.put(config.getSchematicName(), operation);
                    logDebug("[Gimmick] Pasted schematic '" + config.getSchematicName() + "' at " +
                            loc.getWorld().getName() + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ());
                }
            } catch (Exception e) {
                logDebug("[Gimmick] Error pasting schematic: " + e.getMessage());
            }
        });
    }

    public void removeGimmickBlocks(GimmickConfig config) {
        if (config.getSchematicName() == null) return;

        SchedulerHelper.runTask(plugin, () -> {
            try {
                Object operation = pasteOperations.remove(config.getSchematicName());
                if (operation != null) {
                    undoOperation(operation);
                    logDebug("[Gimmick] Removed schematic '" + config.getSchematicName() + "'");
                }
            } catch (Exception e) {
                logDebug("[Gimmick] Error removing schematic: " + e.getMessage());
            }
        });
    }

    private File findSchematicFile(String schematicName) {
        String name = schematicName.endsWith(".schem") ? schematicName : schematicName + ".schem";

        File faweDir = new File("plugins/FastAsyncWorldEdit/schematics/" + name);
        if (faweDir.exists()) return faweDir;

        File faweDir2 = new File("plugins/FAWE/schematics/" + name);
        if (faweDir2.exists()) return faweDir2;

        File weDir = new File("plugins/WorldEdit/schematics/" + name);
        if (weDir.exists()) return weDir;

        return null;
    }

    private Object pasteSchematic(File schematicFile, Location location) {
        try {
            Class<?> clipboardFormats = Class.forName("com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats");
            Method findByFile = clipboardFormats.getMethod("findByFile", File.class);
            Object format = findByFile.invoke(null, schematicFile);

            if (format == null) {
                logDebug("[Gimmick] Could not determine schematic format for: " + schematicFile.getName());
                return null;
            }

            Method getReader = format.getClass().getMethod("getReader", java.io.InputStream.class);
            Object reader = getReader.invoke(null, new FileInputStream(schematicFile));

            Class<?> clipboardClass = Class.forName("com.sk89q.worldedit.extent.clipboard.Clipboard");
            Method readMethod = reader.getClass().getMethod("read");
            Object clipboard = readMethod.invoke(reader);
            try { reader.getClass().getMethod("close").invoke(reader); } catch (Exception ignored) {}

            Object worldEdit = Class.forName("com.sk89q.worldedit.WorldEdit").getMethod("getInstance").invoke(null);

            Object bukkitWorld = location.getWorld();
            Object weWorld = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter")
                    .getMethod("adapt", org.bukkit.World.class).invoke(null, bukkitWorld);

            Class<?> editSessionClass;
            Object editSession;
            try {
                Class<?> editSessionBuilderClass = Class.forName("com.sk89q.worldedit.EditSessionBuilder");
                Class<?> worldClass = Class.forName("com.sk89q.worldedit.world.World");
                Object builder = editSessionBuilderClass.getMethod("world", worldClass).invoke(null, weWorld);
                builder = builder.getClass().getMethod("maxBlocks", int.class).invoke(builder, -1);
                builder = builder.getClass().getMethod("build").invoke(builder);
                editSession = builder;
                editSessionClass = builder.getClass();
            } catch (Exception e) {
                editSession = worldEdit.getClass().getMethod("newEditSession", Class.forName("com.sk89q.worldedit.world.World"))
                        .invoke(worldEdit, weWorld);
                editSessionClass = editSession.getClass();
            }

            Class<?> vectorClass = Class.forName("com.sk89q.worldedit.math.BlockVector3");
            Object targetVector = vectorClass.getMethod("at", double.class, double.class, double.class)
                    .invoke(null, location.getX(), location.getY(), location.getZ());

            Class<?> clipboardHolderClass = Class.forName("com.sk89q.worldedit.session.ClipboardHolder");
            Object clipboardHolder = clipboardHolderClass.getConstructor(clipboardClass).newInstance(clipboard);

            Method pasteMethod = clipboardHolderClass.getMethod("createPaste", editSessionClass);
            Object builder = pasteMethod.invoke(clipboardHolder, editSession);
            builder = builder.getClass().getMethod("to", vectorClass).invoke(builder, targetVector);
            builder = builder.getClass().getMethod("ignoreAirBlocks", boolean.class).invoke(builder, true);
            builder = builder.getClass().getMethod("build").invoke(builder);

            Object operation = builder.getClass().getMethod("call").invoke(builder);

            try {
                editSessionClass.getMethod("flushQueue").invoke(editSession);
            } catch (Exception ignored) {}

            return operation;

        } catch (Exception e) {
            logDebug("[Gimmick] Reflection error pasting schematic: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    private void undoOperation(Object operation) {
        try {
            Method undoMethod = operation.getClass().getMethod("undo");
            undoMethod.invoke(operation);
        } catch (Exception e) {
            logDebug("[Gimmick] Error undoing operation: " + e.getMessage());
        }
    }

    private void broadcastAnnounce(String message) {
        if (message == null || message.isEmpty()) return;

        String formatted = message.replace("&", "§");
        Bukkit.broadcastMessage("§6=============== §f§lAnuncio §6===============");
        Bukkit.broadcastMessage("§f" + formatted);
        Bukkit.broadcastMessage("§6=======================================");

        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
        }
    }

    private void logDebug(String message) {
        DebugManager dm = plugin.getDebugManager();
        if (dm != null) {
            dm.logRaceSystem(message);
        }
    }
}
