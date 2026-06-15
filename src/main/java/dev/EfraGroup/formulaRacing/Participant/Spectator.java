package dev.EfraGroup.formulaRacing.Participant;

import java.util.UUID;

public class Spectator {
    private final UUID uuid;
    private final String playerName;
    private SpectatorMode mode;
    private UUID followingDriverUUID;
    private long joinTime;

    public Spectator(UUID uuid, String playerName) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.mode = Spectator.SpectatorMode.FREE_CAM;
        this.followingDriverUUID = null;
        this.joinTime = System.currentTimeMillis();
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public UUID getPlayerId() {
        return this.uuid;
    }

    public String getPlayerName() {
        return this.playerName;
    }

    public SpectatorMode getMode() {
        return this.mode;
    }

    public void setMode(SpectatorMode mode) {
        this.mode = mode;
    }

    public UUID getFollowingDriverUUID() {
        return this.followingDriverUUID;
    }

    public void setFollowingDriverUUID(UUID followingDriverUUID) {
        this.followingDriverUUID = followingDriverUUID;
        if (followingDriverUUID != null) {
            this.mode = Spectator.SpectatorMode.FOLLOW_DRIVER;
        }

    }

    public long getJoinTime() {
        return this.joinTime;
    }

    public long getWatchTime() {
        return System.currentTimeMillis() - this.joinTime;
    }

    public String toString() {
        return String.format("Spectator{name=%s, mode=%s, following=%s}", this.playerName, this.mode, this.followingDriverUUID);
    }

    public static enum SpectatorMode {
        FREE_CAM,
        FOLLOW_DRIVER,
        BIRD_EYE,
        TV_CAM;
    }
}
