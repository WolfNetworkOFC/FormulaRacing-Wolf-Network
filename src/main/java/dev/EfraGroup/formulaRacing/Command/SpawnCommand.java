package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

@CommandAlias("spawn|frspawn|lobby")
@Description("Teleporta para o spawn (remove o barco antes).")
public class SpawnCommand extends BaseCommand {

    private final FormulaRacing plugin;

    public SpawnCommand(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @Default
    public void onSpawn(Player player) {
        Location spawn = getSpawnLocation();
        if (spawn == null) {
            player.sendMessage("§cSpawn não configurado! Um admin precisa usar /spawn set ou ajustar o config.yml.");
            return;
        }

        // 1. Deleta o barco do jogador (sai do veículo + remove entidade + limpa mapas)
        try {
            plugin.getAPI().recoverPlayerBoatState(player);
        } catch (Exception e) {
            plugin.getLogger().warning("[FormulaRacing] Falha ao remover barco no /spawn: " + e.getMessage());
        }
        try {
            plugin.getAPI().removePlayerBoat(player.getUniqueId());
        } catch (Exception ignored) {
        }

        // 2. Limpa estado de time trial/duelo/scoreboard (mesmo que SpawnBoatCleanupListener)
        cleanupPlayerState(player);

        // 3. Teleporta para o spawn (Folia-safe) e restaura a hotbar padrão
        SchedulerHelper.teleportAsync(player, spawn).thenAccept(success -> {
            if (Boolean.TRUE.equals(success) && player.isOnline()) {
                SchedulerHelper.runTaskFor(plugin, player, () -> {
                    if (player.isOnline() && plugin.getHotbarController() != null) {
                        plugin.getHotbarController().giveHotbarItems(player);
                    }
                });
                player.sendMessage("§aTeleportado para o spawn!");
            } else if (player.isOnline()) {
                player.sendMessage("§cFalha ao teleportar para o spawn.");
            }
        });
    }

    @Subcommand("set")
    @CommandPermission("formularacing.admin")
    @Description("Define o spawn na sua posição atual (salva no config.yml).")
    public void onSet(Player player) {
        Location loc = player.getLocation();
        FileConfiguration config = plugin.getConfig();
        config.set("spawn.world", loc.getWorld().getName());
        config.set("spawn.x", loc.getX());
        config.set("spawn.y", loc.getY());
        config.set("spawn.z", loc.getZ());
        config.set("spawn.yaw", loc.getYaw());
        config.set("spawn.pitch", loc.getPitch());
        plugin.saveConfig();
        player.sendMessage("§aSpawn definido na sua posição atual!");
    }

    @Subcommand("reload")
    @CommandPermission("formularacing.admin")
    @Description("Recarrega o spawn do config.yml.")
    public void onReload(Player player) {
        plugin.reloadConfig();
        Location spawn = getSpawnLocation();
        if (spawn == null) {
            player.sendMessage("§cSpawn ainda não configurado no config.yml.");
        } else {
            player.sendMessage("§aSpawn recarregado: " + format(spawn));
        }
    }

    /**
     * Limpa todo o estado de corrida do jogador ao ir para o spawn.
     * Espelha o {@code SpawnBoatCleanupListener} para que tanto o comando
     * interno quanto um /spawn externo deixem o jogador no mesmo estado.
     */
    private void cleanupPlayerState(Player player) {
        java.util.UUID uuid = player.getUniqueId();
        try {
            if (plugin.getTimerUtils() != null) {
                plugin.getTimerUtils().stopTimer(player);
                plugin.getTimerUtils().cleanupPlayer(uuid);
            }
        } catch (Exception ignored) {
        }
        try {
            if (plugin.getTimeTrialController() != null) {
                plugin.getTimeTrialController().endSession(player);
            }
        } catch (Exception ignored) {
        }
        try {
            if (plugin.getWolfTimingService() != null) {
                plugin.getWolfTimingService().abort(uuid, true);
            }
        } catch (Exception ignored) {
        }
        try {
            if (plugin.getScoreboardTimeTrialUtils() != null) {
                plugin.getScoreboardTimeTrialUtils().clearPlayerTrack(player);
            }
        } catch (Exception ignored) {
        }
        try {
            if (plugin.getRaceActionBarManager() != null) {
                plugin.getRaceActionBarManager().removePlayer(player);
            }
        } catch (Exception ignored) {
        }
        try {
            if (plugin.getGhostManager() != null) {
                plugin.getGhostManager().cleanupPlayer(player);
            }
        } catch (Exception ignored) {
        }
        try {
            if (plugin.getScoreboardOwnershipCoordinator() != null) {
                plugin.getScoreboardOwnershipCoordinator().clear(uuid);
            }
        } catch (Exception ignored) {
        }
        try {
            plugin.clearLastTimeTrialTrack(uuid);
            plugin.clearLastDuelTrack(uuid);
            plugin.clearLastDuelLonelyStatus(uuid);
        } catch (Exception ignored) {
        }
        try {
            plugin.resetTrackGameTime(player);
        } catch (Exception ignored) {
        }
    }

    private Location getSpawnLocation() {
        FileConfiguration config = plugin.getConfig();
        if (!config.isSet("spawn") || !config.isSet("spawn.world")) {
            return null;
        }
        String worldName = config.getString("spawn.world");
        if (worldName == null || worldName.isEmpty()) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        double x = config.getDouble("spawn.x", 0.5);
        double y = config.getDouble("spawn.y", 64.0);
        double z = config.getDouble("spawn.z", 0.5);
        float yaw = (float) config.getDouble("spawn.yaw", 0.0);
        float pitch = (float) config.getDouble("spawn.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    private String format(Location loc) {
        return loc.getWorld().getName() + " " + loc.getX() + ", " + loc.getY() + ", " + loc.getZ();
    }
}
