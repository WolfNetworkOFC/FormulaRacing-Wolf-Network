package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.*;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.TVCamera.TVCamera;
import dev.EfraGroup.formulaRacing.TVCamera.TVCameraController;
import dev.EfraGroup.formulaRacing.TVCamera.TVCamPlayer;
import dev.EfraGroup.formulaRacing.TVCamera.TVCameraListener;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

@CommandAlias("cam")
@Description("Comandos de controle de câmera para espectadores")
public class CamCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final TVCameraController controller;
    private final TVCameraListener listener;

    public CamCommand(FormulaRacing plugin, TVCameraController controller, TVCameraListener listener) {
        this.plugin = plugin;
        this.controller = controller;
        this.listener = listener;
    }

    @Default
    @CatchUnknown
    @Description("Exibe a ajuda dos comandos de câmera")
    public void onHelp(Player player) {
        player.sendMessage("§b§lTVCameras Help");
        player.sendMessage("§b/cam edit §7- Toggle edit mode for nearest track");
        player.sendMessage("§b/cam set [index] [label] §7- Set camera at your position with region");
        player.sendMessage("§b/cam view <index> §7- Teleport to a camera");
        player.sendMessage("§b/cam follow <player> §7- Follow a player through cameras");
        player.sendMessage("§b/cam stopfollow §7- Stop following");
        player.sendMessage("§b/cam list §7- List cameras on nearest track");
        player.sendMessage("§b/cam menu §7- Open camera menu");
    }

    @Subcommand("edit|e")
    @CommandPermission("cameras.edit")
    @Description("Toggle edit mode for the nearest track")
    public void onEdit(Player player) {
        if (!isSpectator(player)) return;
        TVCamPlayer tvp = TVCameraController.getPlayer(player.getUniqueId());
        if (!tvp.isEditing()) {
            String trackName = controller.getNearestTrackName(player);
            if (trackName == null) {
                player.sendMessage("§cNo track found nearby.");
                return;
            }
            tvp.startEditing(trackName.replaceAll("\\s+", ""));
            player.sendMessage("§aStarted editing cameras at §f" + trackName);
        } else {
            tvp.stopEditing();
            player.sendMessage("§7Stopped editing cameras.");
        }
    }

    @Subcommand("set|s")
    @CommandPermission("cameras.set")
    @Description("Place a camera at your position")
    @CommandCompletion("<index> [label]")
    public void onCameraSet(Player player, @Optional String index, @Optional String label) {
        if (!isSpectator(player)) return;
        TVCamPlayer tvp = TVCameraController.getPlayer(player.getUniqueId());
        if (!tvp.isEditing()) {
            player.sendMessage("§cEnter edit mode first!");
            return;
        }

        int camIndex;
        boolean remove = false;

        if (index != null) {
            remove = index.startsWith("-");
            String numStr = remove ? index.substring(1) : index;
            try {
                camIndex = Integer.parseInt(numStr);
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid number!");
                return;
            }
        } else {
            camIndex = controller.getCamerasForTrack(tvp.getEditingTrack()).size() + 1;
        }

        if (remove) {
            TVCamera existing = controller.getCamera(tvp.getEditingTrack(), camIndex);
            if (existing != null) {
                controller.removeCamera(existing.getId());
                player.sendMessage("§7Camera " + camIndex + " removed from track.");
            } else {
                player.sendMessage("§cCamera " + camIndex + " not found.");
            }
            return;
        }

        Location loc = player.getLocation();
        Vector min = null, max = null;
        if (tvp.getSelection1() != null && tvp.getSelection2() != null) {
            min = Vector.getMinimum(tvp.getSelection1().toVector(), tvp.getSelection2().toVector());
            max = Vector.getMaximum(tvp.getSelection1().toVector(), tvp.getSelection2().toVector());
        }

        if (min == null || max == null) {
            player.sendMessage("§cNo region selected. Use a stick to select a region first.");
        }

        TVCamera camera = new TVCamera(0, tvp.getEditingTrack(), loc, camIndex, min, max, label);
        controller.addCamera(camera);
        player.sendMessage("§aCamera " + camIndex + " set to your position on track.");
    }

    @Subcommand("view|v")
    @CommandPermission("cameras.view")
    @Description("Teleport to a camera")
    @CommandCompletion("<index>")
    public void onViewCamera(Player player, int index) {
        if (!isSpectator(player)) return;
        String trackName = controller.getNearestTrackName(player);
        if (trackName == null) {
            player.sendMessage("§cNo track found nearby.");
            return;
        }
        TVCamera cam = controller.getCamera(trackName.replaceAll("\\s+", ""), index);
        if (cam != null) {
            cam.tpPlayer(player);
            player.sendMessage("§aTeleported to camera " + index);
        } else {
            player.sendMessage("§cCamera " + index + " not found for this track.");
        }
    }

    @Subcommand("follow|f")
    @CommandPermission("cameras.follow")
    @Description("Follow a player through cameras")
    @CommandCompletion("@players")
    public void onFollow(Player player, @Flags("other") Player target) {
        if (!isSpectator(player)) return;
        controller.startFollowingNormal(player, target);
    }

    @Subcommand("stopfollow|sf|stop")
    @CommandPermission("cameras.stopfollow")
    @Description("Stop following a player")
    public void onStopFollow(Player player) {
        if (!isSpectator(player)) return;
        if (!controller.stopFollowingNormal(player)) {
            player.sendMessage("§cYou were not following anyone.");
        }
    }

    @Subcommand("list|l")
    @CommandPermission("cameras.list")
    @Description("List cameras on the nearest track")
    public void onListCameras(Player player) {
        if (!isSpectator(player)) return;
        String trackName = controller.getNearestTrackName(player);
        if (trackName == null) {
            player.sendMessage("§cNo track found nearby.");
            return;
        }
        java.util.List<TVCamera> cams = controller.getCamerasForTrack(trackName.replaceAll("\\s+", ""));
        if (cams.isEmpty()) {
            player.sendMessage("§eNo cameras for track §f" + trackName);
            return;
        }
        StringBuilder sb = new StringBuilder("§eCameras for §f" + trackName + "§e: ");
        for (TVCamera cam : cams) {
            sb.append(cam.getCamIndex()).append(" ");
        }
        player.sendMessage(sb.toString().trim());
    }

    @Subcommand("menu|m")
    @CommandPermission("cameras.menu")
    @Description("Open the camera menu")
    public void onMenu(Player player) {
        if (!isSpectator(player)) return;
        listener.openCameraMenu(player);
    }

    private boolean isSpectator(Player player) {
        if (player.getGameMode() != GameMode.SPECTATOR) {
            player.sendMessage("§cYou need to be in §lSpectator §cmode to use cameras.");
            return false;
        }
        return true;
    }
}
