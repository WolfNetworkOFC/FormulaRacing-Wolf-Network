package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.Lap;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.style.TimingScoreboardStyle;
import java.time.Duration;
import java.time.Instant;
import fr.mrmicky.fastboard.FastBoard;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class RaceScoreboardManagerAdvanced implements RaceScoreboardService {
    private final FormulaRacing plugin;
    private final Map<UUID, FastBoard> boards;
    private final Map<UUID, Heats> playerHeats;
    private final Map<UUID, FastBoard> spectatorBoards;
    private final Map<UUID, Heats> spectatorHeats;
    private BukkitTask updateTask;
    private Instant lastUpdate;
    private final int maxRows;
    private final String accentMarker;
    private static final int UPDATE_INTERVAL_TICKS = 2;

    public RaceScoreboardManagerAdvanced(FormulaRacing plugin) {
        this.plugin = plugin;
        this.boards = new HashMap<UUID, FastBoard>();
        this.playerHeats = new HashMap<UUID, Heats>();
        this.spectatorBoards = new HashMap<UUID, FastBoard>();
        this.spectatorHeats = new HashMap<UUID, Heats>();
        this.lastUpdate = Instant.now();
        this.maxRows = plugin.getConfig().getInt("scoreboard.max-rows", 15);
        String configuredMarker = plugin.getConfig().getString("scoreboard.style.accent-marker", "┃");
        this.accentMarker = TimingScoreboardStyle.normalizeAccentMarker(configuredMarker);
        this.startAutoUpdate();
    }

    private void startAutoUpdate() {
        this.updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    Instant now = Instant.now();
                    // Controle de Delta Time para não sobrecarregar o processador
                    if (Duration.between(RaceScoreboardManagerAdvanced.this.lastUpdate, now).toMillis() < 500L) {
                        return;
                    }
                    RaceScoreboardManagerAdvanced.this.lastUpdate = now;

                    // Mapas com tipagem correta <Chave, Valor>
                    HashMap<Heats, List<Player>> heatToPlayers = new HashMap<>();
                    for (Map.Entry<UUID, Heats> entry : RaceScoreboardManagerAdvanced.this.playerHeats.entrySet()) {
                        Player player = Bukkit.getPlayer(entry.getKey());
                        if (player == null || !player.isOnline()) continue;

                        heatToPlayers.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(player);
                    }

                    HashMap<Heats, List<Player>> heatToSpectators = new HashMap<>();
                    // Tipagem correta para o loop de espectadores
                    for (Map.Entry<UUID, Heats> entry : RaceScoreboardManagerAdvanced.this.spectatorHeats.entrySet()) {
                        Player s = Bukkit.getPlayer(entry.getKey());
                        if (s == null || !s.isOnline()) continue;

                        heatToSpectators.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(s);
                    }

                    ScoreboardTickCache tickCache = new ScoreboardTickCache();
                    // Usando Set<Heats> para evitar duplicatas entre pilotos e specs
                    HashSet<Heats> allHeats = new HashSet<>(heatToPlayers.keySet());
                    allHeats.addAll(heatToSpectators.keySet());

                    for (Heats heat : allHeats) {
                        try {
                            List<Player> drivers = heatToPlayers.getOrDefault(heat, Collections.emptyList());
                            List<Player> spectators = heatToSpectators.getOrDefault(heat, Collections.emptyList());

                            RaceScoreboardManagerAdvanced.this.updateHeatScoreboards(heat, drivers, spectators, tickCache);
                        } catch (Exception e) {
                            RaceScoreboardManagerAdvanced.this.plugin.getDebugManager().logRaceSystem(
                                    "[ScoreboardAdv ERROR] Falha ao atualizar heat " + heat.getId() + ": " + e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    RaceScoreboardManagerAdvanced.this.plugin.getDebugManager().logRaceSystem(
                            "[ScoreboardAdv FATAL] Erro no loop principal: " + e.getMessage());
                }
            }
        }.runTaskTimer(this.plugin, 0L, 2L); // 2 ticks = 10 updates por segundo
    }

    private String getTranslatedCached(Player viewer, String key, ScoreboardTickCache cache) {
        String lang = this.plugin.getTranslationUtil().getPlayerLanguage(viewer.getUniqueId());

        // O cache precisa ser Map<String, Map<String, String>>
        Map<String, String> langMap = cache.translationCache.computeIfAbsent(lang, k -> new HashMap<>());

        return langMap.computeIfAbsent(key, k -> this.plugin.getTranslationUtil().getTranslated(viewer, key));
    }

    private String commonSeparator(Player viewer) {
        return this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_common_separator");
    }

    private String commonFooter(Player viewer) {
        return this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_common_footer");
    }

    private void updateHeatScoreboards(Heats heat, List<Player> drivers, List<Player> spectators, ScoreboardTickCache cache) {
        // Alterado para List<Driver> para bater com o retorno de getSortedDriversForHeat
        List<Driver> sorted = cache.sortedDrivers.computeIfAbsent(heat, this::getSortedDriversForHeat);

        for (Player p : drivers) {
            // Certifique-se que este método aceite List<Driver> como 3º argumento
            this.updateScoreboardOptimized(p, heat, sorted, cache);
        }
        for (Player s : spectators) {
            this.updateSpectatorScoreboardOptimized(s, heat, sorted, cache);
        }
    }

    public void addPlayer(Player player, Heats heat) {
        this.removePlayer(player);
        this.playerHeats.put(player.getUniqueId(), heat);
        FastBoard board = new FastBoard(player);
        this.boards.put(player.getUniqueId(), board);
        this.updateScoreboard(player, heat);
    }

    public void removePlayer(Player player) {
        this.playerHeats.remove(player.getUniqueId());
        FastBoard board = this.boards.remove(player.getUniqueId());
        if (board != null) {
            board.delete();
        }
    }

    public void removeHeat(Heats heat) {
        for (Driver driver : heat.getDrivers().values()) {
            Heats currentHeat;
            Player player = Bukkit.getPlayer((UUID)driver.getUuid());
            if (player == null || (currentHeat = this.playerHeats.get(player.getUniqueId())) == null || !currentHeat.equals(heat)) continue;
            this.removePlayer(player);
        }
    }

    public void addSpectator(Player spectator, Heats heat) {
        this.spectatorHeats.put(spectator.getUniqueId(), heat);
        FastBoard board = new FastBoard(spectator);
        this.spectatorBoards.put(spectator.getUniqueId(), board);
        this.updateSpectatorScoreboard(spectator, heat);
    }

    public void removeSpectator(Player spectator) {
        this.spectatorHeats.remove(spectator.getUniqueId());
        FastBoard board = this.spectatorBoards.remove(spectator.getUniqueId());
        if (board != null) {
            board.delete();
        }
    }

    private void updateScoreboard(Player player, Heats heat) {
        ScoreboardTickCache cache = new ScoreboardTickCache();
        this.updateScoreboardOptimized(player, heat, this.getSortedDriversForHeat(heat), cache);
    }

    private void updateSpectatorScoreboard(Player spectator, Heats heat) {
        ScoreboardTickCache cache = new ScoreboardTickCache();
        this.updateSpectatorScoreboardOptimized(spectator, heat, this.getSortedDriversForHeat(heat), cache);
    }

    private List<Driver> getSortedDriversForHeat(Heats h) {
        if (h.getHeatState() == HeatState.QUALIFYING || h.getHeatState() == HeatState.PRACTICE || h.getHeatState() == HeatState.IDLE) {
            return h.getDrivers().values().stream().filter(d -> d.getFastestLap() != null).sorted(Comparator.comparingLong(d -> d.getFastestLap().getLapTime())).collect(Collectors.toList());
        }
        return h.getDrivers().values().stream().filter(d -> !d.isDnf()).sorted(Comparator.comparingInt(Driver::getPosition)).collect(Collectors.toList());
    }

    private void updateScoreboardOptimized(Player player, Heats heat, List<Driver> sorted, ScoreboardTickCache cache) {
        List<String> lines;
        FastBoard board = this.boards.get(player.getUniqueId());
        if (board == null) {
            return;
        }
        Driver driver = heat.getDriver(player.getUniqueId());
        if (driver == null) {
            return;
        }
        String titleStr = this.getTitleForState(heat.getHeatState(), player);
        board.updateTitle(titleStr);
        try {
            lines = switch (heat.getHeatState()) {
                default -> throw new MatchException(null, null);
                case HeatState.SETUP -> this.getLinesSetup(heat, player);
                case HeatState.IDLE, HeatState.PRACTICE -> this.getLinesPractice(heat, driver, player, sorted, cache);
                case HeatState.QUALIFYING -> this.getLinesQualifying(heat, driver, player, sorted, cache);
                case HeatState.LOADED -> this.getLinesLoaded(heat, driver, player);
                case HeatState.STARTING -> this.getLinesStarting(heat, driver, player);
                case HeatState.RACING -> this.getLinesRacing(heat, driver, player, sorted, cache);
                case HeatState.FINISHED -> this.getLinesFinished(heat, driver, player, sorted, cache);
            };
        } catch (Exception e) {
            this.plugin.getDebugManager().logRaceSystem("[Scoreboard ERROR] Falha ao atualizar para " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            lines = new ArrayList<String>();
            lines.add("\u00a7c\u26a0 Scoreboard Error");
            lines.add("\u00a77Contact Admin");
        }
        board.updateLines(lines);
    }

    private void updateSpectatorScoreboardOptimized(Player spectator, Heats heat, List<Driver> sorted, ScoreboardTickCache cache) {
        List<String> lines;
        FastBoard board = this.spectatorBoards.get(spectator.getUniqueId());
        if (board == null) {
            return;
        }
        String titleStr = this.getTitleForState(heat.getHeatState(), spectator);
        board.updateTitle(titleStr);
        try {
            lines = switch (heat.getHeatState()) {
                default -> throw new MatchException(null, null);
                case HeatState.SETUP -> this.getLinesSetup(heat, spectator);
                case HeatState.IDLE, HeatState.PRACTICE -> this.getLinesSpectatorPractice(heat, spectator, sorted, cache);
                case HeatState.QUALIFYING -> this.getLinesSpectatorQualifying(heat, spectator, sorted, cache);
                case HeatState.LOADED -> this.getLinesSpectatorLoaded(heat, spectator);
                case HeatState.STARTING -> this.getLinesSpectatorStarting(heat, spectator, sorted, cache);
                case HeatState.RACING -> this.getLinesSpectatorRacing(heat, spectator, sorted, cache);
                case HeatState.FINISHED -> this.getLinesSpectatorFinished(heat, spectator, sorted, cache);
            };
        } catch (Exception e) {
            this.plugin.getDebugManager().logRaceSystem("[Scoreboard ERROR] Falha ao atualizar espectador " + spectator.getName() + ": " + e.getMessage());
            e.printStackTrace();
            lines = new ArrayList<String>();
            lines.add("\u00a7c\u26a0 Scoreboard Error");
            lines.add("\u00a77Contact Admin");
        }
        board.updateLines(lines);
    }

    private String getTitleForState(HeatState state, Player viewer) {
        return switch (state) {
            default -> throw new MatchException(null, null);
            case HeatState.SETUP -> this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_waiting", new String[0]);
            case HeatState.IDLE, HeatState.PRACTICE -> this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_practice", new String[0]);
            case HeatState.QUALIFYING -> this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_qualifying", new String[0]);
            case HeatState.LOADED -> this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_waiting", new String[0]);
            case HeatState.STARTING -> this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_starting", new String[0]);
            case HeatState.RACING -> this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_race", new String[0]);
            case HeatState.FINISHED -> this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_finished", new String[0]);
        };
    }

    private List<String> getLinesSetup(Heats heat, Player viewer) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(this.commonSeparator(viewer));
        lines.add("\u00a77" + this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_state_preparing", new String[0]));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_track", "{track}", heat.getTrackNameWS()));
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_drivers", "{drivers}", heat.getDriverCount() + "\u00a77/\u00a7f" + heat.getMaxDrivers()));
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_laps", "{laps}", String.valueOf(heat.getTotalLaps())));
        if (heat.getTotalPits() > 0) {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_pits", "{pits}", String.valueOf(heat.getTotalPits())));
        }
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_waiting_start", new String[0]));
        lines.add(this.commonSeparator(viewer));
        lines.add(this.commonFooter(viewer));
        return lines;
    }

    private List<String> getLinesPractice(Heats heat, Driver driver, Player viewer, List<Driver> sortedDrivers, ScoreboardTickCache cache) {
        long remaining;
        UUID creatorUuid;
        String eventName;
        ArrayList<String> lines = new ArrayList<String>();
        String heatName = heat.getName();
        String string = eventName = heat.getRound() != null && heat.getRound().getEvent() != null ? heat.getRound().getEvent().getDisplayName() : "";
        if (eventName.length() > 16) {
            eventName = eventName.substring(0, 16);
        }
        lines.add("\u00a7e" + heatName + (String)(eventName.isEmpty() ? "" : " \u00a78/ \u00a77" + eventName));
        lines.add("");
        if (this.plugin.getDailyRaceManager() != null && heat.getRound() != null && heat.getRound().getEvent() != null && (creatorUuid = heat.getRound().getEvent().getCreatorUUID()) != null && creatorUuid.getMostSignificantBits() == 0L && creatorUuid.getLeastSignificantBits() == 0L && (remaining = this.plugin.getDailyRaceManager().getPracticeTimeRemaining()) >= 0L && this.plugin.getDailyRaceManager().getPracticeStartTime() != null) {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_practice_end", new String[0]) + "\u00a7c" + this.formatTimeShort(remaining));
            lines.add("");
        }
        int fixedLines = 4;
        int availableLines = Math.max(5, this.maxRows - fixedLines);
        int playerPosIdx = -1;
        for (int i = 0; i < sortedDrivers.size(); ++i) {
            if (!sortedDrivers.get(i).getUuid().equals(driver.getUuid())) continue;
            playerPosIdx = i;
            break;
        }
        String lang = this.plugin.getTranslationUtil().getPlayerLanguage(viewer.getUniqueId());
        int totalDrivers = sortedDrivers.size();
        int start = 0;
        int end = 0;
        if (totalDrivers <= availableLines) {
            start = 0;
            end = totalDrivers;
        } else {
            int halfWindow = availableLines / 2;
            start = Math.max(0, playerPosIdx - halfWindow);
            end = Math.min(totalDrivers, start + availableLines);
            if (end - start < availableLines) {
                start = Math.max(0, end - availableLines);
            }
        }
        for (int i = start; i < end; ++i) {
            Driver d = sortedDrivers.get(i);
            int pos = i + 1;
            String formatted = this.formatDriverLineAdvanced(d, driver, heat, pos, sortedDrivers, true, viewer);
            lines.add(formatted);
        }
        lines.add("");
        lines.add(this.commonFooter(viewer));
        return lines;
    }

    private List<String> getLinesSpectatorPractice(Heats heat, Player viewer, List<Driver> sortedDrivers, ScoreboardTickCache cache) {
        long remaining;
        UUID creatorUuid;
        String eventName;
        ArrayList<String> lines = new ArrayList<String>();
        String heatName = heat.getName();
        String string = eventName = heat.getRound() != null && heat.getRound().getEvent() != null ? heat.getRound().getEvent().getDisplayName() : "";
        if (eventName.length() > 16) {
            eventName = eventName.substring(0, 16);
        }
        lines.add("\u00a7e" + heatName + (String)(eventName.isEmpty() ? "" : " \u00a78/ \u00a77" + eventName));
        lines.add("");
        if (this.plugin.getDailyRaceManager() != null && heat.getRound() != null && heat.getRound().getEvent() != null && (creatorUuid = heat.getRound().getEvent().getCreatorUUID()) != null && creatorUuid.getMostSignificantBits() == 0L && creatorUuid.getLeastSignificantBits() == 0L && (remaining = this.plugin.getDailyRaceManager().getPracticeTimeRemaining()) >= 0L && this.plugin.getDailyRaceManager().getPracticeStartTime() != null) {
            lines.add(this.getTranslatedCached(viewer, "scoreboard_label_practice_end", cache) + "\u00a7c" + this.formatTimeShort(remaining));
            lines.add("");
        }
        int fixedLines = 4;
        int availableLines = Math.max(5, this.maxRows - fixedLines);
        int totalDrivers = sortedDrivers.size();
        int limit = Math.min(availableLines, totalDrivers);
        for (int i = 0; i < limit; ++i) {
            Driver d = sortedDrivers.get(i);
            int pos = i + 1;
            String formatted = this.formatDriverLineSpectator(d, heat, pos, sortedDrivers, true, viewer);
            lines.add(formatted);
        }
        if (sortedDrivers.isEmpty()) {
            lines.add("\u00a78" + this.getTranslatedCached(viewer, "scoreboard_waiting_times", cache));
        }
        lines.add("");
        lines.add(this.commonFooter(viewer));
        return lines;
    }

    private List<String> getLinesQualifying(Heats heat, Driver driver, Player viewer, List<Driver> sortedDrivers, ScoreboardTickCache cache) {
        String eventName;
        ArrayList<String> lines = new ArrayList<String>();
        String heatName = heat.getName();
        String string = eventName = heat.getRound() != null && heat.getRound().getEvent() != null ? heat.getRound().getEvent().getDisplayName() : "";
        if (eventName.length() > 16) {
            eventName = eventName.substring(0, 16);
        }
        lines.add("\u00a7e" + heatName + (String)(eventName.isEmpty() ? "" : " \u00a78/ \u00a77" + eventName));
        lines.add("");
        long remaining = heat.getSessionTimeRemaining();
        if (remaining > 0L) {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_v2_time", "{time}", "§b" + this.formatTimeShort(remaining)));
            lines.add("");
        } else if (remaining == 0L) {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_status_finishing", new String[0]));
            lines.add("");
        }
        int fixedLines = remaining >= 0L ? 5 : 4;
        int availableLines = Math.max(5, this.maxRows - fixedLines);
        int playerPosIdx = -1;
        for (int i = 0; i < sortedDrivers.size(); ++i) {
            if (!sortedDrivers.get(i).getUuid().equals(driver.getUuid())) continue;
            playerPosIdx = i;
            break;
        }
        int totalDrivers = sortedDrivers.size();
        int start = 0;
        int end = 0;
        if (totalDrivers <= availableLines) {
            start = 0;
            end = totalDrivers;
        } else {
            int halfWindow = availableLines / 2;
            start = Math.max(0, playerPosIdx - halfWindow);
            end = Math.min(totalDrivers, start + availableLines);
            if (end - start < availableLines) {
                start = Math.max(0, end - availableLines);
            }
        }
        for (int i = start; i < end; ++i) {
            Driver d = sortedDrivers.get(i);
            int pos = i + 1;
            String formatted = this.formatDriverLineAdvanced(d, driver, heat, pos, sortedDrivers, true, viewer);
            lines.add(formatted);
        }
        if (sortedDrivers.isEmpty()) {
            lines.add("\u00a78" + this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_waiting_times", new String[0]));
        }
        lines.add("");
        lines.add(this.commonFooter(viewer));
        return lines;
    }

    private List<String> getLinesSpectatorQualifying(Heats heat, Player viewer, List<Driver> sortedDrivers, ScoreboardTickCache cache) {
        String eventName;
        ArrayList<String> lines = new ArrayList<String>();
        String heatName = heat.getName();
        String string = eventName = heat.getRound() != null && heat.getRound().getEvent() != null ? heat.getRound().getEvent().getDisplayName() : "";
        if (eventName.length() > 16) {
            eventName = eventName.substring(0, 16);
        }
        lines.add("\u00a7e" + heatName + (String)(eventName.isEmpty() ? "" : " \u00a78/ \u00a77" + eventName));
        lines.add("");
        long remaining = heat.getSessionTimeRemaining();
        if (remaining > 0L) {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_v2_time", "{time}", "§b" + this.formatTimeShort(remaining)));
            lines.add("");
        }
        int fixedLines = remaining >= 0L ? 5 : 4;
        int availableLines = Math.max(5, this.maxRows - fixedLines);
        int totalDrivers = sortedDrivers.size();
        int limit = Math.min(availableLines, sortedDrivers.size());
        for (int i = 0; i < limit; ++i) {
            Driver d = sortedDrivers.get(i);
            int pos = i + 1;
            String formatted = this.formatDriverLineSpectator(d, heat, pos, sortedDrivers, true, viewer);
            lines.add(formatted);
        }
        if (sortedDrivers.isEmpty()) {
            lines.add("\u00a78" + this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_waiting_times", new String[0]));
        }
        lines.add("");
        lines.add(this.commonFooter(viewer));
        return lines;
    }

    private List<String> getLinesSpectatorRacing(Heats heat, Player viewer, List<Driver> sortedDrivers, ScoreboardTickCache cache) {
        ArrayList<String> lines = new ArrayList<String>();
        String heatName = heat.getName();
        String eventName = "";
        if (heat.getRound() != null && heat.getRound().getEvent() != null && (eventName = heat.getRound().getEvent().getDisplayName()).length() > 16) {
            eventName = eventName.substring(0, 16);
        }
        lines.add("\u00a7e" + heatName + (String)(eventName.isEmpty() ? "" : " \u00a78/ \u00a77" + eventName));
        lines.add("");
        int fixedLines = 4;
        int availableLines = Math.max(5, this.maxRows - fixedLines);
        int totalDrivers = sortedDrivers.size();
        int limit = Math.min(availableLines, totalDrivers);
        for (int i = 0; i < limit; ++i) {
            Driver d = sortedDrivers.get(i);
            int pos = i + 1;
            String formatted = this.formatDriverLineSpectator(d, heat, pos, sortedDrivers, false, viewer);
            lines.add(formatted);
        }
        if (limit < totalDrivers) {
            // empty if block
        }
        lines.add("");
        lines.add(this.commonFooter(viewer));
        return lines;
    }

    private List<String> getLinesLoaded(Heats heat, Driver driver, Player viewer) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(this.commonSeparator(viewer));
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_grid", new String[0]));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_position_f", "{pos}", String.valueOf(driver.getStartPosition())));
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_laps", "{laps}", String.valueOf(heat.getTotalLaps())));
        if (heat.getTotalPits() > 0) {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_pits", "{pits}", String.valueOf(heat.getTotalPits())));
        }
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_prepare_start", new String[0]));
        lines.add(this.commonSeparator(viewer));
        lines.add(this.commonFooter(viewer));
        return lines;
    }

    private List<String> getLinesStarting(Heats heat, Driver driver, Player viewer) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(this.commonSeparator(viewer));
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_lights_out", new String[0]));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_position_f", "{pos}", String.valueOf(driver.getStartPosition())));
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_laps", "{laps}", String.valueOf(heat.getTotalLaps())));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_lights_out", new String[0]));
        lines.add(this.commonSeparator(viewer));
        lines.add(this.commonFooter(viewer));
        return lines;
    }

    private List<String> getLinesRacing(Heats heat, Driver driver, Player viewer, List<Driver> sortedDrivers, ScoreboardTickCache cache) {
        String eventName;
        if (driver.isFinished()) {
            return this.getLinesFinished(heat, driver, viewer, sortedDrivers, cache);
        }
        ArrayList<String> lines = new ArrayList<String>();
        String heatName = heat.getName();
        String string = eventName = heat.getRound() != null && heat.getRound().getEvent() != null ? heat.getRound().getEvent().getDisplayName() : "";
        if (eventName.length() > 16) {
            eventName = eventName.substring(0, 16);
        }
        lines.add("\u00a7e" + heatName + (String)(eventName.isEmpty() ? "" : " \u00a78/ \u00a77" + eventName));
        lines.add("");
        int positionVal = driver.getPosition();
        int totalLaps = heat.getTotalLaps();
        int lapCount = driver.getCurrentLap() == null ? 0 : Math.min(totalLaps, driver.getLapCount() + 1);
        lines.add("\u00a7f" + positionVal + "\u00ba \u00a78| " + this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_practice_lap", "{lap}", lapCount + "\u00a77/\u00a7f" + totalLaps));
        lines.add("");
        int fixedLines = 6;
        int availableLines = Math.max(5, this.maxRows - fixedLines);
        int playerPosIdx = -1;
        for (int i = 0; i < sortedDrivers.size(); ++i) {
            if (!sortedDrivers.get(i).getUuid().equals(driver.getUuid())) continue;
            playerPosIdx = i;
            break;
        }
        int totalDrivers = sortedDrivers.size();
        int start = 0;
        int end = 0;
        if (totalDrivers <= availableLines) {
            start = 0;
            end = totalDrivers;
        } else {
            int halfWindow = availableLines / 2;
            start = Math.max(0, playerPosIdx - halfWindow);
            end = Math.min(totalDrivers, start + availableLines);
            if (end - start < availableLines) {
                start = Math.max(0, end - availableLines);
            }
        }
        for (int i = start; i < end; ++i) {
            Driver d = sortedDrivers.get(i);
            int pos = i + 1;
            String formatted = this.formatDriverLineAdvanced(d, driver, heat, pos, sortedDrivers, false, viewer);
            lines.add(formatted);
        }
        lines.add("");
        lines.add(this.commonFooter(viewer));
        return lines;
    }

    private List<String> getLinesFinished(Heats heat, Driver driver, Player viewer, List<Driver> sortedDrivers, ScoreboardTickCache cache) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(this.commonSeparator(viewer));
        lines.add(this.getTranslatedCached(viewer, "scoreboard_title_finished", cache));
        lines.add("");
        if (driver.isFinished()) {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_position_f", "{pos}", "#" + driver.getPosition()));
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_time", "{time}", this.formatTime(driver.getTotalTime())));
            if (driver.getFastestLap() != null) {
                lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_best", "{time}", this.formatTime(driver.getFastestLap().getLapTime())));
            }
            if (heat.getTotalPits() > 0) {
                lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_pits", "{pits}", driver.getPitstops() + "\u00a77/\u00a7f" + heat.getTotalPits()));
            }
        } else if (driver.isDnf()) {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_status_dnf", new String[0]));
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_status_dnf_desc", new String[0]));
        }
        lines.add("");
        lines.add(this.getTranslatedCached(viewer, "scoreboard_header_final", cache));
        int count = 0;
        for (Driver d : sortedDrivers) {
            Player p;
            if (!d.isFinished() || (p = Bukkit.getPlayer((UUID)d.getUuid())) == null) continue;
            String name = p.getName();
            if (name.length() > 10) {
                name = name.substring(0, 10);
            }
            lines.add(String.format("\u00a77%d. \u00a7f%s \u00a78%s", d.getPosition(), name, this.formatTime(d.getTotalTime())));
            if (++count < 5) continue;
            break;
        }
        lines.add(this.commonSeparator(viewer));
        lines.add(this.commonFooter(viewer));
        return lines;
    }

    private List<String> getLinesSpectatorStarting(Heats heat, Player viewer, List<Driver> sortedDrivers, ScoreboardTickCache cache) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(this.commonSeparator(viewer));
        lines.add(this.getTranslatedCached(viewer, "scoreboard_lights_out", cache));
        lines.add("");
        lines.add(this.getTranslatedCached(viewer, "scoreboard_lights_out", cache));
        lines.add(this.commonSeparator(viewer));
        lines.add(this.commonFooter(viewer));
        return lines;
    }

    private List<String> getLinesSpectatorLoaded(Heats heat, Player viewer) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(this.commonSeparator(viewer));
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_grid", new String[0]));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_drivers", "{drivers}", String.valueOf(heat.getDriverCount())));
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_laps", "{laps}", String.valueOf(heat.getTotalLaps())));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_waiting_start", new String[0]));
        lines.add(this.commonSeparator(viewer));
        lines.add(this.commonFooter(viewer));
        return lines;
    }

    private List<String> getLinesSpectatorFinished(Heats heat, Player viewer, List<Driver> sortedDrivers, ScoreboardTickCache cache) {
        if (heat.getDrivers().isEmpty()) {
            return new ArrayList<String>();
        }
        Driver firstDriver = heat.getDrivers().values().iterator().next();
        return this.getLinesFinished(heat, firstDriver, viewer, sortedDrivers, cache);
    }

    private String formatDriverLine(Driver d, Driver compareDriver, Heats heat, int position, boolean isQualification, Player viewer) {
        Player p = Bukkit.getPlayer((UUID)d.getUuid());
        String posText = this.paddPosition(position, d, heat);
        String middle = this.getMiddleBlock(d, compareDriver, p, heat, isQualification, viewer);
        String teamMarker = this.getTeamMarker(position);
        String name = this.paddDriverName(p != null ? p.getName() : this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_status_offline", new String[0]));
        String pitInfo = this.getPitStopIndicator(d.getPitstops(), heat.getTotalPits());
        return posText + " §8|" + middle + " " + teamMarker + " " + name + pitInfo;
    }

    private String getPitStopStatusDetailed(int completed, int required, Player viewer) {
        if (completed == 0) {
            return this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_pit_status_mandatory", "{current}", String.valueOf(completed), "{total}", String.valueOf(required));
        }
        if (completed < required) {
            return this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_pit_status_remaining_detailed", "{current}", String.valueOf(completed), "{total}", String.valueOf(required), "{missing}", String.valueOf(required - completed));
        }
        return this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_pit_status_complete_detailed", "{current}", String.valueOf(completed), "{total}", String.valueOf(required));
    }

    private String formatDriverLineAdvanced(Driver d, Driver currentDriver, Heats heat, int position, List<Driver> allDrivers, boolean isQualification, Player viewer) {
        Driver compareDriver;
        if (currentDriver != null && currentDriver.getUuid().equals(d.getUuid())) {
            compareDriver = d;
        } else {
            compareDriver = currentDriver;
        }
        return this.formatDriverLine(d, compareDriver, heat, position, isQualification, viewer);
    }

    private String getTeamMarker(int position) {
        return TimingScoreboardStyle.teamMarker(this.accentMarker, position);
    }

    private String getMiddleBlock(Driver current, Driver compareDriver, Player player, Heats heat, boolean isQualification, Player viewer) {
        String status = this.getDriverStatus(current, player, heat, viewer);
        if (!status.isEmpty()) {
            return status;
        }

        if (isQualification) {
            if (compareDriver == null || compareDriver.getUuid().equals(current.getUuid())) {
                Lap best = current.getFastestLap();
                return best == null ? " §8--.---   " : " §7" + TimingScoreboardStyle.padRight(this.formatTime(best.getLapTime()), 8);
            }
            return this.calculateGap(current, compareDriver, heat, true);
        }

        if (compareDriver == null || compareDriver.getUuid().equals(current.getUuid())) {
            return this.getDrsIndicatorOrSpacing(current);
        }

        return this.calculateGap(current, compareDriver, heat, false);
    }

    private String getDrsIndicatorOrSpacing(Driver driver) {
        if (driver.isDrsActive()) {
            return " §a§lDRS§r      ";
        }
        if (driver.hasDrsPermission()) {
            return " §f§lDRS§r      ";
        }
        return " §8         ";
    }

    private String calculateGap(Driver current, Driver ahead, Heats heat, boolean isQualification) {
        int totalCheckpoints;
        if (isQualification) {
            Lap currentBest = current.getFastestLap();
            Lap aheadBest = ahead.getFastestLap();
            if (currentBest == null) {
                return "\u00a78--";
            }
            if (aheadBest == null) {
                return "\u00a78--";
            }
            long diff = currentBest.getLapTime() - aheadBest.getLapTime();
            if (diff > 0L) {
                return " \u00a7a+" + TimingScoreboardStyle.padRight(this.formatTimeDiff(diff), 7);
            }
            if (diff < 0L) {
                return " \u00a7c-" + TimingScoreboardStyle.padRight(this.formatTimeDiff(Math.abs(diff)), 7);
            }
            return " \u00a7e=" + TimingScoreboardStyle.padRight("0.000", 7);
        }
        int currentLaps = current.getLapCount();
        int aheadLaps = ahead.getLapCount();
        int currentCP = current.getCheckpointsReached();
        Long currentTime = current.getAbsoluteTimeAtProgress(currentLaps, currentCP);
        Long aheadTime = ahead.getAbsoluteTimeAtProgress(currentLaps, currentCP);
        long staticDiff = 0L;
        boolean hasStaticDiff = false;
        if (currentTime != null && aheadTime != null) {
            staticDiff = currentTime - aheadTime;
            hasStaticDiff = true;
        } else if (currentLaps < aheadLaps) {
            // empty if block
        }
        long liveDiff = -1L;
        if (this.plugin.getTrackIntegrationManager() != null && (totalCheckpoints = this.plugin.getTrackIntegrationManager().getCheckpointCount(heat.getTrackNameWS())) > 0) {
            Long aheadTimeAtNext;
            int nextCP = currentCP + 1;
            int nextLapIdx = currentLaps;
            if (nextCP > totalCheckpoints) {
                nextCP = 1;
                ++nextLapIdx;
            }
            if ((aheadTimeAtNext = ahead.getAbsoluteTimeAtProgress(nextLapIdx, nextCP)) != null) {
                long now = System.currentTimeMillis();
                liveDiff = now - aheadTimeAtNext;
            }
        }
        long finalDiff = 0L;
        boolean showTime = false;
        if (hasStaticDiff) {
            finalDiff = liveDiff > staticDiff ? liveDiff : staticDiff;
            showTime = true;
        } else if (liveDiff > 0L) {
            finalDiff = liveDiff;
            showTime = true;
        }
        if (showTime) {
            if (finalDiff > 0L) {
                return " \u00a7a+" + TimingScoreboardStyle.padRight(this.formatTimeDiff(finalDiff), 7);
            }
            if (finalDiff < 0L) {
                return " \u00a7c-" + TimingScoreboardStyle.padRight(this.formatTimeDiff(Math.abs(finalDiff)), 7);
            }
            return " \u00a7e=" + TimingScoreboardStyle.padRight("0.000", 7);
        }
        long currentProgressMs = current.getTimeAtLastCheckpoint();
        long aheadProgressMs = ahead.getTimeAtLastCheckpoint();
        if (currentProgressMs > 0L && aheadProgressMs > 0L) {
            long diff = currentProgressMs - aheadProgressMs;
            if (diff > 0L) {
                return " \u00a7a+" + TimingScoreboardStyle.padRight(this.formatTimeDiff(diff), 7);
            }
            if (diff < 0L) {
                return " \u00a7c-" + TimingScoreboardStyle.padRight(this.formatTimeDiff(Math.abs(diff)), 7);
            }
            return " \u00a7e=" + TimingScoreboardStyle.padRight("0.000", 7);
        }
        return " \u00a78--      ";
    }

    private String formatTime(long timeMs) {
        long minutes = timeMs / 60000L;
        long seconds = timeMs % 60000L / 1000L;
        long millis = timeMs % 1000L;
        if (minutes > 0L) {
            return String.format("%d:%02d.%03d", minutes, seconds, millis);
        }
        return String.format("%d.%03d", seconds, millis);
    }

    private String formatTimeShort(long timeMs) {
        long minutes = timeMs / 60000L;
        long seconds = timeMs % 60000L / 1000L;
        if (minutes > 0L) {
            return String.format("%d:%02d", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }

    private String formatTimeDiff(long timeMs) {
        long seconds = timeMs / 1000L;
        long millis = timeMs % 1000L;
        if (seconds > 0L) {
            return String.format("%d.%03d", seconds, millis);
        }
        return String.format("0.%03d", millis);
    }

    private String paddPosition(int pos, Driver driver, Heats heat) {
        String posColor = TimingScoreboardStyle.positionColor(pos);
        Object posStr = String.valueOf(pos);
        if (pos < 10) {
            posStr = (String)posStr + " ";
        }
        Object decoration = "";
        if (driver.getUuid().equals(heat.getFastestLapUUID())) {
            decoration = "\u00a7n";
        }
        if (driver.isFinished()) {
            decoration = (String)decoration + "\u00a7o";
        }
        return posColor + (String)decoration + (String)posStr + "\u00a7r";
    }

    private String getDriverStatus(Driver driver, Player player, Heats heat, Player viewer) {
        if (driver.isDnf()) {
            return " \u00a77" + TimingScoreboardStyle.padRight(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_status_dnf_short", new String[0]), 9);
        }
        if (player == null || !player.isOnline()) {
            return " \u00a77" + TimingScoreboardStyle.padRight(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_status_offline", new String[0]), 9);
        }
        if (this.plugin.getPitStopManager() != null && this.plugin.getPitStopManager().isPlayerInPitRegion(driver.getUuid())) {
            return " \u00a77" + TimingScoreboardStyle.padRight(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_status_in_pit", new String[0]), 9);
        }
        return "";
    }

    private String paddDriverName(String name) {
        int maxLen = 14;
        if (name.length() > maxLen) {
            name = name.substring(0, maxLen);
        }
        int spacesNeeded = maxLen - name.length();
        return "\u00a7f" + name + " ".repeat(Math.max(0, spacesNeeded));
    }

    private String getPitStopIndicator(int completed, int required) {
        if (required <= 0) {
            return "";
        }
        String color = completed == 0 ? "\u00a7c" : (completed < required ? "\u00a76" : "\u00a7a");
        return " \u00a78P: " + color + completed;
    }

    private String formatDriverLineSpectator(Driver d, Heats heat, int position, List<Driver> allDrivers, boolean isQualification, Player viewer) {
        Driver compareDriver = null;
        if (isQualification) {
            if (!allDrivers.isEmpty() && position > 1) {
                compareDriver = allDrivers.get(0);
            }
        } else if (position > 1 && position - 2 < allDrivers.size()) {
            compareDriver = allDrivers.get(position - 2);
        }
        return this.formatDriverLine(d, compareDriver, heat, position, isQualification, viewer);
    }

    public void shutdown() {
        if (this.updateTask != null) {
            this.updateTask.cancel();
        }
        for (FastBoard board : this.boards.values()) {
            board.delete();
        }
        for (FastBoard board : this.spectatorBoards.values()) {
            board.delete();
        }
        this.boards.clear();
        this.playerHeats.clear();
        this.spectatorBoards.clear();
        this.spectatorHeats.clear();
    }

    private static class ScoreboardTickCache {
        public final Map<Heats, List<Driver>> sortedDrivers = new HashMap<Heats, List<Driver>>();
        public final Map<String, Map<UUID, String>> lineCache = new HashMap<String, Map<UUID, String>>();
        public final Map<String, Map<String, String>> translationCache = new HashMap<String, Map<String, String>>();

        private ScoreboardTickCache() {
        }
    }
}
