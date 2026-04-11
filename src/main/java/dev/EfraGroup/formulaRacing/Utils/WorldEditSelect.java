/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 *
 * Could not load the following classes:
 *  com.sk89q.worldedit.LocalSession
 *  com.sk89q.worldedit.WorldEdit
 *  com.sk89q.worldedit.bukkit.BukkitAdapter
 *  com.sk89q.worldedit.bukkit.BukkitPlayer
 *  com.sk89q.worldedit.regions.Region
 *  com.sk89q.worldedit.session.SessionOwner
 *  org.bukkit.Location
 *  org.bukkit.entity.Player
 */
package dev.EfraGroup.formulaRacing.Utils;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.SessionOwner;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class WorldEditSelect {
    private static Region getRegion(Player player) {
        BukkitPlayer bukkitPlayer = BukkitAdapter.adapt((Player)player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get((SessionOwner)bukkitPlayer);
        try {
            return session.getSelection(bukkitPlayer.getWorld());
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasSelection(Player player) {
        return WorldEditSelect.getRegion(player) != null;
    }

    public static Location getMin(Player player) {
        Region region = WorldEditSelect.getRegion(player);
        if (region == null) {
            return null;
        }
        return new Location(player.getWorld(), (double)region.getMinimumPoint().getX(), (double)region.getMinimumPoint().getY(), (double)region.getMinimumPoint().getZ());
    }

    public static Location getMax(Player player) {
        Region region = WorldEditSelect.getRegion(player);
        if (region == null) {
            return null;
        }
        return new Location(player.getWorld(), (double)region.getMaximumPoint().getX() + 1.0, (double)region.getMaximumPoint().getY() + 1.0, (double)region.getMaximumPoint().getZ() + 1.0);
    }
}
