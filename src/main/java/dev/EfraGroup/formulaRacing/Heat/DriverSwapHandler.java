package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.*;

public class DriverSwapHandler {

    private static final double RESERVE_DISTANCE = 10.0;

    public static boolean handleSwap(Player newDriver, Player currentDriver) {
        if (newDriver == null || currentDriver == null) return false;
        if (newDriver.equals(currentDriver)) return false;

        FormulaRacing plugin = FormulaRacing.getInstance();
        Heats heat = plugin.getDriverLookup().getHeat(currentDriver.getUniqueId());
        if (heat == null || !heat.getDriverSwap()) return false;
        if (heat.getHeatState() != HeatState.RACING) return false;

        Entity vehicle = currentDriver.getVehicle();
        if (vehicle == null || !(vehicle instanceof Boat)) return false;

        PitBoxRegion pitBox = plugin.getPitStopManager().getPitBoxAt(vehicle.getLocation());
        if (pitBox == null) {
            plugin.sendMessage(currentDriver, "driver_swap_not_in_pit");
            return false;
        }

        if (!isSameTeam(newDriver, currentDriver, heat)) {
            plugin.sendMessage(newDriver, "driver_swap_no_team");
            return false;
        }

        Driver driver = heat.getDriver(currentDriver.getUniqueId());
        if (driver == null) return false;
        int currentLap = driver.getLapCount();

        if (!plugin.getPitStopManager().isDriverEligibleForSwap(currentDriver.getUniqueId(), currentLap)) {
            plugin.sendMessage(currentDriver, "driver_swap_already_swapped");
            return false;
        }

        if (!plugin.getPitStopManager().isPitStopCompleted(currentDriver.getUniqueId())) {
            plugin.sendMessage(currentDriver, "driver_swap_not_completed");
            return false;
        }

        performSwap(newDriver, currentDriver, heat, vehicle, currentLap);
        return true;
    }

    private static void performSwap(Player newDriver, Player currentDriver, Heats heat, Entity vehicle, int currentLap) {
        FormulaRacing plugin = FormulaRacing.getInstance();
        Location boatLocation = vehicle.getLocation().clone();

        Driver oldDriverObj = heat.getDriver(currentDriver.getUniqueId());
        int oldPosition = oldDriverObj != null ? oldDriverObj.getPosition() : 0;
        int oldStartPosition = oldDriverObj != null ? oldDriverObj.getStartPosition() : 0;
        int oldPits = oldDriverObj != null ? oldDriverObj.getPitstops() : 0;

        SchedulerHelper.runTaskFor(plugin, vehicle, () -> {
            vehicle.removePassenger(currentDriver);
        });

        SchedulerHelper.teleport(newDriver, boatLocation);

        SchedulerHelper.runTaskLater(plugin, () -> {
            SchedulerHelper.runTaskFor(plugin, vehicle, () -> {
                vehicle.addPassenger(newDriver);
            });
        }, 2L);

        heat.getDrivers().remove(currentDriver.getUniqueId());

        Driver newDriverObj = new Driver(newDriver.getUniqueId(), heat.getId(), oldStartPosition);
        newDriverObj.setPosition(oldPosition);
        newDriverObj.setPitstops(oldPits + 1);
        heat.getDrivers().put(newDriver.getUniqueId(), newDriverObj);

        plugin.getPitStopManager().markDriverSwapped(currentDriver.getUniqueId(), currentLap);
        plugin.getPitStopManager().markDriverSwapped(newDriver.getUniqueId(), currentLap);

        plugin.sendMessage(currentDriver, "driver_swap_success", "{old}", currentDriver.getName(), "{new}", newDriver.getName());
        plugin.sendMessage(newDriver, "driver_swap_success", "{old}", currentDriver.getName(), "{new}", newDriver.getName());

        plugin.getDebugManager().logRaceSystem("[DriverSwap] " + currentDriver.getName() + " -> " + newDriver.getName() + " no heat " + heat.getId());
    }

    private static boolean isSameTeam(Player player1, Player player2, Heats heat) {
        return player1.getScoreboard().getEntryTeam(player2.getName()) != null
            && player1.getScoreboard().getEntryTeam(player1.getName()) != null
            && player1.getScoreboard().getEntryTeam(player1.getName()).equals(
               player1.getScoreboard().getEntryTeam(player2.getName()));
    }

    public static Optional<Player> findReserveDriver(Player currentDriver, PitBoxRegion pitBox) {
        if (pitBox == null || currentDriver == null) return Optional.empty();
        Location center = pitBox.getCenter();
        if (center == null) return Optional.empty();

        Player closest = null;
        double closestDist = RESERVE_DISTANCE;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.equals(currentDriver)) continue;
            if (!online.getWorld().equals(center.getWorld())) continue;
            double dist = online.getLocation().distance(center);
            if (dist < closestDist) {
                closest = online;
                closestDist = dist;
            }
        }
        return Optional.ofNullable(closest);
    }
}
