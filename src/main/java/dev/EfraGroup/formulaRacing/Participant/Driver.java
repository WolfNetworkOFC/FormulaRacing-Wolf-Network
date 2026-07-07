package dev.EfraGroup.formulaRacing.Participant;

import dev.EfraGroup.formulaRacing.RegionBox;
import dev.EfraGroup.formulaRacing.Event.Driver.DriverFinishLapEvent;
import dev.EfraGroup.formulaRacing.Heat.Lap;
import dev.EfraGroup.formulaRacing.Heat.Logic.TireCompound;
import dev.EfraGroup.formulaRacing.Heat.PitStopManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;

public class Driver {
    private int id;
    private final UUID uuid;
    private final int heatId;
    private int position;
    private int startPosition;
    private Long startTime;
    private Long endTime;
    private int pitstops;
    private Long qualifyingTime;
    private final List<Lap> laps;
    private Lap currentLap;
    private int checkpointsReached;
    private DriverState state;
    private boolean hasDrsPermission;
    private BossBar drsBossBar;
    private boolean hasdrs;
    private double ptpEnergy;
    private boolean ptpActive;
    private String cachedDelta;
    private int lastProcessedCheckpointId;
    private Map<Integer, Long> bestLapCheckpointTimes;
    private final Map<Integer, Long> sessionCheckpointTimes;
    private long lastCheckpointTime;
    private int resetCount;
    private boolean lagStartPassed;
    private boolean lagEndPassed;
    private String ersmode;
    private double ersenergy;
    private TireCompound tireCompound;
    private int tireWear;
    private String customName;
    private boolean aiControlled;
    private double fuelLevel;
    private double fuelCapacity;

    public Driver(UUID uuid, int heatId, int startPosition) {
        this.state = DriverState.SETUP;
        this.hasDrsPermission = false;
        this.drsBossBar = null;
        this.hasdrs = false;
        this.ptpEnergy = (double)0.0F;
        this.ptpActive = false;
        this.cachedDelta = "";
        this.lastProcessedCheckpointId = -1;
        this.bestLapCheckpointTimes = new HashMap();
        this.sessionCheckpointTimes = new HashMap();
        this.lastCheckpointTime = 0L;
        this.lagStartPassed = false;
        this.lagEndPassed = false;
        this.uuid = uuid;
        this.heatId = heatId;
        this.startPosition = startPosition;
        this.position = startPosition;
        this.pitstops = 0;
        this.laps = new ArrayList();
        this.checkpointsReached = 0;
        this.state = DriverState.SETUP;
        this.ersenergy = 50;
        this.ersmode = "Disabled";
        this.tireCompound = TireCompound.MEDIUM;
        this.tireWear = 0;
        this.customName = null;
        this.aiControlled = false;
        this.fuelCapacity = 100.0D;
        this.fuelLevel = 100.0D;

    }

    public void setErsEnergy(double value) {
        this.ersenergy = value;
    }
    public double getErsEnergy() {
        return this.ersenergy;
    }


    public int getId() {
        return this.id;
    }

    public void setErsMode(String id) {
        this.ersmode = id;
    }

    public String getErsMode() {
        return this.ersmode;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setPtpActive(boolean ptpActive) {
        this.ptpActive = ptpActive;
    }

    public boolean isPtpActive() {
        return this.ptpActive;
    }

    public double getPtpEnergy() {
        return this.ptpEnergy;
    }

    public void setPtpEnergy(double ptpEnergy) {
        this.ptpEnergy = ptpEnergy;
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public boolean hasDrsPermission() {
        return this.hasDrsPermission;
    }

    public void setDrsPermission(boolean hasDrsPermission) {
        this.hasDrsPermission = hasDrsPermission;
    }

    public BossBar getDrsBossBar() {
        return this.drsBossBar;
    }

    public void setDrsBossBar(BossBar bar) {
        this.drsBossBar = bar;
    }

    public boolean isDrsActive() {
        return this.hasdrs;
    }

    public void setDrsActive(boolean permission) {
        this.hasdrs = permission;
    }

    public UUID getPlayerId() {
        return this.uuid;
    }

    public int getHeatId() {
        return this.heatId;
    }

    public int getPosition() {
        return this.position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getStartPosition() {
        return this.startPosition;
    }

    public void setStartPosition(int startPosition) {
        this.startPosition = startPosition;
    }

    public Long getStartTime() {
        return this.startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return this.endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }

    public int getPitstops() {
        return this.pitstops;
    }

    public void setPitstops(int pitstops) {
        this.pitstops = pitstops;
    }

    public String getName() {
        Player p = Bukkit.getPlayer(this.uuid);
        return p != null ? p.getName() : "Unknown";
    }

    public int getCheckpointIndex() {
        return this.checkpointsReached;
    }

    public void setLastCheckpointTime(long time) {
        this.lastCheckpointTime = time;
        this.sessionCheckpointTimes.put(this.checkpointsReached, time);
    }

    public long getLastCheckpointTime() {
        return this.lastCheckpointTime;
    }

    public long getCheckpointTimeAt(int index) {
        return (Long)this.sessionCheckpointTimes.getOrDefault(index, 0L);
    }

    public void incrementPitstops() {
        ++this.pitstops;
    }

    public Long getQualifyingTime() {
        return this.qualifyingTime;
    }

    public void setQualifyingTime(Long qualifyingTime) {
        this.qualifyingTime = qualifyingTime;
    }

    public List<Lap> getLaps() {
        return this.laps;
    }

    public void addLap(Lap lap) {
        this.laps.add(lap);
    }

    public Lap getCurrentLap() {
        return this.currentLap;
    }

    public void setCurrentLap(Lap currentLap) {
        this.currentLap = currentLap;
    }

    public int getCheckpointsReached() {
        return this.checkpointsReached;
    }

    public void setCheckpointsReached(int checkpointsReached) {
        this.checkpointsReached = checkpointsReached;
    }

    public void incrementCheckpoint() {
        ++this.checkpointsReached;
        if (this.currentLap == null) {
            this.newLap();
        }
        long timestamp = System.currentTimeMillis();
        this.currentLap.recordCheckpointTime(this.checkpointsReached, timestamp);
    }

    public DriverState getState() {
        return this.state;
    }

    public void setState(DriverState state) {
        this.state = state;
    }

    public boolean isFinished() {
        return this.state == DriverState.FINISHED;
    }

    public void setFinished(boolean finished) {
        if (finished) {
            this.state = DriverState.FINISHED;
        } else if (this.state == DriverState.FINISHED) {
            this.state = DriverState.RUNNING;
        }

    }

    public boolean isDnf() {
        return this.state == DriverState.DNF;
    }

    public void setDnf(boolean dnf) {
        if (dnf) {
            this.state = DriverState.DNF;
        } else if (this.state == DriverState.DNF) {
            this.state = DriverState.RUNNING;
        }

    }

    public boolean isRacing() {
        return this.state.isRacing();
    }

    public String getCachedDelta() {
        return this.cachedDelta;
    }

    public void setCachedDelta(String cachedDelta) {
        this.cachedDelta = cachedDelta;
    }

    public int getLastProcessedCheckpointId() {
        return this.lastProcessedCheckpointId;
    }

    public void setLastProcessedCheckpointId(int lastProcessedCheckpointId) {
        this.lastProcessedCheckpointId = lastProcessedCheckpointId;
    }

    public Map<Integer, Long> getBestLapCheckpointTimes() {
        return this.bestLapCheckpointTimes;
    }

    public void setBestLapCheckpointTimes(Map<Integer, Long> bestLapCheckpointTimes) {
        this.bestLapCheckpointTimes = bestLapCheckpointTimes;
    }
    public int getLapCount() {
        return this.laps.size();
    }

    public Long getAbsoluteTimeAtProgress(int lapIndex, int cpId) {
        if (cpId <= 0) {
            if (lapIndex < this.laps.size()) {
                return lapIndex > 0 ? ((Lap)this.laps.get(lapIndex - 1)).getLapEnd() : ((Lap)this.laps.get(lapIndex)).getLapStart();
            } else if (lapIndex == this.laps.size() && this.currentLap != null) {
                return lapIndex > 0 ? ((Lap)this.laps.get(lapIndex - 1)).getLapEnd() : this.currentLap.getLapStart();
            } else {
                return null;
            }
        } else if (lapIndex < this.laps.size()) {
            return ((Lap)this.laps.get(lapIndex)).getCheckpointTime(cpId);
        } else {
            return lapIndex == this.laps.size() && this.currentLap != null ? this.currentLap.getCheckpointTime(cpId) : null;
        }
    }

    /**
     * Get elapsed time (from heat start) at a specific progress point (lap, checkpoint).
     * Used for live gap calculation where the "slower" driver's time can be substituted
     * with System.currentTimeMillis() - heatStartTime to create a "live" gap.
     *
     * @param lapIndex the lap index (0-based for completed laps, laps.size() for current lap)
     * @param cpId checkpoint ID (0 = start of lap)
     * @return elapsed time in ms, or null if not available
     */
    public Long getElapsedAtProgress(int lapIndex, int cpId) {
        Long absoluteTime = getAbsoluteTimeAtProgress(lapIndex, cpId);
        if (absoluteTime == null || this.startTime == null) {
            return null;
        }
        return absoluteTime - this.startTime;
    }

    /**
     * Get the driver's current progress as (lap, checkpoint) tuple.
     * @return int array where [0] = lap index, [1] = checkpoint ID
     */
    public int[] getCurrentProgress() {
        int lapIndex = this.laps.size();
        int checkpoint = 0;
        if (this.currentLap != null) {
            checkpoint = this.checkpointsReached;
        }
        return new int[]{lapIndex, checkpoint};
    }

    public long getTimeAtLastCheckpoint() {
        if (this.startTime == null) {
            return 0L;
        } else {
            long time = 0L;

            for(Lap lap : this.laps) {
                time += lap.getLapTime();
            }

            if (this.currentLap != null && this.checkpointsReached > 0) {
                Long cpTime = this.currentLap.getCheckpointTime(this.checkpointsReached);
                if (cpTime != null) {
                    time += cpTime - this.currentLap.getLapStart();
                }
            }

            return time;
        }
    }

    public long getTotalTime() {
        if (this.startTime == null) {
            return 0L;
        } else {
            return this.endTime != null ? this.endTime - this.startTime : System.currentTimeMillis() - this.startTime;
        }
    }

    public long getCurrentRaceTime() {
        return this.startTime == null ? 0L : System.currentTimeMillis() - this.startTime;
    }

    public Lap getFastestLap() {
        return (Lap)this.laps.stream().filter((lap) -> lap.getLapEnd() > 0L).min((l1, l2) -> Long.compare(l1.getLapTime(), l2.getLapTime())).orElse(null);
    }

    public boolean passPit() {
        if (this.currentLap != null && !this.currentLap.hasPitted()) {
            ++this.pitstops;
            this.currentLap.setPitted(true);
            return true;
        } else {
            return false;
        }
    }

    public boolean isInPit(Location playerLoc, PitStopManager pitStopManager, String trackNameWS) {
        return playerLoc != null && pitStopManager != null && trackNameWS != null ? pitStopManager.isValidPitStopLocation(playerLoc, trackNameWS) : false;
    }

    public boolean isInPit(PitStopManager pitStopManager) {
        return pitStopManager != null && pitStopManager.isPlayerInPitRegion(this.uuid);
    }

    public boolean hasCompletedMandatoryPits(int totalPits) {
        return this.pitstops >= totalPits;
    }

    public void passLap() {
        this.finishLap();
        this.newLap();
    }

    public void finishLap() {
        if (this.currentLap != null) {
            long currentTime = System.currentTimeMillis();
            this.currentLap.finishLap(currentTime);
            this.laps.add(this.currentLap);
        }

    }

    public void newLap() {
        long currentTime = System.currentTimeMillis();
        this.currentLap = new Lap(currentTime);
        this.checkpointsReached = 0;
        this.resetLagFlags();
        this.lastProcessedCheckpointId = -1;
    }

    public void forceCompleteCheckpoints(int totalCheckpoints) {
        if (this.checkpointsReached < totalCheckpoints) {
            this.checkpointsReached = totalCheckpoints;
            if (this.currentLap != null) {
                long timestamp = System.currentTimeMillis();

                for(int i = 1; i <= totalCheckpoints; ++i) {
                    if (!this.currentLap.getCheckpointTimes().containsKey(i)) {
                        this.currentLap.recordCheckpointTime(i, timestamp);
                    }
                }
            }
        }

    }

    public boolean hasPassedAllCheckpoints(int totalCheckpoints) {
        return totalCheckpoints <= 0 || this.checkpointsReached >= totalCheckpoints;
    }

    public void reset() {
        this.position = this.startPosition;
        this.startTime = null;
        this.endTime = null;
        this.pitstops = 0;
        this.qualifyingTime = null;
        this.laps.clear();
        this.currentLap = null;
        this.checkpointsReached = 0;
        this.cachedDelta = "";
        this.lastProcessedCheckpointId = -1;
        this.bestLapCheckpointTimes.clear();
        this.ptpEnergy = (double)0.0F;
        this.ptpActive = false;
        this.state = DriverState.SETUP;
        this.resetCount = 0;
        this.lagStartPassed = false;
        this.lagEndPassed = false;
    }

    public int getResetCount() {
        return this.resetCount;
    }

    public void incrementResetCount() {
        ++this.resetCount;
    }

    public void setResetCount(int count) {
        this.resetCount = count;
    }

    public boolean hasPassedLagStart() {
        return this.lagStartPassed;
    }

    public void setLagStartPassed(boolean passed) {
        this.lagStartPassed = passed;
    }

    public boolean hasPassedLagEnd() {
        return this.lagEndPassed;
    }

    public void setLagEndPassed(boolean passed) {
        this.lagEndPassed = passed;
    }

    public void resetLagFlags() {
        this.lagStartPassed = false;
        this.lagEndPassed = false;
    }

    public TireCompound getTireCompound() {
        return tireCompound;
    }

    public void setTireCompound(TireCompound tireCompound) {
        this.tireCompound = tireCompound == null ? TireCompound.MEDIUM : tireCompound;
    }

    public int getTireWear() {
        return tireWear;
    }

    public void setTireWear(int tireWear) {
        this.tireWear = Math.max(0, Math.min(100, tireWear));
    }

    public String getCustomName() {
        return this.customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public boolean isAiControlled() {
        return this.aiControlled;
    }

    public void setAiControlled(boolean aiControlled) {
        this.aiControlled = aiControlled;
    }

    public double getFuelLevel() {
        return this.fuelLevel;
    }

    public void setFuelLevel(double fuelLevel) {
        this.fuelLevel = Math.max(0.0D, Math.min(this.fuelCapacity, fuelLevel));
    }

    public double getFuelCapacity() {
        return this.fuelCapacity;
    }

    public void setFuelCapacity(double fuelCapacity) {
        this.fuelCapacity = Math.max(1.0D, fuelCapacity);
        this.fuelLevel = Math.max(0.0D, Math.min(this.fuelCapacity, this.fuelLevel));
    }

    public void refuelToFull() {
        this.fuelLevel = this.fuelCapacity;
    }

    public void addFuel(double amount) {
        this.setFuelLevel(this.fuelLevel + amount);
    }

    public void consumeFuel(double amount) {
        this.setFuelLevel(this.fuelLevel - Math.max(0.0D, amount));
    }

    public String toString() {
        String var10000 = String.valueOf(this.uuid);
        return "Driver{uuid=" + var10000 + ", heatId=" + this.heatId + ", position=" + this.position + ", startPosition=" + this.startPosition + ", laps=" + this.laps.size() + ", laps=" + this.laps.size() + ", state=" + String.valueOf(this.state) + "}";
    }

    public void finishLap(Location from, Location to, RegionBox region) {
        if (this.currentLap != null) {
            long now = System.currentTimeMillis();
            long preciseEndTime = now;
            if (from != null && to != null && region != null) {
                double proportion = calculateRegionEntryProportion(from, to, region);
                long tickDurationMs = 50L;
                long adjustmentMs = (long)(((double)1.0F - proportion) * (double)tickDurationMs);
                preciseEndTime = now - adjustmentMs;
            }

            this.currentLap.finishLap(preciseEndTime);
            this.laps.add(this.currentLap);
            DriverFinishLapEvent event = new DriverFinishLapEvent(this, this.currentLap, false);
            Bukkit.getPluginManager().callEvent(event);
        }
    }

    private static double calculateRegionEntryProportion(Location from, Location to, RegionBox region) {
        double low = (double)0.0F;
        double high = (double)1.0F;

        for(int i = 0; i < 15; ++i) {
            double mid = (low + high) / (double)2.0F;
            Location midLocation = interpolateLocation(from, to, mid);
            if (region.contains(midLocation)) {
                high = mid;
            } else {
                low = mid;
            }
        }

        return (low + high) / (double)2.0F;
    }

    private static Location interpolateLocation(Location from, Location to, double proportion) {
        double x = from.getX() + (to.getX() - from.getX()) * proportion;
        double y = from.getY() + (to.getY() - from.getY()) * proportion;
        double z = from.getZ() + (to.getZ() - from.getZ()) * proportion;
        return new Location(from.getWorld(), x, y, z);
    }
}
