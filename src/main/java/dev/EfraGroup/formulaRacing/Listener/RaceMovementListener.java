package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Controllers.RaceEventManager;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Round.RoundType;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import java.util.Optional;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;

public class RaceMovementListener implements Listener {
    private final FormulaRacing plugin;
    private final RaceEventManager eventManager;

    public RaceMovementListener(FormulaRacing plugin, RaceEventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
    }

    @EventHandler
    public void onVehicleMove(VehicleMoveEvent event) {
        if (event.getVehicle().getPassengers().size() > 0 && event.getVehicle().getPassengers().get(0) instanceof Player) {
            Player player = (Player)event.getVehicle().getPassengers().get(0);
            Optional<Events> eventOpt = this.eventManager.getPlayerEvent(player.getUniqueId());
            if (!eventOpt.isEmpty()) {
                Events raceEvent = (Events)eventOpt.get();
                Optional<Rounds> roundOpt = raceEvent.getEventSchedule().getCurrentRound();
                if (!roundOpt.isEmpty()) {
                    Rounds round = (Rounds)roundOpt.get();
                    if (round.getRoundType() == RoundType.FINAL || round.getRoundType() == RoundType.SPRINT_RACE) {
                        Heats activeHeat = this.findActiveHeat(round);
                        if (activeHeat != null && activeHeat.getHeatState() == HeatState.RACING) {
                            Driver driver = activeHeat.getDriver(player.getUniqueId());
                            if (driver != null) {
                                this.handlePitStops(player, driver, activeHeat);
                            }
                        }
                    }
                }
            }
        }
    }

    private void handlePitStops(Player player, Driver driver, Heats heat) {
        String trackNameWS = heat.getTrackNameWS();
        if (trackNameWS != null && !trackNameWS.isEmpty()) {
            boolean isValidPit = this.plugin.getPitStopManager().isValidPitStopLocation(player.getLocation(), trackNameWS);
            if (isValidPit) {
                this.plugin.getPitStopManager().onPlayerEnterPit(player, trackNameWS, heat);
            }

        }
    }

    private Heats findActiveHeat(Rounds round) {
        return round.getHeats().values().stream().filter((heat) -> heat.getHeatState() == HeatState.RACING).findFirst().orElse(null);
    }
}
