package dev.EfraGroup.formulaRacing.Heat;

import org.bukkit.Location;

public class GimmickConfig {

    private String schematicName;
    private Location pasteLocation;
    private int triggerLap;
    private boolean permanent;
    private int removeAfterLaps;
    private String announceMessage;
    private boolean enabled;
    private int dbId;

    public GimmickConfig() {
        this.triggerLap = 1;
        this.permanent = true;
        this.removeAfterLaps = 0;
        this.enabled = true;
    }

    public GimmickConfig(String schematicName, Location pasteLocation) {
        this();
        this.schematicName = schematicName;
        this.pasteLocation = pasteLocation;
    }

    public String getSchematicName() {
        return schematicName;
    }

    public void setSchematicName(String schematicName) {
        this.schematicName = schematicName;
    }

    public Location getPasteLocation() {
        return pasteLocation;
    }

    public void setPasteLocation(Location pasteLocation) {
        this.pasteLocation = pasteLocation;
    }

    public int getTriggerLap() {
        return triggerLap;
    }

    public void setTriggerLap(int triggerLap) {
        this.triggerLap = Math.max(1, triggerLap);
    }

    public boolean isPermanent() {
        return permanent;
    }

    public void setPermanent(boolean permanent) {
        this.permanent = permanent;
    }

    public int getRemoveAfterLaps() {
        return removeAfterLaps;
    }

    public void setRemoveAfterLaps(int removeAfterLaps) {
        this.removeAfterLaps = Math.max(0, removeAfterLaps);
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

    public int getDbId() {
        return dbId;
    }

    public void setDbId(int dbId) {
        this.dbId = dbId;
    }

    public boolean shouldRemoveOnLap(int currentLap) {
        if (permanent) return false;
        if (removeAfterLaps <= 0) return false;
        return currentLap >= triggerLap + removeAfterLaps;
    }
}
