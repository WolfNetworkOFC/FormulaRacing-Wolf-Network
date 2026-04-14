package dev.EfraGroup.formulaRacing.Utils.scoreboard.v2;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.RaceScoreboardService;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.ScoreboardOwnershipCoordinator;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.builder.DefaultStateViewModelBuilder;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.builder.FinishedViewModelBuilder;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.builder.PracticeViewModelBuilder;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.builder.QualifyingViewModelBuilder;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.builder.RacingViewModelBuilder;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.builder.StateViewModelBuilder;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.model.ScoreboardContext;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.model.ScoreboardViewModel;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.provider.ScoreboardAdapter;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.render.LineBudgetPolicy;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.render.ScoreboardRenderer;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRTheme;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeParser;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class RaceScoreboardV2Manager implements RaceScoreboardService {
    private final FormulaRacing plugin;
    private final ScoreboardAdapter primaryAdapter;
    private final ScoreboardOwnershipCoordinator ownershipCoordinator;
    private final boolean metricsLogEnabled;
    private final long metricsLogIntervalSeconds;
    private final int maxRows;
    private final long staticUpdateIntervalMs;
    private final long dynamicUpdateIntervalMs;
    private final ScoreboardRenderer renderer;
    private final List<StateViewModelBuilder> builders;
    private final StateViewModelBuilder defaultBuilder;

    private final Map<UUID, Heats> playerHeats;
    private final Map<UUID, Heats> spectatorHeats;
    private final Map<Heats, Instant> lastHeatUpdate;
    private BukkitTask updateTask;
    private Instant lastMetricsLog = Instant.now();
    private long updatesOk;
    private long updatesFailed;
    private long fallbackActivated;
    private long renderNanosTotal;
    private long renderSamples;

    public RaceScoreboardV2Manager(FormulaRacing plugin, ScoreboardAdapter primaryAdapter, ScoreboardOwnershipCoordinator ownershipCoordinator) {
        this.plugin = plugin;
        this.primaryAdapter = primaryAdapter;
        this.ownershipCoordinator = ownershipCoordinator;
        this.maxRows = plugin.getConfig().getInt("scoreboard.max-rows", 15);
        long legacyIntervalMs = parseDurationMillis(plugin.getConfig().getString("scoreboard.v2.interval", "500ms"));
        String dynamicIntervalRaw = plugin.getConfig().getString("scoreboard.v2.dynamic-interval");
        String staticIntervalRaw = plugin.getConfig().getString("scoreboard.v2.static-interval");
        this.dynamicUpdateIntervalMs = dynamicIntervalRaw == null ? Math.min(legacyIntervalMs, 200L) : parseDurationMillis(dynamicIntervalRaw);
        this.staticUpdateIntervalMs = staticIntervalRaw == null ? legacyIntervalMs : parseDurationMillis(staticIntervalRaw);
        this.metricsLogEnabled = plugin.getConfig().getBoolean("scoreboard.v2.metrics-log-enabled", false);
        this.metricsLogIntervalSeconds = Math.max(5L, plugin.getConfig().getLong("scoreboard.v2.metrics-log-interval-seconds", 30L));

        this.renderer = new ScoreboardRenderer(new LineBudgetPolicy());
        this.builders = List.of(
                new PracticeViewModelBuilder(),
                new QualifyingViewModelBuilder(),
                new RacingViewModelBuilder(),
                new FinishedViewModelBuilder()
        );
        this.defaultBuilder = new DefaultStateViewModelBuilder();

        this.playerHeats = new HashMap<>();
        this.spectatorHeats = new HashMap<>();
        this.lastHeatUpdate = new HashMap<>();

        this.startAutoUpdate();
    }

    @Override
    public void addPlayer(Player player, Heats heat) {
        if (player == null || heat == null) {
            return;
        }
        this.removePlayer(player);
        this.playerHeats.put(player.getUniqueId(), heat);
        this.ownershipCoordinator.acquire(player.getUniqueId(), ScoreboardOwnershipCoordinator.Mode.RACE);
        this.primaryAdapter.create(player);
        this.renderPlayer(player, heat, false);
    }

    @Override
    public void removePlayer(Player player) {
        if (player == null) {
            return;
        }
        this.playerHeats.remove(player.getUniqueId());
        this.ownershipCoordinator.release(player.getUniqueId(), ScoreboardOwnershipCoordinator.Mode.RACE);
        this.primaryAdapter.delete(player);
    }

    @Override
    public void removeHeat(Heats heat) {
        if (heat == null) {
            return;
        }

        this.lastHeatUpdate.remove(heat);

        this.playerHeats.entrySet().removeIf(entry -> {
            if (!entry.getValue().equals(heat)) {
                return false;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                this.primaryAdapter.delete(player);
                this.ownershipCoordinator.release(player.getUniqueId(), ScoreboardOwnershipCoordinator.Mode.RACE);
            }
            return true;
        });

        this.spectatorHeats.entrySet().removeIf(entry -> {
            if (!entry.getValue().equals(heat)) {
                return false;
            }
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                this.primaryAdapter.delete(player);
                this.ownershipCoordinator.release(player.getUniqueId(), ScoreboardOwnershipCoordinator.Mode.RACE);
            }
            return true;
        });
    }

    @Override
    public void addSpectator(Player spectator, Heats heat) {
        if (spectator == null || heat == null) {
            return;
        }
        this.removeSpectator(spectator);
        this.spectatorHeats.put(spectator.getUniqueId(), heat);
        this.ownershipCoordinator.acquire(spectator.getUniqueId(), ScoreboardOwnershipCoordinator.Mode.RACE);
        this.primaryAdapter.create(spectator);
        this.renderPlayer(spectator, heat, true);
    }

    @Override
    public void removeSpectator(Player spectator) {
        if (spectator == null) {
            return;
        }
        this.spectatorHeats.remove(spectator.getUniqueId());
        this.ownershipCoordinator.release(spectator.getUniqueId(), ScoreboardOwnershipCoordinator.Mode.RACE);
        this.primaryAdapter.delete(spectator);
    }

    @Override
    public void shutdown() {
        if (this.updateTask != null) {
            this.updateTask.cancel();
        }
        this.playerHeats.keySet().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                this.primaryAdapter.delete(p);
            }
        });
        this.spectatorHeats.keySet().forEach(uuid -> {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                this.primaryAdapter.delete(p);
            }
        });
        this.playerHeats.clear();
        this.spectatorHeats.clear();
        this.lastHeatUpdate.clear();
    }

    private void startAutoUpdate() {
        this.updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                Instant now = Instant.now();
                Map<Heats, List<Player>> playersByHeat = new HashMap<>();
                for (Map.Entry<UUID, Heats> entry : RaceScoreboardV2Manager.this.playerHeats.entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) {
                        continue;
                    }
                    playersByHeat.computeIfAbsent(entry.getValue(), ignored -> new ArrayList<>()).add(player);
                }

                Map<Heats, List<Player>> spectatorsByHeat = new HashMap<>();
                for (Map.Entry<UUID, Heats> entry : RaceScoreboardV2Manager.this.spectatorHeats.entrySet()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player == null || !player.isOnline()) {
                        continue;
                    }
                    spectatorsByHeat.computeIfAbsent(entry.getValue(), ignored -> new ArrayList<>()).add(player);
                }

                Set<Heats> heatsToRender = new HashSet<>();
                heatsToRender.addAll(playersByHeat.keySet());
                heatsToRender.addAll(spectatorsByHeat.keySet());
                RaceScoreboardV2Manager.this.lastHeatUpdate.entrySet().removeIf(entry -> !heatsToRender.contains(entry.getKey()));

                for (Heats heat : heatsToRender) {
                    if (!RaceScoreboardV2Manager.this.shouldUpdateHeat(heat, now)) {
                        continue;
                    }
                    List<Driver> sorted = RaceScoreboardV2Manager.this.getSortedDriversForHeat(heat);
                    List<Player> racePlayers = playersByHeat.get(heat);
                    if (racePlayers != null) {
                        for (Player player : racePlayers) {
                            RaceScoreboardV2Manager.this.renderPlayer(player, heat, false, sorted);
                        }
                    }
                    List<Player> spectatorPlayers = spectatorsByHeat.get(heat);
                    if (spectatorPlayers != null) {
                        for (Player player : spectatorPlayers) {
                            RaceScoreboardV2Manager.this.renderPlayer(player, heat, true, sorted);
                        }
                    }
                }

                RaceScoreboardV2Manager.this.logMetricsIfNeeded();
            }
        }.runTaskTimer(this.plugin, 0L, 2L);
    }

    private boolean shouldUpdateHeat(Heats heat, Instant now) {
        long intervalMs = this.resolveIntervalMs(heat.getHeatState());
        Instant lastUpdate = this.lastHeatUpdate.get(heat);
        if (lastUpdate != null && now.toEpochMilli() - lastUpdate.toEpochMilli() < intervalMs) {
            return false;
        }
        this.lastHeatUpdate.put(heat, now);
        return true;
    }

    private long resolveIntervalMs(HeatState state) {
        if (state == HeatState.PRACTICE || state == HeatState.QUALIFYING || state == HeatState.RACING) {
            return this.dynamicUpdateIntervalMs;
        }
        return this.staticUpdateIntervalMs;
    }

    private void renderPlayer(Player player, Heats heat, boolean spectator) {
        this.renderPlayer(player, heat, spectator, this.getSortedDriversForHeat(heat));
    }

    private void renderPlayer(Player player, Heats heat, boolean spectator, List<Driver> sortedDrivers) {
        long startNanos = System.nanoTime();
        Driver viewerDriver = spectator ? null : heat.getDriver(player.getUniqueId());
        if (!this.ownershipCoordinator.isOwner(player.getUniqueId(), ScoreboardOwnershipCoordinator.Mode.RACE)) {
            return;
        }
        boolean compact = this.plugin.getDatabaseManager().getPlayerCompactMode(player.getUniqueId());
        ScoreboardContext context = new ScoreboardContext(this.plugin, heat, player, viewerDriver, spectator, sortedDrivers, this.maxRows, compact);

        try {
            ScoreboardViewModel baseModel = this.findBuilder(heat.getHeatState()).build(context);
            ScoreboardViewModel rendered = this.renderer.render(context, baseModel);
            this.primaryAdapter.updateTitle(player, rendered.title());
            this.primaryAdapter.updateLines(player, rendered.lines());
            this.updatesOk++;
        } catch (Exception ex) {
            this.updatesFailed++;
            this.plugin.getDebugManager().logRaceSystem("[ScoreboardV2] Render error for " + player.getName() + " heat=" + heat.getId() + " state=" + heat.getHeatState() + ": " + ex.getMessage());
            this.renderSimplified(player, heat);
        } finally {
            this.renderNanosTotal += (System.nanoTime() - startNanos);
            this.renderSamples++;
        }
    }

    private void renderSimplified(Player player, Heats heat) {
        FRTheme theme = FRThemeResolver.resolveTheme(player);
        String rawName = "&n" + heat.getName();
        String rawState = "&2" + heat.getHeatState().name();
        String rawDrivers = "&2Pilotos: &1" + heat.getDriverCount();
        String rawLaps = "&2Voltas: &1" + heat.getTotalLaps();
        String rawSponsor = "&nwolfnetwork.com.br";
        List<String> lines = new ArrayList<>();
        lines.add(LegacyComponentSerializer.legacySection().serialize(FRThemeParser.parseWithLegacy(rawName, theme)));
        lines.add(LegacyComponentSerializer.legacySection().serialize(FRThemeParser.parseWithLegacy(rawState, theme)));
        lines.add(LegacyComponentSerializer.legacySection().serialize(FRThemeParser.parseWithLegacy(rawDrivers, theme)));
        lines.add(LegacyComponentSerializer.legacySection().serialize(FRThemeParser.parseWithLegacy(rawLaps, theme)));
        lines.add(LegacyComponentSerializer.legacySection().serialize(FRThemeParser.parseWithLegacy(rawSponsor, theme)));

        this.fallbackActivated++;
        this.primaryAdapter.updateTitle(player, this.plugin.getTranslationUtil().getTranslated(player, "scoreboard_title_waiting"));
        this.primaryAdapter.updateLines(player, lines);
    }

    private StateViewModelBuilder findBuilder(HeatState state) {
        for (StateViewModelBuilder builder : this.builders) {
            if (builder.supports(state)) {
                return builder;
            }
        }
        return this.defaultBuilder;
    }

    private List<Driver> getSortedDriversForHeat(Heats heat) {
        if (heat.getHeatState() == HeatState.QUALIFYING || heat.getHeatState() == HeatState.PRACTICE || heat.getHeatState() == HeatState.IDLE) {
            return heat.getDrivers().values().stream()
                    .filter(driver -> driver.getFastestLap() != null)
                    .sorted(Comparator.comparingLong(driver -> driver.getFastestLap().getLapTime()))
                    .collect(Collectors.toList());
        }

        return heat.getDrivers().values().stream()
                .filter(driver -> !driver.isDnf())
                .sorted(Comparator.comparingInt(Driver::getPosition))
                .collect(Collectors.toList());
    }

    private long parseDurationMillis(String raw) {
        if (raw == null || raw.isBlank()) {
            return 500L;
        }
        String normalized = raw.trim().toLowerCase();
        try {
            if (normalized.endsWith("ms")) {
                return Math.max(100L, Long.parseLong(normalized.substring(0, normalized.length() - 2)));
            }
            if (normalized.endsWith("s")) {
                long seconds = Long.parseLong(normalized.substring(0, normalized.length() - 1));
                return Math.max(100L, seconds * 1000L);
            }
            return Math.max(100L, Long.parseLong(normalized));
        } catch (NumberFormatException ex) {
            return 500L;
        }
    }

    private void logMetricsIfNeeded() {
        if (!this.metricsLogEnabled) {
            return;
        }
        Instant now = Instant.now();
        if (Duration.between(this.lastMetricsLog, now).toSeconds() < this.metricsLogIntervalSeconds) {
            return;
        }
        this.lastMetricsLog = now;
        long avgMicros = this.renderSamples == 0 ? 0L : (this.renderNanosTotal / this.renderSamples) / 1000L;
        this.plugin.getDebugManager().logRaceSystem(
                "[ScoreboardV2] ok=" + this.updatesOk
                        + " fail=" + this.updatesFailed
                        + " fallback=" + this.fallbackActivated
                        + " avgRenderMicros=" + avgMicros
                        + " ownership{" + this.ownershipCoordinator.metricsSnapshot() + "}"
        );
    }
}
