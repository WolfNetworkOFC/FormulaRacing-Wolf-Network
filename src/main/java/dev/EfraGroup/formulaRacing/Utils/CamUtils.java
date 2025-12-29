package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public class CamUtils {

    // --- Following Normal ---
    private final Map<UUID, UUID> followingNormal = new HashMap<>();
    private final Map<UUID, Location> lastCameraNormal = new HashMap<>();

    private final DatabaseManager mysql;
    private final FormulaRacing plugin;

    public CamUtils(DatabaseManager mysql, FormulaRacing plugin) {
        this.mysql = mysql;
        this.plugin = plugin;
    }

    // ======================
    // FOLLOWING NORMAL
    // ======================
    public void startFollowingNormal(Player follower, Player target) {
        followingNormal.put(follower.getUniqueId(), target.getUniqueId());
        lastCameraNormal.remove(follower.getUniqueId()); // reseta câmera anterior

        // Teleporta imediatamente para a câmera mais próxima
        Location nearest = getNearestCamera(target);
        if (nearest != null) {
            follower.teleport(nearest);
            lastCameraNormal.put(follower.getUniqueId(), nearest);
        }

        String langCode = mysql.getPlayerLanguage(follower.getUniqueId());
        follower.sendMessage(plugin.getTranslation("cam_started_following", langCode, "{player}", target.getName()));
    }

    public boolean stopFollowingNormal(Player follower) {
        UUID uuid = follower.getUniqueId();
        if (followingNormal.containsKey(uuid)) {
            followingNormal.remove(uuid);
            lastCameraNormal.remove(uuid);
            String langCode = mysql.getPlayerLanguage(follower.getUniqueId());
            follower.sendMessage(plugin.getDirectTranslation("cam_stopped_following", langCode));
            return true;
        }
        return false;
    }

    public boolean isFollowingNormal(Player follower) {
        return followingNormal.containsKey(follower.getUniqueId());
    }

    public Player getTargetNormal(Player follower) {
        UUID targetId = followingNormal.get(follower.getUniqueId());
        return targetId != null ? Bukkit.getPlayer(targetId) : null;
    }

    /** Atualiza todos os seguidores normais */
    public void updateFollowersNormal() {
        for (UUID followerId : followingNormal.keySet()) {
            Player follower = Bukkit.getPlayer(followerId);
            if (follower == null || !follower.isOnline()) continue;

            Player target = getTargetNormal(follower);
            if (target == null || !target.isOnline()) continue;

            Location nearest = getNearestCamera(target);
            if (nearest == null) continue;

            Location last = lastCameraNormal.get(followerId);
            if (last == null || !locationsEqualBlock(last, nearest)) {
                follower.teleport(nearest);
                lastCameraNormal.put(followerId, nearest);
            }
        }
    }

    // ======================
    // UTILIDADES
    // ======================
    public Location getNearestCamera(Player target) {
        List<Location> allCameras = mysql.getAllCameras();
        if (allCameras.isEmpty()) return null;

        Location targetLoc = target.getLocation();
        return allCameras.stream()
                .min(Comparator.comparingDouble(c -> c.distanceSquared(targetLoc)))
                .orElse(null);
    }

    private boolean locationsEqualBlock(Location a, Location b) {
        if (a == null || b == null) return false;
        return a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
