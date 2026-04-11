package dev.EfraGroup.formulaRacing.Database;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

public class GridPosition {
    private final int position;
    private final double x;
    private final double y;
    private final double z;
    private final double yaw;
    private final double pitch;
    private final String world;

    public GridPosition(int position, double x, double y, double z, double yaw, double pitch, String world) {
        this.position = position;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.world = world;
    }

    public int getPosition() {
        return this.position;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }

    public double getZ() {
        return this.z;
    }

    public double getYaw() {
        return this.yaw;
    }

    public double getPitch() {
        return this.pitch;
    }

    public String getWorld() {
        return this.world;
    }

    public Location toLocation(Server server) {
        World w = server.getWorld(this.world);
        return w == null ? null : new Location(w, this.x, this.y, this.z, (float)this.yaw, (float)this.pitch);
    }
}
