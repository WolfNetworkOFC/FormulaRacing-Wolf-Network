//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing;

import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;

public interface NMSHandler {
    default void setBoatType(String entityType) {
    }

    Boat spawnBoat(Location var1);

    ChestBoat spawnChestBoat(Location var1);
}
