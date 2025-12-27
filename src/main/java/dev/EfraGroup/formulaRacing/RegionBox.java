package dev.EfraGroup.formulaRacing;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class RegionBox {
    private final Location min;
    private final Location max;

    public RegionBox(Location min, Location max) {
        this.min = min;
        this.max = max;
    }

    public boolean contains(Location location) {
        return location.getWorld().equals(min.getWorld()) &&
                location.getX() >= min.getX() && location.getX() <= max.getX() &&
                location.getY() >= min.getY() && location.getY() <= max.getY() &&
                location.getZ() >= min.getZ() && location.getZ() <= max.getZ();
    }

    public boolean contains(Player player) {
        return contains(player.getLocation());
    }

    public Location getMin() {
        return min;
    }

    public Location getMax() {
        return max;
    }

    public Vector getCenter() {
        return new Vector(
                (min.getX() + max.getX()) / 2,
                (min.getY() + max.getY()) / 2,
                (min.getZ() + max.getZ()) / 2
        );
    }
}
