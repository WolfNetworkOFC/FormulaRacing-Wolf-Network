package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Controllers.RaceEventManager;
import dev.EfraGroup.formulaRacing.Database.Track;
import dev.EfraGroup.formulaRacing.Event.EventSchedule;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.PitStopManager;
import dev.EfraGroup.formulaRacing.Heat.PitStopMinigame;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.vehicle.VehicleMoveEvent;

public class PitStopListener implements Listener {
    private final FormulaRacing plugin;
    private final RaceEventManager raceEventManager;
    private final PitStopManager pitStopManager;
    private final Map<UUID, Boolean> wasInPitStopStart;
    private final Map<UUID, Boolean> wasInPitStopEntry;
    private final Map<UUID, Boolean> wasInPitStopExit;
    private final Map<UUID, Boolean> wasInMinigameArea;
    private final Map<UUID, Long> pitAreaExitTime;
    private static final long REGION_EXIT_GRACE_MS = 400L;

    public PitStopListener(FormulaRacing plugin, RaceEventManager raceEventManager, PitStopManager pitStopManager) {
        this.plugin = plugin;
        this.raceEventManager = raceEventManager;
        this.pitStopManager = pitStopManager;
        this.wasInPitStopEntry = new HashMap();
        this.wasInPitStopExit = new HashMap();
        this.wasInMinigameArea = new HashMap();
        this.pitAreaExitTime = new HashMap();
        this.wasInPitStopStart = new HashMap();
    }

    private Heats findActiveHeat(UUID playerId) {
        for(Events eve : this.raceEventManager.getAllEvents()) {
            EventSchedule schedule = eve.getEventSchedule();

            for(Rounds round : schedule.getRounds().values()) {
                for(Heats h : round.getHeats().values()) {
                    if (h.getHeatState() == HeatState.RACING && h.getDriver(playerId) != null) {
                        return h;
                    }
                }
            }
        }

        return null;
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onVehicleMove(VehicleMoveEvent event) {
        Vehicle vehicle = event.getVehicle();
        if (vehicle instanceof Boat) {
            if (!vehicle.getPassengers().isEmpty()) {
                Object var4 = vehicle.getPassengers().get(0);
                if (var4 instanceof Player) {
                    Player player = (Player)var4;
                    UUID var12 = player.getUniqueId();
                    Heats heat = this.findActiveHeat(var12);
                    if (heat == null) {
                        return;
                    }

                    Location location = vehicle.getLocation();
                    String trackNameWS = heat.getTrackNameWS();
                    boolean isInEntry = this.pitStopManager.getPitStopEntryAtLocation(location) != null;
                    boolean isInStart = this.pitStopManager.getPitStopStartAtLocation(location) != null;
                    boolean isInExit = this.pitStopManager.getPitStopExitAtLocation(location) != null;
                    boolean isInMinigameArea = this.pitStopManager.isValidPitStopLocation(location, trackNameWS);
                    if (isInEntry && !(Boolean)this.wasInPitStopEntry.getOrDefault(var12, false)) {
                        this.onEnterPitStopEntry(player, heat);
                        this.wasInPitStopEntry.put(var12, true);
                    } else if (!isInEntry) {
                        this.wasInPitStopEntry.put(var12, false);
                    }

                    this.processMinigameLogic(player, heat, isInMinigameArea, trackNameWS);
                    if (isInStart && !(Boolean)this.wasInPitStopStart.getOrDefault(var12, false)) {
                        this.onPitLapTrigger(player, heat);
                        this.wasInPitStopStart.put(var12, true);
                    } else if (!isInStart) {
                        this.wasInPitStopStart.put(var12, false);
                    }

                    if (isInExit && !(Boolean)this.wasInPitStopExit.getOrDefault(var12, false)) {
                        this.onEnterPitStopExit(player, heat);
                        this.wasInPitStopExit.put(var12, true);
                    } else if (!isInExit) {
                        this.wasInPitStopExit.put(var12, false);
                    }

                    return;
                }
            }

        }
    }

    private void onPitLapTrigger(Player player, Heats heat) {
        this.plugin.getDebugManager().logPitStopSystem("[PIT] " + player.getName() + " passou pela linha de START do pit.");
        Driver driver = heat.getDriver(player.getUniqueId());
        if (driver != null) {
        }

    }

    private void processMinigameLogic(Player player, Heats heat, boolean isInArea, String track) {
        UUID pid = player.getUniqueId();
        if (isInArea) {
            this.pitStopManager.onPlayerEnterPit(player, track, heat);
            this.wasInMinigameArea.put(pid, true);
            this.pitAreaExitTime.remove(pid);
        } else if ((Boolean)this.wasInMinigameArea.getOrDefault(pid, false)) {
            long now = System.currentTimeMillis();
            this.pitAreaExitTime.putIfAbsent(pid, now);
            if (now - (Long)this.pitAreaExitTime.get(pid) >= 400L) {
                this.onExitMinigameArea(player);
                this.wasInMinigameArea.put(pid, false);
            }
        }

    }

    private void onEnterPitStopEntry(Player player, Heats heat) {
        this.plugin.getDebugManager().logPitStopSystem("[PIT] " + player.getName() + " entrou na ENTRY");
        UUID playerId = player.getUniqueId();
        this.pitStopManager.markPlayerInPitStop(playerId);
        this.plugin.sendMessage(player, "pit_entering", new String[0]);
        Driver driver = heat.getDriver(playerId);
        if (driver != null) {
            int totalCheckpoints = 0;
            Track track = this.plugin.getTrackIntegrationManager().getTrack(heat.getTrackNameWS());
            if (track != null) {
                totalCheckpoints = track.getTotalCheckpoints();
            }

            if (totalCheckpoints > 0) {
                double threshold = (double)totalCheckpoints * (double)0.5F;
                if ((double)driver.getCheckpointsReached() >= threshold) {
                    DebugManager var10000 = this.plugin.getDebugManager();
                    String var10001 = player.getName();
                    var10000.logPitStopSystem("[PIT] Forçando checkpoints na entrada para " + var10001 + " (" + driver.getCheckpointsReached() + "/" + totalCheckpoints + ") - Threshold (50%) atingido.");
                    driver.forceCompleteCheckpoints(totalCheckpoints);
                } else {
                    DebugManager var9 = this.plugin.getDebugManager();
                    String var10 = player.getName();
                    var9.logPitStopSystem("[PIT] " + var10 + " entrou no pit mas não atingiu o threshold de checkpoints (" + driver.getCheckpointsReached() + "/" + totalCheckpoints + " < 50%). Não forçando conclusão.");
                }
            }
        }

    }

    private void onEnterPitStopExit(Player player, Heats heat) {
        this.plugin.getDebugManager().logPitStopSystem("[PIT] " + player.getName() + " entrou na EXIT");
        UUID playerId = player.getUniqueId();
        boolean hasPitLane = this.pitStopManager.hasPitLane(heat.getTrackNameWS());
        boolean passedEntry = this.pitStopManager.hasPassedPitStop(playerId);
        boolean completedMinigame = !hasPitLane || this.pitStopManager.hasCompletedMinigame(playerId);
        if (passedEntry && completedMinigame) {
            this.pitStopManager.handlePitExit(player, heat);
            this.plugin.sendMessage(player, "pit_completed", new String[0]);
        } else if (!passedEntry) {
            this.plugin.sendMessage(player, "pit_invalid_entry", new String[0]);
        } else if (hasPitLane && !completedMinigame) {
            this.plugin.sendMessage(player, "pit_invalid_game", new String[0]);
        }

        this.pitStopManager.clearPitStopState(playerId);
    }

    private void onExitMinigameArea(Player player) {
        UUID playerId = player.getUniqueId();
        if (!this.pitStopManager.hasCompletedMinigame(playerId)) {
            this.pitStopManager.onPlayerExitPit(player);
        }

    }

    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onInventoryClick(InventoryClickEvent event) {
        HumanEntity var3 = event.getWhoClicked();
        if (var3 instanceof Player player) {
            if (this.pitStopManager.hasActiveMinigame(player.getUniqueId())) {
                event.setCancelled(true);
                PitStopMinigame minigame = this.pitStopManager.getMinigame(player.getUniqueId());
                if (minigame != null) {
                    if (event.getClickedInventory() != null) {
                        if (event.getClickedInventory().equals(minigame.getInventory())) {
                            switch (event.getAction()) {
                                case CLONE_STACK:
                                case MOVE_TO_OTHER_INVENTORY:
                                case HOTBAR_SWAP:
                                    this.plugin.sendMessage(player, "pit_action_blocked", new String[0]);
                                    return;
                                default:
                                    if (event.getClick().isShiftClick()) {
                                        this.plugin.sendMessage(player, "pit_shift_blocked", new String[0]);
                                    } else {
                                        int slot = event.getRawSlot();
                                        this.pitStopManager.handleInventoryClick(player, slot);
                                    }
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onInventoryClose(InventoryCloseEvent event) {
        HumanEntity var3 = event.getPlayer();
        if (var3 instanceof Player player) {
            UUID var5 = player.getUniqueId();
            PitStopMinigame minigame = this.pitStopManager.getMinigame(var5);
            if (minigame != null) {
                if (!minigame.isCompleted()) {
                    minigame.cancel();
                    this.pitStopManager.onPlayerExitPit(player);
                }

            }
        }
    }

    public void clearPlayer(UUID playerId) {
        this.wasInPitStopEntry.remove(playerId);
        this.wasInPitStopExit.remove(playerId);
        this.wasInMinigameArea.remove(playerId);
        this.wasInPitStopStart.remove(playerId);
    }

    public void clearAll() {
        this.wasInPitStopEntry.clear();
        this.wasInPitStopExit.clear();
        this.wasInMinigameArea.clear();
        this.wasInPitStopStart.clear();
    }
}
