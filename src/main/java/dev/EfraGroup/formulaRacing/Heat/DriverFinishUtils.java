package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.EventAnnouncements;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import java.util.UUID;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class DriverFinishUtils {
    public static boolean canFinishRace(Driver driver, Heats heat) {
        if (driver.getLapCount() < heat.getTotalLaps()) {
            return false;
        } else {
            return heat.getTotalPits() == null || heat.getTotalPits() <= 0 || driver.getPitstops() >= heat.getTotalPits();
        }
    }

    public static boolean finishDriver(Driver driver, Heats heat, FormulaRacing plugin) {
        Player player = plugin.getServer().getPlayer(driver.getUuid());
        if (!canFinishRace(driver, heat)) {
            if (player != null) {
                if (driver.getLapCount() < heat.getTotalLaps()) {
                    player.sendMessage(String.valueOf(ChatColor.RED) + "✗ Você ainda não completou todas as voltas!");
                    String var4 = String.valueOf(ChatColor.GRAY);
                    player.sendMessage(var4 + "Voltas: " + String.valueOf(ChatColor.YELLOW) + driver.getLapCount() + String.valueOf(ChatColor.GRAY) + "/" + String.valueOf(ChatColor.WHITE) + heat.getTotalLaps());
                } else if (driver.getPitstops() < heat.getTotalPits()) {
                    player.sendMessage(String.valueOf(ChatColor.RED) + "✗ Você ainda não completou todos os pit stops obrigatórios!");
                    String var5 = String.valueOf(ChatColor.GRAY);
                    player.sendMessage(var5 + "Pit Stops: " + String.valueOf(ChatColor.YELLOW) + driver.getPitstops() + String.valueOf(ChatColor.GRAY) + "/" + String.valueOf(ChatColor.WHITE) + heat.getTotalPits());
                }
            }

            return false;
        } else {
            driver.setFinished(true);
            driver.setEndTime(System.currentTimeMillis());
            if (player != null && plugin.getPTP() != null) {
                plugin.getPTP().disablePTP(player, driver);
            } else {
                driver.setPtpActive(false);
                driver.setPtpEnergy((double)0.0F);
            }
            heat.updateLivePositions();
            broadcastFinish(driver, heat, plugin);
            DebugManager var10000 = plugin.getDebugManager();
            String var10001 = String.valueOf(driver.getUuid());
            var10000.logRaceSystem("Driver finalizado: " + var10001 + " - Posição: " + driver.getPosition());
            return true;
        }
    }

    public static boolean allDriversFinished(Heats heat) {
        return heat.getDrivers().values().stream().allMatch((driver) -> driver.isFinished() || driver.isDnf());
    }

    private static void broadcastFinish(Driver driver, Heats heat, FormulaRacing plugin) {
        EventAnnouncements announcements = heat.getRound() != null && heat.getRound().getEvent() != null ? heat.getRound().getEvent().getAnnouncements() : plugin.getEventAnnouncements();
        announcements.broadcastFinish(heat, driver, formatTime(driver.getTotalTime()));
    }

    private static String formatTime(long millis) {
        long seconds = millis / 1000L;
        long ms = millis % 1000L;
        long minutes = seconds / 60L;
        seconds %= 60L;
        return minutes > 0L ? String.format("%d:%02d.%03d", minutes, seconds, ms) : String.format("%d.%03d", seconds, ms);
    }

    private static String getPlayerName(UUID uuid, FormulaRacing plugin) {
        Player player = plugin.getServer().getPlayer(uuid);
        return player != null ? player.getName() : uuid.toString().substring(0, 8);
    }
}
