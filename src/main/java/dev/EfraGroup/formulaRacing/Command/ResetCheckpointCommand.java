package dev.EfraGroup.formulaRacing.Command;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ResetCheckpointCommand implements CommandExecutor {

    private final FormulaRacing plugin;
    private final DatabaseManager mysql;

    public ResetCheckpointCommand(FormulaRacing plugin, DatabaseManager mysql) {
        this.plugin = plugin;
        this.mysql = mysql;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command cannot be executed from the console.");
            return true;
        }

        Optional<Heats> heatOpt = this.plugin.getRaceEventManager().getPlayerActiveHeat(player.getUniqueId());
        if (heatOpt.isEmpty()) {
            this.plugin.sendMessage(player, "resetcp_not_in_heat", new String[0]);
            return true;
        }

        Heats heat = heatOpt.get();
        Driver driver = heat.getDriver(player.getUniqueId());
        if (driver == null) {
            this.plugin.sendMessage(player, "resetcp_not_in_heat", new String[0]);
            return true;
        }

        String trackNameWS = heat.getTrackNameWS();
        int checkpointsReached = driver.getCheckpointsReached();
        Location targetLoc = null;

        if (checkpointsReached > 0) {
            List<DatabaseManager.RegionData> checkpointList =
                this.plugin.getTrackIntegrationManager().getCheckpointById(trackNameWS, checkpointsReached - 1);
            if (checkpointList != null && !checkpointList.isEmpty()) {
                DatabaseManager.RegionData cp = checkpointList.get(0);
                targetLoc = new Location(
                    Bukkit.getWorld(cp.getWorld()),
                    (cp.getMinX() + cp.getMaxX()) / 2.0,
                    cp.getMaxY() - 0.5,
                    (cp.getMinZ() + cp.getMaxZ()) / 2.0,
                    player.getLocation().getYaw(),
                    player.getLocation().getPitch()
                );
            }
        }

        if (targetLoc == null) {
            targetLoc = this.plugin.getTrackIntegrationManager().getTrackSpawn(trackNameWS);
        }

        if (targetLoc == null) {
            this.plugin.sendMessage(player, "resetcp_no_spawn", new String[0]);
            return true;
        }

        final Location finalLoc = targetLoc;
        boolean success = SchedulerHelper.teleport(player, finalLoc);
        if (success) {
            this.plugin.sendMessage(player, "resetcp_teleported", new String[0]);
        }

        return true;
    }
}
