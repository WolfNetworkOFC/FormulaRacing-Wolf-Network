//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.BoatUtils.NocolManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Duels.TimeTrialDuels;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

public class LonelyController implements Listener {
    private final DatabaseManager databaseManager;
    private final FormulaRacing plugin;
    private final Map<UUID, Boolean> lonelyCache = new ConcurrentHashMap();
    private static final long VISIBILITY_UPDATE_DELAY = 2L;
    private final Set<UUID> obuInfoShown = ConcurrentHashMap.newKeySet();

    public LonelyController(DatabaseManager databaseManager, FormulaRacing plugin) {
        this.databaseManager = databaseManager;
        this.plugin = plugin;
    }

    public void setLonelyMode(Player player, boolean enabled) {
        this.databaseManager.setLonelyModePlayer(player.getUniqueId(), enabled);
        this.lonelyCache.put(player.getUniqueId(), enabled);
        this.updatePlayerVisibility(player);
        this.updatePlayersVisibility(player);
    }

    public boolean isLonely(UUID uuid) {
        return (Boolean)this.lonelyCache.computeIfAbsent(uuid, (k) -> this.databaseManager.getLonelyModePlayer(k));
    }

    public void updatePlayersVisibility(Player player) {
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (player.isOnline()) {
                this.updatePlayerCollisionState(player);

                for(Player target : Bukkit.getOnlinePlayers()) {
                    if (!target.equals(player)) {
                        this.processVisibility(player, target);
                    }
                }

            }
        }, 2L);
    }

    private void updatePlayerCollisionState(Player player) {
        if (!this.isPlayerInBoat(player)) {
            this.setVanillaCollision(player, false);
        } else {
            boolean isLonely = this.isLonely(player.getUniqueId());
            boolean hasMod = FormulaRacing.hasOpenBoatUtilsMod(player);
            Optional<Heats> activeHeat = this.plugin.getRaceEventManager().getPlayerActiveHeat(player.getUniqueId());
            if (activeHeat.isPresent()) {
                Heats heat = (Heats)activeHeat.get();
                if (heat.isPlayerActivelyRacing(player.getUniqueId())) {
                    boolean heatLonely = heat.isLonely();
                    boolean isPracticeOrQualy = heat.getHeatState() == HeatState.PRACTICE || heat.getHeatState() == HeatState.QUALIFYING;
                    if (heatLonely) {
                        NocolManager.setCollisionMode(player, false);
                        this.setVanillaCollision(player, true);
                    } else if (isPracticeOrQualy && hasMod) {
                        NocolManager.setCollisionMode(player, false);
                        this.setVanillaCollision(player, true);
                    } else {
                        NocolManager.setCollisionMode(player, true);
                        this.setVanillaCollision(player, false);
                    }

                    return;
                }
            }

            int duelIdCached = this.plugin.getTimeTrialDuels() != null ? this.plugin.getTimeTrialDuels().getActiveDuelIdCached(player.getUniqueId()) : -1;
            if (duelIdCached != -1) {
                NocolManager.setCollisionMode(player, false);
                this.setVanillaCollision(player, true);
            } else {
                String activeTrack = this.plugin.getTimerUtils().getActiveTrack(player);
                if (activeTrack != null) {
                    NocolManager.setCollisionMode(player, false);
                    this.setVanillaCollision(player, true);
                } else {
                    if (isLonely) {
                        this.setVanillaCollision(player, true);
                        NocolManager.setCollisionMode(player, false);
                    } else {
                        this.setVanillaCollision(player, false);
                        NocolManager.setCollisionMode(player, true);
                    }

                }
            }
        }
    }

    public void updatePlayerVisibility(Player target) {
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            if (target.isOnline()) {
                for(Player viewer : Bukkit.getOnlinePlayers()) {
                    if (!viewer.equals(target)) {
                        if (!viewer.getWorld().equals(target.getWorld())) {
                            this.hidePlayer(viewer, target);
                        } else {
                            this.processVisibility(viewer, target);
                        }
                    }
                }

            }
        }, 2L);
    }

    private void processVisibility(Player viewer, Player target) {
        if (!viewer.getWorld().equals(target.getWorld())) {
            this.hidePlayer(viewer, target);
        } else {
            Optional<Heats> viewerHeatOpt = this.plugin.getRaceEventManager().getPlayerActiveHeat(viewer.getUniqueId());
            if (viewerHeatOpt.isPresent()) {
                Heats viewerHeat = viewerHeatOpt.get();
                if (viewerHeat.isPlayerActivelyRacing(viewer.getUniqueId())) {
                    if (viewerHeat.isDriver(target.getUniqueId())) {
                        if (viewerHeat.isLonely()) {
                            this.hidePlayer(viewer, target);
                            return;
                        }

                        this.showPlayer(viewer, target);
                        return;
                    }

                    this.hidePlayer(viewer, target);
                    return;
                }
            }

            int viewerDuelId = this.plugin.getTimeTrialDuels() != null ? this.plugin.getTimeTrialDuels().getActiveDuelIdCached(viewer.getUniqueId()) : -1;
            if (viewerDuelId != -1) {
                int targetDuelId = this.plugin.getTimeTrialDuels().getActiveDuelIdCached(target.getUniqueId());
                if (viewerDuelId != targetDuelId) {
                    this.hidePlayer(viewer, target);
                } else {
                    TimeTrialDuels.DuelState duelState = this.plugin.getTimeTrialDuels().getDuelState(viewerDuelId);
                    if (duelState != null && duelState.isLonely()) {
                        this.hidePlayer(viewer, target);
                    } else {
                        this.showPlayer(viewer, target);
                    }

                }
            } else {
                Optional<Heats> targetHeatOpt = this.plugin.getRaceEventManager().getPlayerActiveHeat(target.getUniqueId());
                if (targetHeatOpt.isPresent()) {
                    Heats targetHeat = (Heats)targetHeatOpt.get();
                    if (targetHeat.isPlayerActivelyRacing(target.getUniqueId())) {
                    }
                }

                int targetDuelId = this.plugin.getTimeTrialDuels() != null ? this.plugin.getTimeTrialDuels().getActiveDuelIdCached(target.getUniqueId()) : -1;
                if (targetDuelId != -1) {
                    this.hidePlayer(viewer, target);
                } else {
                    boolean viewerIsLonely = this.isLonely(viewer.getUniqueId());
                    if (viewerIsLonely) {
                        this.hidePlayer(viewer, target);
                    } else {
                        this.showPlayer(viewer, target);
                    }

                }
            }
        }
    }

    private void showPlayer(Player viewer, Player target) {
        viewer.showPlayer(this.plugin, target);
        if (target.getVehicle() != null) {
            viewer.showEntity(this.plugin, target.getVehicle());
            if (this.plugin.getConfig().getBoolean("FrostHexAddOn", false) && !target.getVehicle().getPassengers().isEmpty()) {
                for(Entity e : target.getVehicle().getPassengers()) {
                    viewer.showEntity(this.plugin, e);
                }
            }
        }

    }

    private void hidePlayer(Player viewer, Player target) {
        viewer.hidePlayer(this.plugin, target);
        if (target.getVehicle() != null) {
            viewer.hideEntity(this.plugin, target.getVehicle());
            if (this.plugin.getConfig().getBoolean("FrostHexAddOn", false) && !target.getVehicle().getPassengers().isEmpty()) {
                for(Entity e : target.getVehicle().getPassengers()) {
                    viewer.hideEntity(this.plugin, e);
                }
            }
        }

    }

    private boolean isPlayerInBoat(Player player) {
        Entity v = player.getVehicle();
        return v instanceof Boat || v instanceof ChestBoat;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        this.isLonely(e.getPlayer().getUniqueId());
        this.updatePlayersVisibility(e.getPlayer());
        this.updatePlayerVisibility(e.getPlayer());
    }

    public boolean hasSeenObuInfo(UUID uuid) {
        return this.obuInfoShown.contains(uuid);
    }

    public void markObuInfoShown(UUID uuid) {
        this.obuInfoShown.add(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        this.lonelyCache.remove(e.getPlayer().getUniqueId());
        this.obuInfoShown.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent e) {
        Entity var3 = e.getEntered();
        if (var3 instanceof Player p) {
            this.updatePlayersVisibility(p);
            this.updatePlayerVisibility(p);
        }

    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent e) {
        LivingEntity var3 = e.getExited();
        if (var3 instanceof Player p) {
            this.updatePlayersVisibility(p);
            this.updatePlayerVisibility(p);
        }

    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent e) {
        this.updatePlayersVisibility(e.getPlayer());
        this.updatePlayerVisibility(e.getPlayer());
    }

    private void setVanillaCollision(Player player, boolean preventCollision) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = sb.getTeam("fr_nocol");
        if (team == null) {
            team = sb.registerNewTeam("fr_nocol");
            team.setOption(Option.COLLISION_RULE, OptionStatus.NEVER);
            team.setCanSeeFriendlyInvisibles(false);
            this.plugin.getDebugManager().logPacketHandling("[FormulaRacing] Time 'fr_nocol' criado no MainScoreboard.");
        }

        if (preventCollision) {
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }
        } else if (team.hasEntry(player.getName())) {
            team.removeEntry(player.getName());
        }

    }
}
