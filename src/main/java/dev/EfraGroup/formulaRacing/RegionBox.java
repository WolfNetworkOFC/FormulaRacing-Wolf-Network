//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Objects;

public class RegionBox {
    private final Location min;
    private final Location max;

    public RegionBox(Location loc1, Location loc2) {
        this.min = new Location(loc1.getWorld(), Math.min(loc1.getX(), loc2.getX()), Math.min(loc1.getY(), loc2.getY()), Math.min(loc1.getZ(), loc2.getZ()));
        this.max = new Location(loc1.getWorld(), Math.max(loc1.getX(), loc2.getX()), Math.max(loc1.getY(), loc2.getY()), Math.max(loc1.getZ(), loc2.getZ()));
    }

    public boolean contains(Location location) {
        return Objects.equals(location.getWorld(), this.min.getWorld()) && location.getX() >= this.min.getX() && location.getX() <= this.max.getX() && location.getY() >= this.min.getY() && location.getY() <= this.max.getY() && location.getZ() >= this.min.getZ() && location.getZ() <= this.max.getZ();
    }

    public boolean contains(Player player) {
        return this.contains(player.getLocation());
    }

    public Location getMin() {
        return this.min;
    }

    public Location getMax() {
        return this.max;
    }

    public Vector getCenter() {
        return new Vector((this.min.getX() + this.max.getX()) / (double)2.0F, (this.min.getY() + this.max.getY()) / (double)2.0F, (this.min.getZ() + this.max.getZ()) / (double)2.0F);
    }
}
