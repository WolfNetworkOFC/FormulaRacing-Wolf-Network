package dev.EfraGroup.formulaRacing.Heat;

import java.util.UUID;

public class Lap {

    private final UUID playerUUID;
    private final int heatId;
    private final String trackNameWS;
    private long lapStart; // timestamp em ms
    private long lapEnd;   // timestamp em ms
    private boolean pitted; // <— era static, removido
    private boolean saved;  // marca se já foi salvo no DB

    public Lap(UUID playerUUID, int heatId, String trackNameWS) {
        this.playerUUID = playerUUID;
        this.heatId = heatId;
        this.trackNameWS = trackNameWS;
        this.lapStart = System.currentTimeMillis();
        this.lapEnd = 0;
        this.pitted = false;
        this.saved = false;
    }

    // Construtor usado ao carregar do banco
    public Lap(int id, UUID playerUUID, int heatId, String trackNameWS, long lapStart, long lapEnd, boolean pitted) {
        this.playerUUID = playerUUID;
        this.heatId = heatId;
        this.trackNameWS = trackNameWS;
        this.lapStart = lapStart;
        this.lapEnd = lapEnd;
        this.pitted = pitted;
        this.saved = true;
    }

    // =======================
    // 🔹 GETTERS / SETTERS
    // =======================
    public UUID getPlayerUUID() { return playerUUID; }

    public int getHeatId() { return heatId; }

    public String getTrackNameWS() { return trackNameWS; }

    public long getLapStart() { return lapStart; }

    public void setLapStart(long lapStart) { this.lapStart = lapStart; }

    public long getLapEnd() { return lapEnd; }

    public void setLapEnd(long lapEnd) { this.lapEnd = lapEnd; }

    public boolean hasPitted() { return pitted; } // ✅ método certo para stream/filter

    public void setPitted(boolean pitted) { this.pitted = pitted; }

    public boolean isSaved() { return saved; }

    public void setSaved(boolean saved) { this.saved = saved; }

    // =======================
    // 🔹 UTILITÁRIOS
    // =======================
    public long getLapTime() {
        if (lapEnd > 0) {
            return lapEnd - lapStart;
        }
        return System.currentTimeMillis() - lapStart;
    }

    @Override
    public String toString() {
        return "Lap{" +
                "playerUUID=" + playerUUID +
                ", heatId=" + heatId +
                ", trackNameWS='" + trackNameWS + '\'' +
                ", lapStart=" + lapStart +
                ", lapEnd=" + lapEnd +
                ", pitted=" + pitted +
                ", saved=" + saved +
                ", lapTime=" + getLapTime() +
                '}';
    }
}
