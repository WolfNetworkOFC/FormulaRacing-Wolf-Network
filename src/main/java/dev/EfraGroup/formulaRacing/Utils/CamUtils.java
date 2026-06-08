/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 *
 * Could not load the following classes:
 *  dev.EfraGroup.formulaRacing.Database.DatabaseManager
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 */
package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;

import java.util.*;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class CamUtils {
    private final Map<UUID, UUID> followingNormal = new HashMap<>();
    private final Map<UUID, Location> lastCameraNormal = new HashMap<>();
    private final DatabaseManager mysql;
    private final FormulaRacing plugin;

    public CamUtils(DatabaseManager mysql, FormulaRacing plugin) {
        this.mysql = mysql;
        this.plugin = plugin;
    }

    public void startFollowingNormal(Player follower, Player target) {
        this.followingNormal.put(follower.getUniqueId(), target.getUniqueId());
        this.lastCameraNormal.remove(follower.getUniqueId());
        Location nearest = this.getNearestCamera(target);
        if (nearest != null) {
            SchedulerHelper.teleport(follower, nearest);
            this.lastCameraNormal.put(follower.getUniqueId(), nearest);
        }
        String langCode = this.mysql.getPlayerLanguage(follower.getUniqueId());
        follower.sendMessage(this.plugin.getTranslation("cam_started_following", langCode, "{player}", target.getName()));
    }

    public boolean stopFollowingNormal(Player follower) {
        UUID uuid = follower.getUniqueId();
        if (this.followingNormal.containsKey(uuid)) {
            this.followingNormal.remove(uuid);
            this.lastCameraNormal.remove(uuid);
            String langCode = this.mysql.getPlayerLanguage(follower.getUniqueId());
            follower.sendMessage(this.plugin.getDirectTranslation("cam_stopped_following", langCode));
            return true;
        }
        return false;
    }

    public boolean isFollowingNormal(Player follower) {
        return this.followingNormal.containsKey(follower.getUniqueId());
    }

    public Player getTargetNormal(Player follower) {
        UUID targetId = this.followingNormal.get(follower.getUniqueId());
        return targetId != null ? Bukkit.getPlayer(targetId) : null;
    }

    public void updateFollowersNormal() {
        for (UUID followerId : this.followingNormal.keySet()) {
            Location last;
            Location nearest;
            Player target;
            Player follower = Bukkit.getPlayer(followerId);
            if (follower == null || !follower.isOnline() || (target = this.getTargetNormal(follower)) == null || !target.isOnline() || (nearest = this.getNearestCamera(target)) == null || (last = this.lastCameraNormal.get(followerId)) != null && this.locationsEqualBlock(last, nearest)) continue;
            SchedulerHelper.teleport(follower, nearest);
            this.lastCameraNormal.put(followerId, nearest);
        }
    }

    public Location getNearestCamera(Player target) {
        // 1. Especificar o tipo da lista <Location>
        List<Location> allCameras = this.mysql.getAllCameras();

        if (allCameras == null || allCameras.isEmpty()) {
            return null;
        }

        Location targetLoc = target.getLocation();

        // 2. Usar o Stream com o comparador de distância
        // Nota: Usamos distanceSquared por ser muito mais leve que distance (evita raiz quadrada)
        return allCameras.stream()
                .filter(loc -> Objects.equals(loc.getWorld(), targetLoc.getWorld())) // Filtro de segurança para o mesmo mundo
                .min(Comparator.comparingDouble(loc -> loc.distanceSquared(targetLoc)))
                .orElse(null);
    }

    private boolean locationsEqualBlock(Location a, Location b) {
        if (a == null || b == null) {
            return false;
        }
        return Objects.equals(a.getWorld(),b.getWorld()) && a.getBlockX() == b.getBlockX() && a.getBlockY() == b.getBlockY() && a.getBlockZ() == b.getBlockZ();
    }
}