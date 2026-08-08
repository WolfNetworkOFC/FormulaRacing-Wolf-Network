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
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;


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
        SchedulerHelper.runTaskTimer(this.plugin, () -> {
            for (Boat boat : this.api.getTrackedBoats()) {
                final Boat captured = boat;
                SchedulerHelper.runTaskFor(this.plugin, captured, () -> {
                    if (captured.isValid() && captured.getPassengers().isEmpty()) {
                        this.api.queueDeleteBoat(captured);
                    }
                });
            }
        }, 1200L, 6000L);
    }

    @EventHandler(
            priority = EventPriority.LOWEST
    )
    public void onPlayerSneakBoost(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();

        if (event.isSneaking() && player.isInsideVehicle()) {
            if (this.plugin.getRaceEventManager() != null) {
                for (Events eventObj : this.plugin.getRaceEventManager().getAllEvents()) {
                    for (Rounds round : eventObj.getEventSchedule().getRounds().values()) {
                        for (Heats heat : round.getHeats().values()) {
                            Driver d = heat.getDriver(player.getUniqueId());

                            if (d != null && heat.getHeatState() == HeatState.RACING) {
                                if (heat.isPushtopass()) {
                                    event.setCancelled(true);
                                    if (this.plugin.getPTP() != null) {
                                        this.plugin.getPTP().togglePTP(player, d, heat);
                                    }
                                    return;
                                }
                                if (this.plugin.getERS() != null) {
                                    this.plugin.getERS().cycleERSMode(player, d, heat);
                                }
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks if the given player is the driver (first passenger) of the boat.
     * If the player is NOT the driver (e.g., a passenger in a multiplayer boat),
     * we should NOT delete the boat or run cleanup — just let them exit.
     */
    private boolean isBoatDriver(Player player, Boat boat) {
        if (boat == null || boat.isEmpty()) return true; // no other passengers, player IS the driver
        return boat.getPassengers().get(0) instanceof Player first
            && first.getUniqueId().equals(player.getUniqueId());
    }

    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onBoatExit(VehicleExitEvent event) {
        Vehicle var3 = event.getVehicle();
        if (var3 instanceof Boat boat) {
            LivingEntity var4 = event.getExited();
            if (var4 instanceof Player player) {
                if (player.hasMetadata("fr_resetting")) {
                    // fr_resetting significa que recoverPlayerBoatState já tratou do cleanup,
                    // mas o barco pode não ter sido removido se a task agendada falhou.
                    // Garantimos a remoção aqui também para evitar barcos fantasmas.
                    this.api.deleteBoat(boat);
                    // Auto-cura: limpa o flag após o uso. Se ele ficar preso (ex.: task
                    // do barco descartada em chunk descarregado no Folia), o próximo
                    // shift-exit durante um TT cairia aqui e o timer nunca pararia.
                    player.removeMetadata("fr_resetting", this.plugin);
                    return;
                }

                // If this player is NOT the boat driver, just let them exit without cleanup
                if (!isBoatDriver(player, boat)) {
                    this.plugin.getDebugManager().logTimeTrialSystem(
                        "[LISTENER] " + player.getName() + " saiu como pendura - barco preservado"
                    );
                    return;
                }

                this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] onBoatExit disparado para " + player.getName());
                if (this.plugin.getTimeTrialDuels() != null && this.plugin.getTimeTrialDuels().isPlayerActivelyInDuel(player.getUniqueId())) {
                    this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] " + player.getName() + " está em duelo, verificando lap reset");
                    if (TimeTrialDuels.isPlayerBeingLapReset(player.getUniqueId())) {
                        this.plugin.getDebugManager().logDuelSystem("[LISTENER] Permitindo ejeção de " + player.getName() + " (lap reset)");
                    } else {
                        this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] Cancelando ejeção de " + player.getName() + " (em duelo)");
                        event.setCancelled(true);
                        this.sendDismountMessage(player);
                    }
                } else if (this.plugin.getQuickRaceManager() != null && this.plugin.getQuickRaceManager().isPlayerInActiveRace(player.getUniqueId())) {
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
                                        boolean isDriverStillRunning = !d.isFinished() && !d.isDnf();
                                        if (isDriverStillRunning && (isSessionActive || isReadyCheckActive)) {
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
                    }

                    this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] Boat exit para " + player.getName() + " - iniciando cleanup de Time Trial");
                    this.api.deleteBoat(boat);
                    this.timerUtils.stopTimer(player);
                    if (this.plugin.getTimeTrialController() != null) {
                        this.plugin.getTimeTrialController().endSession(player);
                    }

                    // O scoreboard de Time Trial NÃO é limpo ao sair do barco: ele
                    // persiste até o jogador usar /spawn, sair do jogo, voltar ao
                    // spawn ou entrar numa corrida.

                    if (this.plugin.getTimeTrialDuelsAction() != null) {
                        this.plugin.getTimeTrialDuelsAction().stopAll(player);
                    }

                    this.plugin.getDebugManager().logTimeTrialSystem("[LISTENER] Cleanup de Time Trial concluído para " + player.getName());
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
