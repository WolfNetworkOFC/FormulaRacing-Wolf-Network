package dev.EfraGroup.formulaRacing.Heat;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Lap {
    private final UUID playerUUID;
    private final int heatId;
    private final String trackNameWS;
    private long lapStart;
    private long lapEnd;
    private boolean pitted;
    private boolean saved;
    private final Map<Integer, Long> checkpointTimes;

    public Lap(UUID playerUUID, int heatId, String trackNameWS) {
        this.playerUUID = playerUUID;
        this.heatId = heatId;
        this.trackNameWS = trackNameWS;
        this.lapStart = System.currentTimeMillis();
        this.lapEnd = 0L;
        this.pitted = false;
        this.saved = false;
        this.checkpointTimes = new ConcurrentHashMap<>();
    }

    public Lap(long startTime) {
        this.playerUUID = null;
        this.heatId = 0;
        this.trackNameWS = null;
        this.lapStart = startTime;
        this.lapEnd = 0L;
        this.pitted = false;
        this.saved = false;
        this.checkpointTimes = new ConcurrentHashMap<>();
    }

    public Lap(int id, UUID playerUUID, int heatId, String trackNameWS, long lapStart, long lapEnd, boolean pitted) {
        this.playerUUID = playerUUID;
        this.heatId = heatId;
        this.trackNameWS = trackNameWS;
        this.lapStart = lapStart;
        this.lapEnd = lapEnd;
        this.pitted = pitted;
        this.saved = true;
        this.checkpointTimes = new ConcurrentHashMap<>();
    }

    public UUID getPlayerUUID() {
        return this.playerUUID;
    }

    public int getHeatId() {
        return this.heatId;
    }

    public String getTrackNameWS() {
        return this.trackNameWS;
    }

    public long getLapStart() {
        return this.lapStart;
    }

    public void setLapStart(long lapStart) {
        this.lapStart = lapStart;
    }

    public long getLapEnd() {
        return this.lapEnd;
    }

    public void setLapEnd(long lapEnd) {
        this.lapEnd = lapEnd;
    }

    public boolean hasPitted() {
        return this.pitted;
    }

    public boolean isPitted() {
        return this.pitted;
    }

    public void setPitted(boolean pitted) {
        this.pitted = pitted;
    }

    public boolean isSaved() {
        return this.saved;
    }

    public void setSaved(boolean saved) {
        this.saved = saved;
    }

    public long getStartTime() {
        return this.lapStart;
    }

    public Map<Integer, Long> getCheckpointTimes() {
        return this.checkpointTimes;
    }

    public Map<Integer, Long> getRelativeCheckpointTimes() {
        Map<Integer, Long> relative = new ConcurrentHashMap<>();

        for(Map.Entry<Integer, Long> entry : this.checkpointTimes.entrySet()) {
            relative.put((Integer)entry.getKey(), (Long)entry.getValue() - this.lapStart);
        }

        return relative;
    }

    public void finishLap(long endTime) {
        this.lapEnd = endTime;
    }

    public long getLapTime() {
        return this.lapEnd > 0L ? this.lapEnd - this.lapStart : System.currentTimeMillis() - this.lapStart;
    }

    public void recordCheckpointTime(int checkpointId, long timestamp) {
        this.checkpointTimes.put(checkpointId, timestamp);
    }

    public Long getCheckpointTime(int checkpointId) {
        return (Long)this.checkpointTimes.get(checkpointId);
    }

    public Long getRelativeCheckpointTime(int checkpointId) {
        Long timestamp = (Long)this.checkpointTimes.get(checkpointId);
        return timestamp == null ? null : timestamp - this.lapStart;
    }

    public int getLatestCheckpoint() {
        return (Integer)this.checkpointTimes.keySet().stream().max(Integer::compareTo).orElse(0);
    }

    public String toString() {
        String var10000 = String.valueOf(this.playerUUID);
        return "Lap{playerUUID=" + var10000 + ", heatId=" + this.heatId + ", trackNameWS='" + this.trackNameWS + "', lapStart=" + this.lapStart + ", lapEnd=" + this.lapEnd + ", pitted=" + this.pitted + ", saved=" + this.saved + ", lapTime=" + this.getLapTime() + "}";
    }
}
