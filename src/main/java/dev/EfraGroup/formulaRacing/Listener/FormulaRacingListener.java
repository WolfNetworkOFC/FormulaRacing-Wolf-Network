//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.APIFormulaRacing;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Duels.TimeTrialDuels;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class FormulaRacingListener implements Listener {
    private final APIFormulaRacing api;
    private final TimerUtils timerUtils;
    private final FormulaRacing plugin;
    private final DatabaseManager db;
    private final PacketSender packetSender;
    private final Map<UUID, Long> dismountMessageCooldown = new ConcurrentHashMap();
    private static final long MESSAGE_COOLDOWN_MS = 3000L;

    public FormulaRacingListener(FormulaRacing plugin, TimerUtils timerUtils, APIFormulaRacing api, DatabaseManager db, PacketSender packetSender) {
        this.plugin = plugin;
        this.timerUtils = timerUtils;
        this.api = api;
        this.db = db;
        this.packetSender = packetSender;
        this.startBoatCleaner();
    }

    private void startBoatCleaner() {
        (new BukkitRunnable() {
            public void run() {
                int removedCount = 0;

                for(World world : Bukkit.getWorlds()) {
                    for(Boat boat : world.getEntitiesByClass(Boat.class)) {
                        if (boat.getPassengers().isEmpty()) {
                            FormulaRacingListener.this.api.deleteBoat(boat);
                            ++removedCount;
                        }
                    }
                }

                if (removedCount > 0) {
                    FormulaRacingListener.this.plugin.getDebugManager().logRaceSystem("[FormulaRacing] Limpeza: " + removedCount + " barcos abandonados foram removidos.");
                }

            }
        }).runTaskTimer(this.plugin, 1200L, 6000L);
    }

    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void onPlayerSneakBoost(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (event.isSneaking() && player.isInsideVehicle()) {
            if (this.plugin.getRaceEventManager() != null) {
                for(Events eventObj : this.plugin.getRaceEventManager().getAllEvents()) {
                    for(Rounds round : eventObj.getEventSchedule().getRounds().values()) {
                        for(Heats heat : round.getHeats().values()) {
                            Driver d = heat.getDriver(player.getUniqueId());
                            if (d != null && heat.getHeatState() == HeatState.RACING && heat.isPushtopass()) {
                                event.setCancelled(true);
                                if (this.plugin.getPTP() != null) {
                                    this.plugin.getPTP().togglePTP(player, d, heat);
                                }

                                return;
                            }
                        }
                    }
                }
            }

            if (this.plugin.getQuickRaceManager().isPlayerActivelyRacing(player.getUniqueId())) {
            }

        }
    }

    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onBoatExit(VehicleExitEvent event) {
        Vehicle var3 = event.getVehicle();
        if (var3 instanceof Boat boat) {
            LivingEntity var4 = event.getExited();
            if (var4 instanceof Player player) {
                if (!player.hasMetadata("fr_resetting")) {
                    this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] onBoatExit disparado para " + player.getName());
                    boolean isDriver = !boat.getPassengers().isEmpty() && ((Entity)boat.getPassengers().get(0)).equals(player);
                    if (!isDriver) {
                        this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] " + player.getName() + " não é o motorista, ignorando");
                    } else {
                        this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] " + player.getName() + " é o motorista, verificando contexto");
                        if (this.plugin.getTimeTrialDuels() != null && this.plugin.getTimeTrialDuels().isPlayerActivelyInDuel(player.getUniqueId())) {
                            this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] " + player.getName() + " está em duelo, verificando lap reset");
                            if (TimeTrialDuels.isPlayerBeingLapReset(player.getUniqueId())) {
                                this.plugin.getDebugManager().logDuelSystem("[LISTENER] Permitindo ejeção de " + player.getName() + " (lap reset)");
                            } else {
                                this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] Cancelando ejeção de " + player.getName() + " (em duelo)");
                                event.setCancelled(true);
                                this.sendDismountMessage(player);
                            }
                        } else if (this.plugin.getQuickRaceManager().isPlayerInActiveRace(player.getUniqueId())) {
                            this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] " + player.getName() + " está em Quick Race");
                            if (this.plugin.getQuickRaceManager().isPlayerActivelyRacing(player.getUniqueId())) {
                                this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] Cancelando ejeção de " + player.getName() + " (correndo em Quick Race)");
                                event.setCancelled(true);
                                this.sendDismountMessage(player, "race");
                            } else {
                                this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] " + player.getName() + " terminou Quick Race, deletando barco");
                                this.api.deleteBoat(boat);
                            }

                        } else {
                            if (this.plugin.getRaceEventManager() != null) {
                                this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] Verificando se " + player.getName() + " está em algum heat");
                                boolean isInAnyHeat = false;
                                boolean isRacingInAnyHeat = false;

                                for(Events eventObj : this.plugin.getRaceEventManager().getAllEvents()) {
                                    for(Rounds round : eventObj.getEventSchedule().getRounds().values()) {
                                        for(Heats heat : round.getHeats().values()) {
                                            Driver d = heat.getDriver(player.getUniqueId());
                                            if (d != null) {
                                                isInAnyHeat = true;
                                                boolean isSessionActive = heat.getHeatState() == HeatState.PRACTICE || heat.getHeatState() == HeatState.QUALIFYING || heat.getHeatState() == HeatState.LOADED || heat.getHeatState() == HeatState.STARTING || heat.getHeatState() == HeatState.RACING;
                                                boolean isReadyCheckActive = this.plugin.getReadyCheckManager().isReadyCheckActive(heat.getId());
                                                if (isSessionActive || isReadyCheckActive) {
                                                    isRacingInAnyHeat = true;
                                                    String reason = isReadyCheckActive ? "Ready Check ativo" : "sessão ativa";
                                                    DebugManager var10000 = this.plugin.getDebugManager();
                                                    String var10001 = player.getName();
                                                    var10000.logTimeTrialSystem("[LISTENER] Cancelando ejeção de " + var10001 + " (" + reason + " no heat " + heat.getId() + " | Estado: " + heat.getHeatState() + ")");
                                                    event.setCancelled(true);
                                                    if (!isReadyCheckActive) {
                                                        this.sendDismountMessage(player, "heat");
                                                    }

                                                    return;
                                                }
                                            }
                                        }
                                    }
                                }

                                if (isInAnyHeat && !isRacingInAnyHeat) {
                                    this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] " + player.getName() + " está em heats mas não está correndo, FAZENDO CLEANUP DE TT");
                                    this.timerUtils.stopTimer(player);
                                    if (this.plugin.getTimeTrialController() != null) {
                                        this.plugin.getTimeTrialController().endSession(player);
                                    }

                                    if (this.plugin.getTimeTrialDuelsAction() != null) {
                                        this.plugin.getTimeTrialDuelsAction().stopAll(player);
                                    }

                                    this.api.deleteBoat(boat);
                                    return;
                                }

                                if (isInAnyHeat) {
                                    this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] " + player.getName() + " está em heat ativo (proteção fallback)");
                                    event.setCancelled(true);
                                    return;
                                }

                                this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] " + player.getName() + " NÃO está em nenhum heat, continuando...");
                                this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] " + player.getName() + " NÃO está em nenhum heat, continuando...");
                            }

                            this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] Boat exit para " + player.getName() + " - iniciando cleanup de Time Trial");
                            this.api.deleteBoat(boat);
                            this.timerUtils.stopTimer(player);
                            if (this.plugin.getTimeTrialController() != null) {
                                this.plugin.getTimeTrialController().endSession(player);
                            }

                            if (this.plugin.getScoreboardTimeTrialUtils() != null) {
                                this.plugin.getScoreboardTimeTrialUtils().clearPlayerTrack(player);
                            }

                            if (this.plugin.getTimeTrialDuelsAction() != null) {
                                this.plugin.getTimeTrialDuelsAction().stopAll(player);
                            }

                            this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] Cleanup de Time Trial concluído para " + player.getName());
                        }
                    }
                }
            }
        }
    }

    private void sendDismountMessage(Player player) {
        this.sendDismountMessage(player, "duel");
    }

    private void sendDismountMessage(Player player, String type) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastMessage = (Long)this.dismountMessageCooldown.get(uuid);
        if (lastMessage == null || now - lastMessage >= 3000L) {
            String langCode = this.db.getPlayerLanguage(uuid);
            player.sendMessage(this.plugin.getDirectTranslation(type + "_cannot_dismount", langCode));
            player.sendMessage(this.plugin.getDirectTranslation(type + "_use_quit", langCode));
            this.dismountMessageCooldown.put(uuid, now);
        }

    }

    @EventHandler
    public void onVehicleDestroy(VehicleDestroyEvent event) {
        Vehicle var3 = event.getVehicle();
        if (var3 instanceof Boat boat) {
            this.api.deleteBoat(boat);
        }

    }
}
