package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatConfig;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Checkered Flag Manager
 * Controls the finishing flow when the first place crosses the line
 */
public class CheckeredFlagManager {

    private final FormulaRacing plugin;
    private boolean checkeredFlagShown = false;
    private Driver winner = null;

    public CheckeredFlagManager(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if the checkered flag should be shown
     */
    public boolean shouldShowCheckeredFlag(Heats heat, Driver driver) {
        HeatConfig config = heat.getHeatConfig();

        if (!config.isEnableCheckeredFlagFlow()) {
            return false;
        }

        if (checkeredFlagShown) {
            return false;
        }

        // Check if the driver has completed the race requirements
        if (!hasCompletedRaceRequirements(heat, driver)) {
            return false;
        }

        // Check if they are the first to complete
        Optional<Driver> firstFinisher = getFirstFinisher(heat);

        if (firstFinisher.isPresent() && firstFinisher.get().getUuid().equals(driver.getUuid())) {
            return true;
        }

        return false;
    }

    /**
     * Shows the checkered flag and finishes the race for everyone
     */
    public void showCheckeredFlag(Heats heat, Driver driver) {
        HeatConfig config = heat.getHeatConfig();

        if (checkeredFlagShown) {
            return;
        }

        checkeredFlagShown = true;
        winner = driver;

        // Announce victory
        announceWinner(heat, driver);

        // Mark race as finished for everyone
        config.setRaceFinishedForAll(true);

        plugin.getDebugManager().logRaceSystem(
            "[CHECKERED FLAG] Checkered flag shown for " + driver.getUuid() +
            " - Race finished for everyone"
        );

        // Finalize all drivers who have completed the race
        finalizeAllDrivers(heat);
    }

    /**
     * Checks if a driver should be finalized after the checkered flag
     */
    public boolean shouldFinalizeDriver(Heats heat, Driver driver) {
        HeatConfig config = heat.getHeatConfig();

        if (!config.isRaceFinishedForAll()) {
            return false;
        }

        if (driver.isFinished() || driver.isDnf()) {
            return false;
        }

        // Check if the driver crossed the finish line
        return hasCrossedFinishLine(heat, driver);
    }

    /**
     * Finalizes a driver after the checkered flag
     */
    public void finalizeDriver(Heats heat, Driver driver) {
        if (driver.isFinished() || driver.isDnf()) {
            return;
        }

        // Mark as finished
        driver.setFinished(true);

        // Lock current position
        int currentPosition = driver.getPosition();
        driver.setPosition(currentPosition);

        // Remove from boat
        Player player = Bukkit.getPlayer(driver.getUuid());
        if (player != null && player.isOnline()) {
            if (player.getVehicle() != null) {
                player.getVehicle().remove();
            }

            // Teleport to spawn
            Location spawnLoc = plugin.getDatabaseManager().getTrackSpawn(heat.getTrackNameWS());
            if (spawnLoc != null) {
                SchedulerHelper.teleport(player,spawnLoc);
            }

            plugin.getDebugManager().logRaceSystem(
                "[CHECKERED FLAG] Driver " + player.getName() + " finished at position " + currentPosition
            );
        }
    }

    /**
     * Checks if the driver has completed the race requirements
     */
    private boolean hasCompletedRaceRequirements(Heats heat, Driver driver) {
        // Check if the required lap count has been completed
        if (heat.getTotalLaps() != null && driver.getLapCount() < heat.getTotalLaps()) {
            return false;
        }

        // Check if mandatory pits have been completed
        if (heat.getTotalPits() != null && heat.getTotalPits() > 0) {
            if (!driver.hasCompletedMandatoryPits(heat.getTotalPits())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Gets the first driver to complete the race
     */
    private Optional<Driver> getFirstFinisher(Heats heat) {
        // Optimization: Use efficient stream to find the first finisher
        return heat.getDrivers().values().stream()
            .filter(d -> !d.isFinished() && !d.isDnf())
            .filter(d -> hasCompletedRaceRequirements(heat, d))
            .min((d1, d2) -> Long.compare(d1.getTotalTime(), d2.getTotalTime()));
    }

    /**
     * Checks if the driver crossed the finish line
     */
    private boolean hasCrossedFinishLine(Heats heat, Driver driver) {
        // Check if at least one lap has been completed
        return driver.getLapCount() > 0;
    }

    /**
     * Announces the winner
     */
    private void announceWinner(Heats heat, Driver driver) {
        String winnerName = "Unknown";

        Player player = Bukkit.getPlayer(driver.getUuid());
        if (player != null) {
            winnerName = player.getName();
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "🏁 CHECKERED FLAG! 🏁");
        Bukkit.broadcastMessage(ChatColor.GREEN + "🏆 WINNER: " + ChatColor.WHITE + winnerName);
        Bukkit.broadcastMessage(ChatColor.GRAY + "Final position: " + ChatColor.YELLOW + "#" + driver.getPosition());
        Bukkit.broadcastMessage(ChatColor.GRAY + "Tempo: " + ChatColor.YELLOW + formatTime(driver.getTotalTime()));
        Bukkit.broadcastMessage("");
    }

    /**
     * Finalizes all drivers who completed the race
     */
    private void finalizeAllDrivers(Heats heat) {
        // Optimization: Process only drivers who completed requirements
        heat.getDrivers().values().stream()
            .filter(d -> !d.isFinished() && !d.isDnf())
            .filter(d -> hasCompletedRaceRequirements(heat, d))
            .forEach(d -> finalizeDriver(heat, d));
    }

    /**
     * Formats time from milliseconds to a readable format
     */
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        long ms = milliseconds % 1000;

        return String.format("%d:%02d.%03d", minutes, remainingSeconds, ms);
    }

    /**
     * Resets the manager
     */
    public void reset() {
        checkeredFlagShown = false;
        winner = null;
    }

    /**
     * Checks if the checkered flag was shown
     */
    public boolean isCheckeredFlagShown() {
        return checkeredFlagShown;
    }

    /**
     * Gets the winner
     */
    public Optional<Driver> getWinner() {
        return Optional.ofNullable(winner);
    }
}

