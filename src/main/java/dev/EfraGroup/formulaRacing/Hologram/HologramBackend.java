package dev.EfraGroup.formulaRacing.Hologram;

import java.util.List;
import org.bukkit.Location;

/**
 * Abstraction over the underlying hologram implementation, so the plugin can
 * switch between the built-in ArmorStand holograms and an external
 * HolographicDisplays-backed hologram without touching caller code.
 */
public interface HologramBackend {

    /**
     * Creates/initialises the hologram at the given location with the given lines.
     */
    void create(String name, Location loc, List<String> lines);

    /**
     * @return the display name used to register/track this hologram.
     */
    String getName();

    /**
     * Updates all lines of the hologram in place.
     */
    void updateLines(List<String> lines);

    /**
     * Removes the hologram from the world.
     */
    void remove();

    Location getLocation();
}
