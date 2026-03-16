//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.RegionBox;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import org.bukkit.Location;

public interface SessionLogic {
    void start(Heats var1);

    boolean passLap(Heats var1, Driver var2);

    default boolean passLap(Heats heat, Driver driver, Location from, Location to, RegionBox region) {
        return this.passLap(heat, driver);
    }
}
