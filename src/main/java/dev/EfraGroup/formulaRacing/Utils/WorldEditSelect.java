package dev.EfraGroup.formulaRacing.Utils;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.bukkit.BukkitPlayer;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class WorldEditSelect {

    private static Region getRegion(Player player) {
        BukkitPlayer bukkitPlayer = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(bukkitPlayer);
        try {
            return session.getSelection(bukkitPlayer.getWorld());
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasSelection(Player player) {
        return getRegion(player) != null;
    }

    public static Location getMin(Player player) {
        Region region = getRegion(player);
        if (region == null) return null;

        return new Location(player.getWorld(),
                region.getMinimumPoint().getX(),
                region.getMinimumPoint().getY(),
                region.getMinimumPoint().getZ());
    }

    public static Location getMax(Player player) {
        Region region = getRegion(player);
        if (region == null) return null;

        return new Location(player.getWorld(),
                region.getMaximumPoint().getX(),
                region.getMaximumPoint().getY(),
                region.getMaximumPoint().getZ());
    }
}
