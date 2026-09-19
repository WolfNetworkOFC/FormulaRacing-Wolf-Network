package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Gimmicks: admins save a build (from the WorldEdit clipboard) bound to a track,
 * and heats schedule it for a lap.
 *
 * <p>How it runs:</p>
 * <ol>
 *   <li>everything lives on disk ({@code gimmicks/<pista>/<nome>.gimmick});</li>
 *   <li>one lap before the scheduled lap the build is loaded into memory and the
 *       current state of the area is captured into a {@code .backup} file, so the
 *       paste itself is instant and the undo survives a restart;</li>
 *   <li>on the scheduled lap the blocks are written in tick-batched chunks on the
 *       region thread, without WorldEdit;</li>
 *   <li>when the heat ends (or is removed) every paste is undone from its backup,
 *       and leftover backups from a crash are restored on the next start.</li>
 * </ol>
 */
public class GimmickManager {

    /** Lap 1 is the start: it has no "new lap" event, so the first schedulable lap is 2. */
    public static final int MIN_TRIGGER_LAP = 2;

    /** Manual pastes (/gimmick paste, GUI) are tracked under this pseudo heat id. */
    public static final int MANUAL_HEAT_ID = -1;

    /** Grace period before looking for leftover backups, so the worlds are loaded. */
    private static final long RECOVERY_DELAY_TICKS = 100L;

    private final FormulaRacing plugin;
    private final File rootFolder;

    private final Map<Integer, GimmickConfig> gimmicksById = new ConcurrentHashMap<>();
    private volatile boolean definitionsLoaded = false;

    private final Map<Integer, List<GimmickSchedule>> scheduleCache = new ConcurrentHashMap<>();
    /**
     * Completed laps of the leader when the current race went green. Practice and
     * qualifying laps are recorded in the driver, so without this baseline the race
     * lap counter would start shifted.
     */
    private final Map<Integer, Integer> lapBaseline = new ConcurrentHashMap<>();
    /** heatId -> gimmickId -> build already loaded one lap ahead of its trigger. */
    private final Map<Integer, Map<Integer, PreparedGimmick>> preparedPerHeat = new ConcurrentHashMap<>();
    /** heatId -> pastes still applied, newest first (restore walks the stack). */
    private final Map<Integer, Deque<PastedGimmick>> pastedPerHeat = new ConcurrentHashMap<>();
    private volatile boolean recoveryScheduled = false;

    public GimmickManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.rootFolder = new File(plugin.getDataFolder(), "gimmicks");
    }

    public File getRootFolder() {
        return rootFolder;
    }

    /* ========================================================
     *  DEFINITIONS
     * ======================================================== */

    private DatabaseManager db() {
        return plugin.getDatabaseManager();
    }

    private void ensureDefinitionsLoaded() {
        if (definitionsLoaded) return;
        gimmicksById.clear();
        for (GimmickConfig gimmick : db().getAllGimmicks()) {
            gimmicksById.put(gimmick.getId(), gimmick);
        }
        definitionsLoaded = true;
    }

    /** Reloads the definitions from the database. */
    public void reload() {
        definitionsLoaded = false;
        ensureDefinitionsLoaded();
        scheduleCache.clear();
    }

    public List<GimmickConfig> getGimmicksForTrack(String trackName) {
        ensureDefinitionsLoaded();
        String trackWS = GimmickConfig.normalizeTrack(trackName);
        List<GimmickConfig> result = new ArrayList<>();
        if (trackWS == null) return result;

        for (GimmickConfig gimmick : gimmicksById.values()) {
            if (trackWS.equals(GimmickConfig.normalizeTrack(gimmick.getTrackNameWS()))) {
                result.add(gimmick);
            }
        }
        result.sort(Comparator.comparing(GimmickConfig::getName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public GimmickConfig findGimmick(String trackName, String name) {
        if (name == null) return null;
        String wanted = GimmickConfig.normalize(name);
        for (GimmickConfig gimmick : getGimmicksForTrack(trackName)) {
            if (wanted.equals(GimmickConfig.normalize(gimmick.getName()))) return gimmick;
        }
        return null;
    }

    public GimmickConfig findGimmickById(int id) {
        ensureDefinitionsLoaded();
        return gimmicksById.get(id);
    }

    /** Every gimmick name, used by the command tab completion. */
    public List<String> getAllGimmickNames() {
        ensureDefinitionsLoaded();
        List<String> names = new ArrayList<>();
        for (GimmickConfig gimmick : gimmicksById.values()) {
            names.add(gimmick.getName());
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** File a gimmick's build is stored in. */
    public File fileOf(GimmickConfig gimmick) {
        return GimmickFile.gimmickFile(rootFolder, gimmick.getTrackNameWS(), gimmick.getName());
    }

    /**
     * Saves the clipboard the player has right now as the build of a gimmick, anchored
     * on the player's current position (the same position {@code //paste} would use).
     *
     * @param pasteWithAir {@code -a} on the command: paste the build air too
     */
    public GimmickConfig saveFromClipboard(Player player, String name, String trackName, boolean pasteWithAir) {
        if (name == null || name.isBlank()) {
            throw new GimmickException("Informe o nome da gimmick.");
        }
        String trackWS = GimmickConfig.normalizeTrack(trackName);
        if (trackWS == null || trackWS.isBlank()) {
            throw new GimmickException("Informe a pista da gimmick.");
        }
        if (!db().isTrackExists(trackWS)) {
            throw new GimmickException("Pista não encontrada: " + trackName);
        }

        // Reads the clipboard and writes the file first: a failure here must not touch the database.
        List<GimmickFile.Entry> entries = GimmickSchematics.readClipboard(player);
        if (entries.isEmpty()) {
            throw new GimmickException("O clipboard está vazio.");
        }
        try {
            GimmickFile.writeGimmick(GimmickFile.gimmickFile(rootFolder, trackWS, name), entries);
        } catch (Exception e) {
            throw new GimmickException("Erro ao salvar o arquivo da gimmick: " + e.getMessage(), e);
        }

        Location anchor = player.getLocation();

        GimmickConfig existing = findGimmick(trackWS, name);
        if (existing != null) {
            existing.setPasteLocation(anchor);
            existing.setPasteWithAir(pasteWithAir);
            db().updateGimmick(existing);
            dropPrepared(existing.getId());
            return existing;
        }

        GimmickConfig gimmick = new GimmickConfig(name.trim(), trackWS, anchor);
        gimmick.setPasteWithAir(pasteWithAir);
        gimmick.setCreatedBy(player.getName());
        gimmick.setCreatedAt(System.currentTimeMillis());
        gimmick.setEnabled(true);

        int id = db().insertGimmick(gimmick);
        if (id <= 0) {
            throw new GimmickException("Não foi possível salvar a gimmick no banco de dados.");
        }
        gimmick.setId(id);
        gimmicksById.put(id, gimmick);
        return gimmick;
    }

    /** How many blocks the saved build has, or -1 when the file is gone. */
    public int countBlocks(GimmickConfig gimmick) {
        try {
            return GimmickFile.readGimmick(fileOf(gimmick)).size();
        } catch (Exception e) {
            return -1;
        }
    }

    /** Whether the build of a gimmick is currently loaded in memory (one lap ahead). */
    public boolean isLoaded(GimmickConfig gimmick) {
        for (Map<Integer, PreparedGimmick> prepared : preparedPerHeat.values()) {
            PreparedGimmick entry = prepared.get(gimmick.getId());
            if (entry != null && entry.isReady()) return true;
        }
        return false;
    }

    public void setAnnounceMessage(GimmickConfig gimmick, String message) {
        gimmick.setAnnounceMessage(message);
        db().updateGimmick(gimmick);
    }

    public boolean toggleGimmick(GimmickConfig gimmick) {
        gimmick.setEnabled(!gimmick.isEnabled());
        db().updateGimmick(gimmick);
        return gimmick.isEnabled();
    }

    /** Removes the definition, its files and every heat schedule pointing at it. */
    public void deleteGimmick(GimmickConfig gimmick) {
        db().deleteGimmick(gimmick.getId());
        gimmicksById.remove(gimmick.getId());

        File file = fileOf(gimmick);
        if (file.exists() && !file.delete()) {
            logWarn("Não foi possível apagar o arquivo " + file.getName());
        }

        // The rows are gone from the database, so cached schedules must be rebuilt.
        scheduleCache.clear();
        dropPrepared(gimmick.getId());
    }

    /* ========================================================
     *  HEAT SCHEDULE
     * ======================================================== */

    /** Completed laps of the leader right now, practice and qualifying included. */
    private int completedLaps(Heats heat) {
        int completed = 0;
        for (Driver driver : heat.getDrivers().values()) {
            completed = Math.max(completed, driver.getLapCount());
        }
        return completed;
    }

    /**
     * Lap the heat is on right now: 1 on the grid and 2 onwards after the first
     * completed lap. The counting starts when the heat goes green, so laps done in
     * practice or qualifying never shift the lap a gimmick is scheduled for.
     */
    public int currentHeatLap(Heats heat) {
        if (heat == null) return 1;

        Integer baseline = lapBaseline.get(heat.getId());
        if (baseline == null) {
            // Race resumed with no state transition (heat loaded as RACING): start
            // counting from here instead of never firing anything.
            if (heat.getHeatState() != HeatState.RACING) return 1;
            int laps = completedLaps(heat);
            baseline = lapBaseline.putIfAbsent(heat.getId(), laps);
            if (baseline == null) baseline = laps;
            return 1;
        }

        return Math.max(1, completedLaps(heat) - baseline + 1);
    }

    public List<GimmickSchedule> getSchedule(Heats heat) {
        if (heat == null || heat.getId() <= 0) return Collections.emptyList();
        ensureDefinitionsLoaded();
        return scheduleCache.computeIfAbsent(
            heat.getId(),
            heatId -> Collections.synchronizedList(loadSchedule(heatId))
        );
    }

    private List<GimmickSchedule> loadSchedule(int heatId) {
        List<GimmickSchedule> schedules = new ArrayList<>();
        for (DatabaseManager.HeatGimmickRow row : db().getHeatGimmicks(heatId)) {
            GimmickConfig gimmick = gimmicksById.get(row.getGimmickId());
            if (gimmick == null) continue; // definition was deleted
            schedules.add(new GimmickSchedule(row.getHeatId(), gimmick, row.getTriggerLap()));
        }
        return schedules;
    }

    /** Heat the player has selected: /heat select, or the current heat of the selected event. */
    public Heats resolveSelectedHeat(Player player) {
        if (player == null) return null;
        var selectedHeat = db().getPlayerSelectedHeat(player.getUniqueId());
        if (selectedHeat.isPresent()) {
            var heat = plugin.getRaceEventManager().getHeat(selectedHeat.get());
            if (heat.isPresent()) return heat.get();
        }

        var event = db().getPlayerSelectedEvent(player.getUniqueId()).orElse(null);
        if (event != null) {
            var round = event.getSchedule().getCurrentRound().orElse(null);
            if (round != null) return round.getCurrentHeat().orElse(null);
        }
        return null;
    }

    /** Finds the schedule of a gimmick in a heat, or null. */
    public GimmickSchedule findSchedule(Heats heat, GimmickConfig gimmick) {
        if (heat == null || gimmick == null) return null;
        for (GimmickSchedule schedule : getSchedule(heat)) {
            if (schedule.getGimmick().getId() == gimmick.getId()) return schedule;
        }
        return null;
    }

    /**
     * Schedules a gimmick for a lap that has not happened yet.
     *
     * @throws GimmickException with the reason when the schedule is not allowed
     */
    public GimmickSchedule scheduleGimmick(Heats heat, GimmickConfig gimmick, int lap) {
        if (heat == null) throw new GimmickException("Nenhum heat selecionado ou ativo.");
        if (heat.getId() <= 0) throw new GimmickException("Esse heat ainda não foi salvo, não dá para agendar gimmicks nele.");
        if (gimmick == null) throw new GimmickException("Gimmick não encontrada.");
        if (heat.getTrackNameWS() == null) throw new GimmickException("O heat não tem pista definida.");
        if (heat.getHeatState() == HeatState.FINISHED) throw new GimmickException("O heat já terminou.");

        String heatTrack = GimmickConfig.normalizeTrack(heat.getTrackNameWS());
        if (!heatTrack.equals(GimmickConfig.normalizeTrack(gimmick.getTrackNameWS()))) {
            throw new GimmickException(
                "A gimmick '" + gimmick.getName() + "' é da pista '" + gimmick.getTrackNameWS() +
                "', mas o heat é em '" + heat.getTrackNameWS() + "'."
            );
        }
        if (lap < MIN_TRIGGER_LAP) {
            throw new GimmickException("A volta mínima é " + MIN_TRIGGER_LAP + " (a largada é a volta 1).");
        }

        int currentLap = currentHeatLap(heat);
        if (lap <= currentLap) {
            throw new GimmickException("A volta " + lap + " já passou: o heat está na volta " + currentLap + ".");
        }

        Integer totalLaps = heat.getTotalLaps();
        if (totalLaps != null && totalLaps > 0 && lap > totalLaps) {
            throw new GimmickException("O heat só tem " + totalLaps + " voltas.");
        }

        GimmickSchedule already = findSchedule(heat, gimmick);
        if (already != null) {
            throw new GimmickException(
                "A gimmick '" + gimmick.getName() + "' já está agendada para a volta " +
                already.getTriggerLap() + "."
            );
        }
        if (fileOf(gimmick) == null || !fileOf(gimmick).exists()) {
            throw new GimmickException("O arquivo da gimmick '" + gimmick.getName() + "' não existe mais.");
        }

        db().addHeatGimmick(heat.getId(), gimmick.getId(), lap);

        // Read back instead of trusting the update count: the upsert returns different
        // values depending on the driver, and a silent failure would only show up after
        // a restart.
        boolean persisted = db()
            .getHeatGimmicks(heat.getId())
            .stream()
            .anyMatch(row -> row.getGimmickId() == gimmick.getId());
        if (!persisted) {
            throw new GimmickException("Não foi possível salvar o agendamento no banco de dados.");
        }

        GimmickSchedule schedule = new GimmickSchedule(heat.getId(), gimmick, lap);
        getSchedule(heat).add(schedule);

        // A gimmick scheduled for the next lap must be in memory right away.
        if (lap == currentLap + 1 || lap == MIN_TRIGGER_LAP) {
            prepare(heat.getId(), gimmick, lap, false);
        }
        return schedule;
    }

    public boolean unscheduleGimmick(Heats heat, GimmickConfig gimmick) {
        if (heat == null || gimmick == null) return false;
        boolean removed = getSchedule(heat).removeIf(
            schedule -> schedule.getGimmick().getId() == gimmick.getId()
        );
        if (removed) {
            db().removeHeatGimmick(heat.getId(), gimmick.getId());
            dropPrepared(gimmick.getId());
        }
        return removed;
    }

    /** Keeps the heat rows in the database (they are the heat config). */
    public void clearSchedule(Heats heat) {
        if (heat == null) return;
        db().removeAllHeatGimmicks(heat.getId());
        getSchedule(heat).clear();
        Map<Integer, PreparedGimmick> prepared = preparedPerHeat.remove(heat.getId());
        if (prepared != null) prepared.clear();
    }

    /** Drops everything about a heat that no longer exists, undoing its pastes first. */
    public void cleanupHeat(int heatId) {
        restoreHeat(heatId);
        scheduleCache.remove(heatId);
        lapBaseline.remove(heatId);
        if (heatId > 0) {
            db().removeAllHeatGimmicks(heatId);
        }
    }

    /* ========================================================
     *  RUNTIME (preload / paste / restore)
     * ======================================================== */

    /** Called when a heat goes back to RACING so a restarted race fires again. */
    public void onHeatRacing(Heats heat) {
        if (heat == null) return;
        // Laps done so far (practice/qualifying) become the baseline of this race.
        lapBaseline.put(heat.getId(), completedLaps(heat));
        for (GimmickSchedule schedule : new ArrayList<>(getSchedule(heat))) {
            schedule.setTriggered(false);
        }
        // Lap 2 has no "previous lap" event, so load it here, at the green light.
        prepareForLap(heat, MIN_TRIGGER_LAP);
    }

    /** Called when a heat finishes: everything pasted by it goes back to the previous state. */
    public void onHeatFinished(Heats heat) {
        if (heat == null) return;
        restoreHeat(heat.getId());
        lapBaseline.remove(heat.getId());
        for (GimmickSchedule schedule : new ArrayList<>(getSchedule(heat))) {
            schedule.setTriggered(false);
        }
    }

    /**
     * Loads the gimmicks of the given lap into memory and fires the ones that are due.
     * Safe to call on every lap event: each schedule fires a single time, no matter how
     * many drivers cross the line.
     */
    public void triggerGimmicks(Heats heat, int heatLap) {
        if (heat == null) return;
        prepareForLap(heat, heatLap + 1);
        fireForLap(heat, heatLap);
    }

    /** Brings every gimmick scheduled for {@code lap} into memory, one lap ahead of time. */
    public void prepareForLap(Heats heat, int lap) {
        if (heat == null || lap < MIN_TRIGGER_LAP) return;
        for (GimmickSchedule schedule : new ArrayList<>(getSchedule(heat))) {
            if (schedule.isTriggered() || schedule.getTriggerLap() != lap) continue;
            GimmickConfig gimmick = schedule.getGimmick();
            if (gimmick == null || !gimmick.isEnabled()) continue;
            if (isPrepared(heat.getId(), gimmick.getId())) continue;
            prepare(heat.getId(), gimmick, lap, false);
        }
    }

    /** Applies the gimmicks whose lap already arrived. */
    private void fireForLap(Heats heat, int heatLap) {
        for (GimmickSchedule schedule : new ArrayList<>(getSchedule(heat))) {
            if (schedule.isTriggered()) continue;

            GimmickConfig gimmick = schedule.getGimmick();
            if (gimmick == null || !gimmick.isEnabled()) continue;
            if (schedule.getTriggerLap() > heatLap) continue;

            PreparedGimmick prepared = prepared(heat.getId()).get(gimmick.getId());
            if (prepared == null || !prepared.isReady()) {
                // Still loading (or never prepared): the next lap event retries.
                logWarn(
                    "[Gimmick] '" + gimmick.getName() + "' (volta " + schedule.getTriggerLap() +
                    ") ainda não está pronta; será colada na próxima volta."
                );
                continue;
            }

            schedule.setTriggered(true);
            apply(heat.getId(), gimmick, prepared, heat);
        }
    }

    /** Manual paste for testing; tracked so /gimmick restore can undo it. */
    public void pasteNow(GimmickConfig gimmick) {
        if (gimmick == null) throw new GimmickException("Gimmick não encontrada.");
        if (gimmick.getPasteLocation() == null) {
            throw new GimmickException("O mundo da gimmick '" + gimmick.getName() + "' não está carregado.");
        }
        prepare(MANUAL_HEAT_ID, gimmick, 1, true);
    }

    private boolean isPrepared(int heatId, int gimmickId) {
        return prepared(heatId).containsKey(gimmickId);
    }

    private Map<Integer, PreparedGimmick> prepared(int heatId) {
        return preparedPerHeat.computeIfAbsent(heatId, key -> new ConcurrentHashMap<>());
    }

    private void dropPrepared(int gimmickId) {
        for (Map<Integer, PreparedGimmick> prepared : preparedPerHeat.values()) {
            prepared.remove(gimmickId);
        }
    }

    /**
     * Loads the build from disk and captures the state it will replace, both off the
     * main thread. The backup file is written before anything is pasted, so a crash
     * between the two is still recoverable.
     *
     * @param applyOnReady true for a manual paste: apply as soon as the build is ready
     */
    private void prepare(int heatId, GimmickConfig gimmick, int triggerLap, boolean applyOnReady) {
        Location anchor = gimmick.getPasteLocation();
        if (anchor == null || anchor.getWorld() == null) {
            logWarn("[Gimmick] Mundo da gimmick '" + gimmick.getName() + "' não está carregado.");
            return;
        }

        File buildFile = fileOf(gimmick);
        File backupFile = GimmickFile.backupFile(
            rootFolder,
            gimmick.getTrackNameWS(),
            gimmick.getName(),
            heatId
        );
        PreparedGimmick preparedGimmick = new PreparedGimmick(gimmick, backupFile);
        prepared(heatId).put(gimmick.getId(), preparedGimmick); // marks it as in progress

        SchedulerHelper.runAsync(plugin, () -> {
            List<GimmickBlocks.Placement> placements;
            try {
                placements = GimmickBlocks.toPlacements(
                    GimmickFile.readGimmick(buildFile),
                    anchor,
                    gimmick.isIgnoreAirBlocks()
                );
            } catch (Exception e) {
                logWarn("[Gimmick] Falha ao carregar '" + gimmick.getName() + "': " + e.getMessage());
                prepared(heatId).remove(gimmick.getId());
                return;
            }

            if (placements.isEmpty()) {
                logWarn("[Gimmick] '" + gimmick.getName() + "' não tem blocos para colar.");
                prepared(heatId).remove(gimmick.getId());
                return;
            }

            GimmickBlocks.capture(plugin, anchor, placements, backup -> {
                SchedulerHelper.runAsync(plugin, () -> {
                    try {
                        GimmickFile.writeBackup(backupFile, anchor.getWorld().getName(), backup);
                    } catch (Exception e) {
                        logWarn("[Gimmick] Falha ao gravar o backup de '" + gimmick.getName() + "': " + e.getMessage());
                    }
                });

                preparedGimmick.markReady(placements, backup);
                logDebug(
                    "[Gimmick] '" + gimmick.getName() + "' carregada para a volta " + triggerLap +
                    " (" + placements.size() + " blocos, " + backup.size() + " para restaurar)"
                );

                if (applyOnReady) {
                    apply(heatId, gimmick, preparedGimmick, null);
                }
            });
        });
    }

    /** Writes a prepared gimmick into the world and tracks it so it can be undone. */
    private void apply(int heatId, GimmickConfig gimmick, PreparedGimmick preparedGimmick, Heats heat) {
        Location anchor = gimmick.getPasteLocation();
        if (anchor == null || anchor.getWorld() == null) {
            logWarn("[Gimmick] Mundo da gimmick '" + gimmick.getName() + "' não está carregado.");
            return;
        }

        prepared(heatId).remove(gimmick.getId());
        pasted(heatId).push(
            new PastedGimmick(
                gimmick.getName(),
                anchor.getWorld(),
                preparedGimmick.backupEntries(),
                preparedGimmick.backupFile()
            )
        );

        GimmickBlocks.apply(plugin, anchor, preparedGimmick.placements(), () -> {
            if (heat != null) {
                announce(heat, gimmick);
            }
            logDebug("[Gimmick] '" + gimmick.getName() + "' colada no heat " + heatId + ".");
        });
    }

    private Deque<PastedGimmick> pasted(int heatId) {
        return pastedPerHeat.computeIfAbsent(heatId, key -> new ArrayDeque<>());
    }

    /** Undoes every paste of a heat. */
    public void restoreHeat(int heatId) {
        Deque<PastedGimmick> pasted = pastedPerHeat.remove(heatId);
        Map<Integer, PreparedGimmick> prepared = preparedPerHeat.remove(heatId);
        if (prepared != null) prepared.clear();
        if (pasted == null || pasted.isEmpty()) return;

        // Deque iteration starts at the newest paste, which is the right order to undo.
        for (PastedGimmick record : pasted) {
            restoreRecord(record);
        }
        logDebug("[Gimmick] Heat " + heatId + ": " + pasted.size() + " gimmick(s) restaurada(s).");
    }

    private void restoreRecord(PastedGimmick record) {
        if (record.backupEntries().isEmpty()) {
            deleteQuietly(record.backupFile());
            return;
        }

        Location anchor = anchorOf(record.world(), record.backupEntries());
        if (anchor == null) {
            deleteQuietly(record.backupFile());
            return;
        }

        SchedulerHelper.runAsync(plugin, () -> {
            List<GimmickBlocks.Placement> placements = GimmickBlocks.backupToPlacements(record.backupEntries());
            GimmickBlocks.apply(plugin, anchor, placements, () -> deleteQuietly(record.backupFile()));
        });
    }

    /** Undoes every paste of every heat plus anything left over from a crash. */
    public int restoreEverything() {
        int restored = 0;
        for (Integer heatId : new ArrayList<>(pastedPerHeat.keySet())) {
            Deque<PastedGimmick> pasted = pastedPerHeat.get(heatId);
            restored += pasted == null ? 0 : pasted.size();
            restoreHeat(heatId);
        }
        restored += restoreOrphanBackups();
        return restored;
    }

    /**
     * Restores backups found on disk that no live heat is tracking. That is what makes a
     * paste survive a server crash: the backup is written before the paste, so the next
     * start can still put the world back.
     */
    public int restoreOrphanBackups() {
        int restored = 0;
        for (File file : GimmickFile.findBackups(rootFolder)) {
            try {
                GimmickFile.Backup backup = GimmickFile.readBackup(file);
                World world = backup.worldName() == null ? null : Bukkit.getWorld(backup.worldName());
                if (world == null || backup.blocks().isEmpty()) {
                    logWarn("[Gimmick] Backup " + file.getName() + " não pôde ser restaurado agora.");
                    continue;
                }

                Location anchor = anchorOf(world, backup.blocks());
                if (anchor == null) continue;

                List<GimmickBlocks.Placement> placements = GimmickBlocks.backupToPlacements(backup.blocks());
                GimmickBlocks.apply(plugin, anchor, placements, () -> deleteQuietly(file));
                restored++;
                logWarn("[Gimmick] Restaurando backup " + file.getName() + " (" + placements.size() + " blocos).");
            } catch (Exception e) {
                logWarn("[Gimmick] Falha ao restaurar " + file.getName() + ": " + e.getMessage());
            }
        }
        return restored;
    }

    /** Called on startup: undo anything a crash left pasted in the world. */
    public void schedulePendingRestore() {
        if (recoveryScheduled) return;
        recoveryScheduled = true;
        SchedulerHelper.runDelayedTask(plugin, () -> {
            int restored = restoreOrphanBackups();
            if (restored > 0) {
                plugin.getLogger().info(
                    "[Gimmick] " + restored + " gimmick(s) de uma sessão anterior foram restauradas."
                );
            }
        }, RECOVERY_DELAY_TICKS);
    }

    public int countPasted() {
        int total = 0;
        for (Deque<PastedGimmick> pasted : pastedPerHeat.values()) {
            total += pasted.size();
        }
        return total;
    }

    private Location anchorOf(World world, List<GimmickFile.Entry> entries) {
        if (world == null || entries.isEmpty()) return null;
        GimmickFile.Entry first = entries.get(0);
        return new Location(world, first.x(), first.y(), first.z());
    }

    private void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            logWarn("Não foi possível apagar " + file.getName());
        }
    }

    private void announce(Heats heat, GimmickConfig gimmick) {
        String message = gimmick.getAnnounceMessage();
        if (message == null || message.isBlank()) return;

        String formatted = ChatColor.translateAlternateColorCodes('&', message);
        for (UUID uuid : heat.getDrivers().keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) continue;
            // Off the thread that fired the paste: on Folia a player is owned by its own
            // region, and the paste runs on the region of the gimmick.
            SchedulerHelper.runTaskFor(plugin, player, () -> {
                player.sendMessage(formatted);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
            });
        }
    }

    private void logDebug(String message) {
        if (plugin.getDebugManager() != null) {
            plugin.getDebugManager().logRaceSystem(message);
        }
    }

    private void logWarn(String message) {
        plugin.getLogger().warning(message);
        logDebug(message);
    }

    /** A build loaded into memory, waiting for its lap (and the undo data that goes with it). */
    private static final class PreparedGimmick {

        private final GimmickConfig gimmick;
        private final File backupFile;
        // Written on a region thread and read by the main thread that fires the paste.
        private volatile List<GimmickBlocks.Placement> placements;
        private volatile List<GimmickFile.Entry> backupEntries;

        PreparedGimmick(GimmickConfig gimmick, File backupFile) {
            this.gimmick = gimmick;
            this.backupFile = backupFile;
        }

        void markReady(List<GimmickBlocks.Placement> placements, List<GimmickFile.Entry> backupEntries) {
            this.placements = placements;
            this.backupEntries = backupEntries;
        }

        boolean isReady() {
            return placements != null && backupEntries != null;
        }

        List<GimmickBlocks.Placement> placements() {
            return placements;
        }

        List<GimmickFile.Entry> backupEntries() {
            return backupEntries;
        }

        File backupFile() {
            return backupFile;
        }

        GimmickConfig gimmick() {
            return gimmick;
        }
    }

    /** A paste that is still applied in the world, together with what it replaced. */
    private static final class PastedGimmick {

        private final String gimmickName;
        private final World world;
        private final List<GimmickFile.Entry> backupEntries;
        private final File backupFile;

        PastedGimmick(String gimmickName, World world, List<GimmickFile.Entry> backupEntries, File backupFile) {
            this.gimmickName = gimmickName;
            this.world = world;
            this.backupEntries = backupEntries;
            this.backupFile = backupFile;
        }

        String gimmickName() {
            return gimmickName;
        }

        World world() {
            return world;
        }

        List<GimmickFile.Entry> backupEntries() {
            return backupEntries;
        }

        File backupFile() {
            return backupFile;
        }
    }
}
