package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.Lap;
import dev.EfraGroup.formulaRacing.Participant.Driver;
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
    private static final int UPDATE_INTERVAL_TICKS = 2;
    private static final String SCOREBOARD_SEPARATOR = "\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501";

    public RaceScoreboardManagerAdvanced(FormulaRacing plugin) {
        this.plugin = plugin;
        this.boards = new HashMap<UUID, FastBoard>();
        this.playerHeats = new HashMap<UUID, Heats>();
        this.spectatorBoards = new HashMap<UUID, FastBoard>();
        this.spectatorHeats = new HashMap<UUID, Heats>();
        this.lastUpdate = Instant.now();
        this.maxRows = plugin.getConfig().getInt("scoreboard.max-rows", 15);
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
        lines.add(SCOREBOARD_SEPARATOR);
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
        lines.add(SCOREBOARD_SEPARATOR);
        lines.add("\u00a7ewolfnetwork.com.br");
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
            String formatted = cache.lineCache.computeIfAbsent(lang, k -> new HashMap<>()).computeIfAbsent(d.getUuid(), k -> this.formatDriverLineAdvanced(d, null, heat, pos, sortedDrivers, true, viewer));
            lines.add(formatted);
        }
        lines.add("");
        lines.add("\u00a7ewolfnetwork.com.br");
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
        String lang = this.plugin.getTranslationUtil().getPlayerLanguage(viewer.getUniqueId());
        int fixedLines = 4;
        int availableLines = Math.max(5, this.maxRows - fixedLines);
        int totalDrivers = sortedDrivers.size();
        int limit = Math.min(availableLines, totalDrivers);
        for (int i = 0; i < limit; ++i) {
            Driver d = sortedDrivers.get(i);
            int pos = i + 1;
            String formatted = cache.lineCache.computeIfAbsent(lang, k -> new HashMap<>()).computeIfAbsent(d.getUuid(), k -> this.formatDriverLineSpectator(d, heat, pos, sortedDrivers, true, viewer));
            lines.add(formatted);
        }
        if (sortedDrivers.isEmpty()) {
            lines.add("\u00a78" + this.getTranslatedCached(viewer, "scoreboard_waiting_times", cache));
        }
        lines.add("");
        lines.add("\u00a7ewolfnetwork.com.br");
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
            lines.add("\u00a77Time: \u00a7b" + this.formatTimeShort(remaining));
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
            String formatted = cache.lineCache.computeIfAbsent(lang, k -> new HashMap<>()).computeIfAbsent(d.getUuid(), k -> this.formatDriverLineAdvanced(d, null, heat, pos, sortedDrivers, true, viewer));
            lines.add(formatted);
        }
        if (sortedDrivers.isEmpty()) {
            lines.add("\u00a78" + this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_waiting_times", new String[0]));
        }
        lines.add("");
        lines.add("\u00a7ewolfnetwork.com.br");
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
            lines.add("\u00a77Time: \u00a7b" + this.formatTimeShort(remaining));
            lines.add("");
        }
        int fixedLines = remaining >= 0L ? 5 : 4;
        int availableLines = Math.max(5, this.maxRows - fixedLines);
        int totalDrivers = sortedDrivers.size();
        String lang = this.plugin.getTranslationUtil().getPlayerLanguage(viewer.getUniqueId());
        int limit = Math.min(availableLines, sortedDrivers.size());
        for (int i = 0; i < limit; ++i) {
            Driver d = sortedDrivers.get(i);
            int pos = i + 1;
            String formatted = cache.lineCache.computeIfAbsent(lang, k -> new HashMap<>()).computeIfAbsent(d.getUuid(), k -> this.formatDriverLineSpectator(d, heat, pos, sortedDrivers, true, viewer));
            lines.add(formatted);
        }
        if (sortedDrivers.isEmpty()) {
            lines.add("\u00a78" + this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_waiting_times", new String[0]));
        }
        lines.add("");
        lines.add("\u00a7ewolfnetwork.com.br");
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
        String lang = this.plugin.getTranslationUtil().getPlayerLanguage(viewer.getUniqueId());
        int limit = Math.min(availableLines, totalDrivers);
        for (int i = 0; i < limit; ++i) {
            Driver d = sortedDrivers.get(i);
            int pos = i + 1;
            String formatted = cache.lineCache.computeIfAbsent(lang, k -> new HashMap<>()).computeIfAbsent(d.getUuid(), k -> this.formatDriverLineSpectator(d, heat, pos, sortedDrivers, false, viewer));
            lines.add(formatted);
        }
        if (limit < totalDrivers) {
            // empty if block
        }
        lines.add("");
        lines.add("\u00a7ewolfnetwork.com.br");
        return lines;
    }

    private List<String> getLinesLoaded(Heats heat, Driver driver, Player viewer) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(SCOREBOARD_SEPARATOR);
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_grid", new String[0]));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_position_f", "{pos}", String.valueOf(driver.getStartPosition())));
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_laps", "{laps}", String.valueOf(heat.getTotalLaps())));
        if (heat.getTotalPits() > 0) {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_pits", "{pits}", String.valueOf(heat.getTotalPits())));
        }
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_prepare_start", new String[0]));
        lines.add(SCOREBOARD_SEPARATOR);
        lines.add("\u00a7ewolfnetwork.com.br");
        return lines;
    }

    private List<String> getLinesStarting(Heats heat, Driver driver, Player viewer) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(SCOREBOARD_SEPARATOR);
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_lights_out", new String[0]));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_position_f", "{pos}", String.valueOf(driver.getStartPosition())));
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_laps", "{laps}", String.valueOf(heat.getTotalLaps())));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_lights_out", new String[0]));
        lines.add(SCOREBOARD_SEPARATOR);
        lines.add("\u00a7ewolfnetwork.com.br");
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
            String formatted = cache.lineCache.computeIfAbsent(lang, k -> new HashMap<>()).computeIfAbsent(d.getUuid(), k -> this.formatDriverLineAdvanced(d, driver, heat, pos, sortedDrivers, false, viewer));
            lines.add(formatted);
        }
        lines.add("");
        lines.add("\u00a7ewolfnetwork.com.br");
        return lines;
    }

    private List<String> getLinesFinished(Heats heat, Driver driver, Player viewer, List<Driver> sortedDrivers, ScoreboardTickCache cache) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(SCOREBOARD_SEPARATOR);
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
        lines.add(SCOREBOARD_SEPARATOR);
        lines.add("\u00a7ewolfnetwork.com.br");
        return lines;
    }

    private List<String> getLinesSpectatorStarting(Heats heat, Player viewer, List<Driver> sortedDrivers, ScoreboardTickCache cache) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(SCOREBOARD_SEPARATOR);
        lines.add(this.getTranslatedCached(viewer, "scoreboard_lights_out", cache));
        lines.add("");
        lines.add(this.getTranslatedCached(viewer, "scoreboard_lights_out", cache));
        lines.add(SCOREBOARD_SEPARATOR);
        lines.add("\u00a7ewolfnetwork.com.br");
        return lines;
    }

    private List<String> getLinesSpectatorLoaded(Heats heat, Player viewer) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(SCOREBOARD_SEPARATOR);
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_grid", new String[0]));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_drivers", "{drivers}", String.valueOf(heat.getDriverCount())));
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_laps", "{laps}", String.valueOf(heat.getTotalLaps())));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_waiting_start", new String[0]));
        lines.add(SCOREBOARD_SEPARATOR);
        lines.add("\u00a7ewolfnetwork.com.br");
        return lines;
    }

    private List<String> getLinesSpectatorFinished(Heats heat, Player viewer, List<Driver> sortedDrivers, ScoreboardTickCache cache) {
        if (heat.getDrivers().isEmpty()) {
            return new ArrayList<String>();
        }
        Driver firstDriver = heat.getDrivers().values().iterator().next();
        return this.getLinesFinished(heat, firstDriver, viewer, sortedDrivers, cache);
    }

    private String formatDriverLine(Driver d, Driver currentDriver, Heats heat, int position, List<Driver> allDrivers, boolean isQualification, Player viewer) {
        Object name;
        Player p = Bukkit.getPlayer((UUID)d.getUuid());
        String posText = this.paddPosition(position, d, heat);
        String divider = " \u00a78|";
        String status = this.getDriverStatus(d, p, heat, viewer);
        Object gap = "";
        if (status.isEmpty()) {
            if (position > 1) {
                Driver ahead = allDrivers.get(position - 2);
                gap = this.calculateGap(d, ahead, heat, isQualification);
            } else if (isQualification && d.getFastestLap() != null) {
                gap = " \u00a77" + this.formatTime(d.getFastestLap().getLapTime());
            } else if (!isQualification) {
                gap = " \u00a7e " + this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_interval", new String[0]) + " ";
            }
        }
        Object object = name = p != null ? p.getName() : "Offline";
        if (((String)name).length() > 14) {
            name = ((String)name).substring(0, 14);
        }
        name = " \u00a7f" + (String)name;
        String pitInfo = "";
        if (heat.getTotalPits() > 0 && status.isEmpty()) {
            pitInfo = this.getPitStopIndicator(d.getPitstops(), heat.getTotalPits(), viewer);
        }
        return posText + divider + status + (String)(status.isEmpty() ? gap : "") + (String)name + pitInfo;
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
        return this.formatDriverLine(d, currentDriver, heat, position, allDrivers, isQualification, viewer);
    }

    private String getPositionColor(int position) {
        return switch (position) {
            case 1 -> "\u00a76";
            case 2 -> "\u00a77";
            case 3 -> "\u00a7c";
            default -> "\u00a77";
        };
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
                return " \u00a7a+" + this.formatTimeDiff(diff);
            }
            if (diff < 0L) {
                return " \u00a7c-" + this.formatTimeDiff(Math.abs(diff));
            }
            return " \u00a7e=";
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
                return " \u00a7a+" + this.formatTimeDiff(finalDiff);
            }
            if (finalDiff < 0L) {
                return " \u00a7c-" + this.formatTimeDiff(Math.abs(finalDiff));
            }
            return " \u00a7e=";
        }
        if (currentLaps < aheadLaps) {
            return " \u00a7c-" + (aheadLaps - currentLaps) + "L";
        }
        return " \u00a78--";
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
        String posColor = this.getPositionColor(pos);
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
            return " \u00a77" + this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_status_dnf_short", new String[0]) + "     ";
        }
        if (player == null || !player.isOnline()) {
            return " \u00a77" + this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_status_offline", new String[0]) + " ";
        }
        if (this.plugin.getPitStopManager() != null && this.plugin.getPitStopManager().isPlayerInPitRegion(driver.getUuid())) {
            return " \u00a7e" + this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_status_in_pit", new String[0]) + " ";
        }
        return "";
    }

    private String paddDriverName(String name) {
        int maxLen = 18;
        if (name.length() > maxLen) {
            name = name.substring(0, maxLen);
        }
        int spacesNeeded = maxLen - name.length();
        return "\u00a7f" + name + " ".repeat(Math.max(0, spacesNeeded));
    }

    private String getPitStopIndicator(int completed, int required, Player viewer) {
        String color = completed == 0 ? "\u00a7c" : (completed < required ? "\u00a76" : "\u00a7a");
        return " \u00a77" + color + completed + "\u00a77/" + required + " " + this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_format_pits_short", new String[0]);
    }

    private String formatDriverLineSpectator(Driver d, Heats heat, int position, List<Driver> allDrivers, boolean isQualification, Player viewer) {
        Player p = Bukkit.getPlayer((UUID)d.getUuid());
        String posText = this.paddPosition(position, d, heat);
        String divider = " \u00a78|";
        String status = this.getDriverStatus(d, p, heat, viewer);
        String name = this.paddDriverName(p != null ? p.getName() : "Offline");
        String teamIcon = "\u00a77\u00a7l||\u00a7r";
        Object gap = "";
        if (status.isEmpty()) {
            if (position > 1) {
                Driver ahead = allDrivers.get(position - 2);
                gap = this.calculateGap(d, ahead, heat, isQualification);
            } else if (isQualification && d.getFastestLap() != null) {
                gap = " \u00a77" + this.formatTime(d.getFastestLap().getLapTime());
            } else if (!isQualification && position == 1) {
                gap = " \u00a7e " + this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_interval", new String[0]) + " ";
            }
        }
        String pitInfo = "";
        if (heat.getTotalPits() > 0 && status.isEmpty()) {
            pitInfo = this.getPitStopIndicator(d.getPitstops(), heat.getTotalPits(), viewer);
        }
        Object lapInfo = "";
        if (status.isEmpty()) {
            int currentLap = d.getCurrentLap() == null ? 0 : d.getLapCount() + 1;
            lapInfo = " \u00a78L" + currentLap;
        }
        return posText + divider + status + (String)(status.isEmpty() ? gap : "") + " " + teamIcon + " " + name + (String)lapInfo + pitInfo;
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
