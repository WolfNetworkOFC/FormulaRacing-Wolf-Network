package dev.EfraGroup.formulaRacing;

import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;

public interface NMSHandler {
    default void setBoatType(String entityType) {
    }

    Boat spawnBoat(Location location, boolean collidable);

    ChestBoat spawnChestBoat(Location location, boolean collidable);
}