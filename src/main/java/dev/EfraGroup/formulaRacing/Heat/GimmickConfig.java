package dev.EfraGroup.formulaRacing.Heat;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Definition of a gimmick: a schematic saved by an admin for a specific track,
 * plus the position where it must be pasted.
 *
 * <p>The definition is global (one per track + name) and stored in
 * {@code fr_gimmicks}; which heat pastes it, and on which lap, is stored
 * separately in {@code fr_heat_gimmicks} (see {@link GimmickSchedule}).</p>
 */
public class GimmickConfig {

    private int id;
    private String name;
    private String trackNameWS;
    private String worldName;
    private double x;
    private double y;
    private double z;
    /** true when the admin saved it with {@code -a}: the pasted air replaces the blocks in the area. */
    private boolean pasteWithAir;
    private String announceMessage;
    private boolean enabled = true;
    private String createdBy;
    private long createdAt;

    public GimmickConfig() {
        this.enabled = true;
    }

    public GimmickConfig(String name, String trackNameWS, Location pasteLocation) {
        this();
        this.name = name;
        this.trackNameWS = trackNameWS;
        setPasteLocation(pasteLocation);
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTrackNameWS() {
        return trackNameWS;
    }

    public void setTrackNameWS(String trackNameWS) {
        this.trackNameWS = trackNameWS;
    }

    public String getWorldName() {
        return worldName;
    }

    public void setWorldName(String worldName) {
        this.worldName = worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public void setCoordinates(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Anchor where the clipboard origin is pasted again (the position the admin stood on). */
    public Location getPasteLocation() {
        if (worldName == null) return null;
        World world = Bukkit.getWorld(worldName);
        return world == null ? null : new Location(world, x, y, z);
    }

    public final void setPasteLocation(Location location) {
        if (location == null) return;
        this.worldName = location.getWorld() != null ? location.getWorld().getName() : null;
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
    }

    public boolean isPasteWithAir() {
        return pasteWithAir;
    }

    public void setPasteWithAir(boolean pasteWithAir) {
        this.pasteWithAir = pasteWithAir;
    }

    /** WorldEdit equivalent of the flag: without {@code -a} the air of the schematic is ignored. */
    public boolean isIgnoreAirBlocks() {
        return !pasteWithAir;
    }

    public String getAnnounceMessage() {
        return announceMessage;
    }

    public void setAnnounceMessage(String announceMessage) {
        this.announceMessage = announceMessage;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /** Comparison used by every lookup, so {@code /gimmick} never cares about case. */
    public static String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Track names are stored without spaces and lower case, exactly like {@code trackNameWS}. */
    public static String normalizeTrack(String trackName) {
        return trackName == null ? null : trackName.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }
}
