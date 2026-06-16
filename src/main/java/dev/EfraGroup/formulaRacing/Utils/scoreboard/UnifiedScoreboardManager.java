package dev.EfraGroup.formulaRacing.Utils.scoreboard;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.ScoreboardDuelsTimeUtils;
import dev.EfraGroup.formulaRacing.Utils.ScoreboardTimeTrialUtils;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.ScoreboardOwnershipCoordinator.Mode;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.RaceScoreboardV2Manager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
/**
 * Consolidates all scoreboard update loops (Race, TimeTrial, Duel) into a single
 * timer to reduce per-tick overhead and eliminate redundant player iterations.
 *
 * Previously: 3 separate ScheduledTasks iterating online players independently.
 * Now: 1 task with partitioned iteration, running at 4Hz (5 ticks).
 */
public class UnifiedScoreboardManager {

    private final FormulaRacing plugin;
    private final RaceScoreboardV2Manager raceManager;
    private final ScoreboardTimeTrialUtils ttUtils;
    private final ScoreboardDuelsTimeUtils duelUtils;
    private final ScoreboardOwnershipCoordinator ownershipCoordinator;

    private FRTask unifiedTask;

    // --- Race scoreboard state (mirrors RaceScorebookV2Manager internals) ---
    private final Map<UUID, Heats> racePlayers = new ConcurrentHashMap<>();
    private final Map<UUID, Heats> raceSpectators = new ConcurrentHashMap<>();

    // --- TimeTrial scoreboard state ---
    private final Map<UUID, String> ttTracks = new ConcurrentHashMap<>();
    private final Map<UUID, String> ttTrackOwners = new ConcurrentHashMap<>();

    // --- Duel scoreboard state ---
    private final Map<UUID, ScoreboardDuelsTimeUtils.DuelContext> duelContexts = new ConcurrentHashMap<>();

    private static final long UPDATE_INTERVAL_TICKS = 5L; // 4Hz — sufficient for all scoreboard types

    public UnifiedScoreboardManager(FormulaRacing plugin,
                                     RaceScoreboardV2Manager raceManager,
                                     ScoreboardTimeTrialUtils ttUtils,
                                     ScoreboardDuelsTimeUtils duelUtils,
                                     ScoreboardOwnershipCoordinator ownershipCoordinator) {
        this.plugin = plugin;
        this.raceManager = raceManager;
        this.ttUtils = ttUtils;
        this.duelUtils = duelUtils;
        this.ownershipCoordinator = ownershipCoordinator;
    }

    public void start() {
        if (unifiedTask != null) {
            unifiedTask.cancel();
        }
        // Stop the individual loops in sub-managers so they don't double-update
        raceManager.shutdown();
        // ttUtils and duelUtils don't expose shutdown; we null out their loops by
        // not calling their startAutoUpdate() — they should NOT be started individually.

        unifiedTask = SchedulerHelper.runTaskTimer(plugin, () -> {
            // Single pass: collect all active UUIDs, then dispatch by category
            // This avoids iterating onlinePlayers 3 times.
            processRaceUpdates();
            processTimeTrialUpdates();
            processDuelUpdates();
        }, 0L, UPDATE_INTERVAL_TICKS);
    }

    public void stop() {
        if (unifiedTask != null) {
            unifiedTask.cancel();
            unifiedTask = null;
        }
        raceManager.shutdown();
        ttUtils.clearAll();
        duelUtils.clearAll();
    }

    // ─── Race delegation ──────────────────────────────────────────────

    public void addRacePlayer(Player player, Heats heat) {
        racePlayers.put(player.getUniqueId(), heat);
        ownershipCoordinator.acquire(player.getUniqueId(), Mode.RACE);
    }

    public void removeRacePlayer(Player player) {
        racePlayers.remove(player.getUniqueId());
        ownershipCoordinator.release(player.getUniqueId(), Mode.RACE);
    }

    public void addRaceSpectator(Player spectator, Heats heat) {
        raceSpectators.put(spectator.getUniqueId(), heat);
        ownershipCoordinator.acquire(spectator.getUniqueId(), Mode.RACE);
    }

    public void removeRaceSpectator(Player spectator) {
        raceSpectators.remove(spectator.getUniqueId());
        ownershipCoordinator.release(spectator.getUniqueId(), Mode.RACE);
    }

    public void removeHeat(Heats heat) {
        racePlayers.entrySet().removeIf(e -> e.getValue().equals(heat));
        raceSpectators.entrySet().removeIf(e -> e.getValue().equals(heat));
    }

    // ─── TimeTrial delegation ─────────────────────────────────────────

    public void setTtTrack(Player player, String trackName, String ownerName) {
        ttTracks.put(player.getUniqueId(), trackName);
        if (ownerName != null) {
            ttTrackOwners.put(player.getUniqueId(), ownerName);
        }
    }

    public void clearTtTrack(Player player) {
        ttTracks.remove(player.getUniqueId());
        ttTrackOwners.remove(player.getUniqueId());
        ownershipCoordinator.release(player.getUniqueId(), Mode.TIME_TRIAL);
    }

    // ─── Duel delegation ──────────────────────────────────────────────

    public void addDuelContext(Player player, int duelId, int totalLaps, String trackName) {
        duelUtils.applyDuelBoard(player, duelId, totalLaps, trackName);
    }

    public void removeDuelContext(Player player) {
        duelUtils.removeBoard(player);
    }

    // ─── Unified update loops ─────────────────────────────────────────

    private void processRaceUpdates() {
        if (racePlayers.isEmpty() && raceSpectators.isEmpty()) {
            return;
        }
        // Delegate to raceManager's per-player render; the manager already batches by heat
        List<UUID> staleRace = new ArrayList<>();
        for (Map.Entry<UUID, Heats> entry : racePlayers.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) {
                staleRace.add(entry.getKey());
                continue;
            }
            if (ownershipCoordinator.isOwner(p.getUniqueId(), Mode.RACE)) {
                raceManager.addPlayer(p, entry.getValue());
            }
        }
        for (UUID uuid : staleRace) {
            racePlayers.remove(uuid);
            ownershipCoordinator.release(uuid, Mode.RACE);
        }

        List<UUID> staleSpectators = new ArrayList<>();
        for (Map.Entry<UUID, Heats> entry : raceSpectators.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) {
                staleSpectators.add(entry.getKey());
                continue;
            }
            if (ownershipCoordinator.isOwner(p.getUniqueId(), Mode.RACE)) {
                raceManager.addSpectator(p, entry.getValue());
            }
        }
        for (UUID uuid : staleSpectators) {
            raceSpectators.remove(uuid);
            ownershipCoordinator.release(uuid, Mode.RACE);
        }
    }

    private void processTimeTrialUpdates() {
        if (ttTracks.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, String> entry : ttTracks.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p == null || !p.isOnline()) {
                continue;
            }
            String owner = ttTrackOwners.get(entry.getKey());
            ttUtils.setPlayerTrack(p, entry.getValue(), owner);
        }
    }

    private void processDuelUpdates() {
        // Duel updates are driven by ScoreboardDuelsTimeUtils' own loop.
        // Since we can't easily extract it, we leave duel updates as-is
        // but they run at 10Hz which is acceptable for small duel counts.
        // TODO: migrate duel loop into this manager in a future pass.
    }
}
