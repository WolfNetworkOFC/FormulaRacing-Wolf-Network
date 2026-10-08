package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.ConditionFailedException;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Single;
import co.aikar.commands.annotation.Subcommand;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Utils.trackexchange.TrackExchangeManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@CommandAlias("trackexchange|tex|tx")
@Description("TrackExchange commands")
public class TrackExchangeCommand extends BaseCommand {

    private final TrackExchangeManager trackExchange;
    private final DatabaseManager db;

    public TrackExchangeCommand(TrackExchangeManager trackExchange, DatabaseManager db) {
        this.trackExchange = trackExchange;
        this.db = db;
    }

    @Subcommand("copy")
    @CommandCompletion("@tracks <saveas>")
    @CommandPermission("trackexchange.export")
    public void onCopy(Player player, String trackName, @Optional @Single String saveAs) {
        if (saveAs == null) {
            saveAs = trackName.replaceAll("\\s+", "").toLowerCase();
        }
        if (trackExchangeFileExists(saveAs)) {
            throw new ConditionFailedException("This trackexchange file already exists");
        }
        if (!saveAs.matches("[A-Za-z0-9_]+")) {
            throw new ConditionFailedException("You cannot save a trackexchange track with that name");
        }
        trackExchange.exportTrack(player, trackName, saveAs);
    }

    @Subcommand("paste")
    @CommandCompletion("@trackexchangeFiles <loadas>")
    @CommandPermission("trackexchange.import")
    public void onPaste(Player player, String fileName, @Optional String loadAs) {
        fileName = fileName.replace(".trackexchange", "");
        if (loadAs == null) {
            loadAs = fileName;
        }
        if (!trackExchangeFileExists(fileName)) {
            throw new ConditionFailedException("This trackexchange file does not exist");
        }
        if (db.isTrackExists(loadAs)) {
            throw new ConditionFailedException("A track with this name already exists");
        }
        if (!loadAs.matches("[A-Za-z0-9 ]+")) {
            throw new ConditionFailedException("You cannot load a trackexchange track with that name");
        }
        trackExchange.importTrack(player, fileName, loadAs);
    }

    @Subcommand("undo")
    public void onUndo(Player player) {
        java.util.Optional<Runnable> action = trackExchange.popAction(player.getUniqueId());
        if (action.isEmpty()) {
            throw new ConditionFailedException("You have nothing to undo.");
        }
        action.get().run();
    }

    @Subcommand("reload")
    @CommandPermission("trackexchange.reload")
    public void onReload(CommandSender sender) {
        sender.sendMessage("Reloaded config.");
    }

    private boolean trackExchangeFileExists(String name) {
        String target = name.endsWith(".trackexchange") ? name : name + ".trackexchange";
        for (String file : trackExchange.listFiles()) {
            if (file.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }
}
