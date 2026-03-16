package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.Lap;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import fr.mrmicky.fastboard.FastBoard;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class RaceScoreboardManager {
    private final FormulaRacing plugin;
    private final Map<UUID, FastBoard> boards;
    private final Map<UUID, Heats> playerHeats;
    private final Map<UUID, FastBoard> spectatorBoards;
    private final Map<UUID, Heats> spectatorHeats;
    private BukkitTask updateTask;
    private Instant lastUpdate;
    private final int maxRows;
    private static final int UPDATE_INTERVAL_TICKS = 10;

    public RaceScoreboardManager(FormulaRacing plugin) {
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
        this.updateTask = new BukkitRunnable(){

            public void run() {
                Instant now = Instant.now();
                if (Duration.between(RaceScoreboardManager.this.lastUpdate, now).toMillis() < 500L) {
                    return;
                }
                RaceScoreboardManager.this.lastUpdate = now;
                for (Map.Entry<UUID, Heats> entry : RaceScoreboardManager.this.playerHeats.entrySet()) {
                    Player player = Bukkit.getPlayer((UUID)entry.getKey());
                    if (player == null || !player.isOnline()) continue;
                    RaceScoreboardManager.this.updateScoreboard(player, entry.getValue());
                }
                for (Map.Entry<UUID, Heats> entry : RaceScoreboardManager.this.spectatorHeats.entrySet()) {
                    Player spectator = Bukkit.getPlayer((UUID)entry.getKey());
                    if (spectator == null || !spectator.isOnline()) continue;
                    RaceScoreboardManager.this.updateSpectatorScoreboard(spectator, entry.getValue());
                }
            }
        }.runTaskTimer((Plugin)this.plugin, 0L, 10L);
    }

    public void addPlayer(Player player, Heats heat) {
        this.removePlayer(player);
        this.playerHeats.put(player.getUniqueId(), heat);
        FastBoard board = new FastBoard(player);
        this.boards.put(player.getUniqueId(), board);
        this.updateScoreboard(player, heat);
    }

    public void removePlayer(Player player) {
        FastBoard board;
        if (player == null) {
            return;
        }
        Heats heat = this.playerHeats.remove(player.getUniqueId());
        if (heat != null) {
            this.plugin.getDebugManager().logRaceSystem("[Scoreboard] Removendo jogador " + player.getName() + " do heat " + heat.getId());
        }
        if ((board = this.boards.remove(player.getUniqueId())) != null) {
            this.plugin.getDebugManager().logRaceSystem("[Scoreboard] Deletando board do jogador " + player.getName());
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
        FastBoard board = this.boards.get(player.getUniqueId());
        if (board == null) return;

        Driver driver = heat.getDriver(player.getUniqueId());
        if (driver == null) return;

        String title = this.getTitleForState(heat.getHeatState(), player);
        board.updateTitle(title);

        // CORREÇÃO: Usando List<String> para compatibilidade com FastBoard
        List<String> lines = new ArrayList<>();

        switch (heat.getHeatState()) {
            case SETUP:
            case IDLE:
                lines = this.getLinesSetup(heat, player);
                break;
            case PRACTICE:
            case QUALIFYING:
                lines = this.getLinesPractice(heat, driver, player);
                break;
            case LOADED:
                lines = this.getLinesLoaded(heat, driver, player);
                break;
            case STARTING:
                lines = this.getLinesStarting(heat, driver, player);
                break;
            case RACING:
                lines = this.getLinesRacing(heat, driver, player);
                break;
            case FINISHED:
                lines = this.getLinesFinished(heat, driver, player);
                break; // Adicionado break por segurança
        }
        board.updateLines(lines);
    }

    private void updateSpectatorScoreboard(Player spectator, Heats heat) {
        FastBoard board = this.spectatorBoards.get(spectator.getUniqueId());
        if (board == null) return;

        String title = this.getTitleForState(heat.getHeatState(), spectator);
        board.updateTitle(title);

        // CORREÇÃO: Usando List<String>
        List<String> lines = new ArrayList<>();

        switch (heat.getHeatState()) {
            case SETUP:
            case IDLE:
                lines = this.getLinesSetup(heat, spectator);
                break;
            case PRACTICE:
            case QUALIFYING:
                lines = this.getLinesSpectatorPractice(heat, spectator);
                break;
            case LOADED:
            case STARTING:
                lines = this.getLinesSpectatorWaiting(heat, spectator);
                break;
            case RACING:
                lines = this.getLinesSpectatorRacing(heat, spectator);
                break;
            case FINISHED:
                lines = this.getLinesSpectatorFinished(heat, spectator);
                break; // Adicionado break por segurança
        }
        board.updateLines(lines);
    }

    private List<String> getLinesPractice(Heats heat, Driver driver, Player viewer) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_track", "{track}", heat.getTrackNameWS()));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_lap_f", "{current}", String.valueOf(driver.getLapCount() + 1), "{total}", "-"));
        Lap bestLap = driver.getFastestLap();
        String bestTimeStr = bestLap != null ? this.formatTime(bestLap.getLapTime()) : "\u00a7c--:--.---";
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_best", "{time}", bestTimeStr));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_drivers", "{drivers}", String.valueOf(heat.getDriverCount())));
        lines.add("\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        lines.add("\u00a7ewolfnetwork.com.br");
        return lines;
    }

    private List<String> getLinesSpectatorPractice(Heats heat, Player viewer) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_track", "{track}", heat.getTrackNameWS()));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_header_classification", new String[0]));
        List sortedByBestLap = heat.getDrivers().values().stream().filter(d -> d.getFastestLap() != null).sorted(Comparator.comparingLong(d -> d.getFastestLap().getLapTime())).limit(5L).toList();
        if (sortedByBestLap.isEmpty()) {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_waiting_times", new String[0]));
        } else {
            for (int i = 0; i < sortedByBestLap.size(); ++i) {
                Driver d2 = (Driver)sortedByBestLap.get(i);
                String time = this.formatTime(d2.getFastestLap().getLapTime());
                lines.add(String.format("\u00a7e%d. \u00a7f%s \u00a77- \u00a7b%s", i + 1, Bukkit.getOfflinePlayer((UUID)d2.getUuid()).getName(), time));
            }
        }
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_drivers", "{drivers}", String.valueOf(heat.getDriverCount())));
        lines.add("\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        lines.add("\u00a7ewolfnetwork.com.br");
        return lines;
    }

    private List<String> getLinesSpectatorWaiting(Heats heat, Player viewer) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_waiting", new String[0]));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_drivers", "{drivers}", String.valueOf(heat.getDriverCount())));
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_laps", "{laps}", String.valueOf(heat.getTotalLaps())));
        if (heat.getTotalPits() > 0) {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_pits", "{pits}", String.valueOf(heat.getTotalPits())));
        }
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_waiting_start", new String[0]));
        lines.add("\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        lines.add("\u00a7ewolfnetwork.com.br");
        return lines;
    }

    private List<String> getLinesSpectatorRacing(Heats heat, Player viewer) {
        ArrayList<String> lines = new ArrayList<String>();
        String heatName = heat.getName();
        String eventName = "";
        if (heat.getRound() != null && heat.getRound().getEvent() != null && (eventName = heat.getRound().getEvent().getDisplayName()).length() > 12) {
            eventName = eventName.substring(0, 12);
        }
        lines.add("\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        lines.add("\u00a7e" + heatName + (String)(eventName.isEmpty() ? "" : " \u00a78| \u00a77" + eventName));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_header_classification", new String[0]));
        List sortedDrivers = heat.getDrivers().values().stream().filter(d -> !d.isDnf()).sorted(Comparator.comparingInt(Driver::getPosition)).limit(10L).toList();
        for (int i = 0; i < sortedDrivers.size(); ++i) {
            Driver d2 = (Driver)sortedDrivers.get(i);
            lines.add(this.formatDriverLineSpectator(d2, i + 1, heat));
        }
        lines.add("\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        lines.add("\u00a7ewolfnetwork.com.br");
        return lines;
    }

    private List<String> getLinesSpectatorFinished(Heats heat, Player viewer) {
        List<String> lines = new ArrayList<>();
        lines.add("§7━━━━━━━━━━━━━━━");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_finished"));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_header_final"));

        // CORREÇÃO: Definindo explicitamente List<Driver> para o compilador identificar os métodos do objeto
        List<Driver> finishedDrivers = heat.getDrivers().values().stream()
                .filter(Driver::isFinished)
                .sorted(Comparator.comparingInt(Driver::getPosition))
                .limit(10)
                .collect(Collectors.toList()); // Coleta compatível com a maioria das versões do Java

        for (Driver d : finishedDrivers) {
            // Removido o cast (UUID) desnecessário, d.getUuid() já retorna UUID
            Player p = Bukkit.getPlayer(d.getUuid());
            if (p == null) continue;

            String name = p.getName();
            if (name.length() > 10) {
                name = name.substring(0, 10);
            }

            // Adicionando a linha formatada com a posição, nome e tempo total
            lines.add(String.format("§7%d. §f%s §8%s", d.getPosition(), name, this.formatTime(d.getTotalTime())));
        }

        lines.add("§7━━━━━━━━━━━━━━━");
        lines.add("§ewolfnetwork.com.br");
        return lines;
    }

    private String formatDriverLineSpectator(Driver d, int position, Heats heat) {
        Player p = Bukkit.getPlayer((UUID)d.getUuid());
        if (p == null) {
            return String.format("\u00a77%d. \u00a78[Offline]", position);
        }
        String posColor = position == 1 ? "\u00a7a" : (position == 2 ? "\u00a7e" : (position == 3 ? "\u00a76" : "\u00a77"));
        String name = p.getName();
        if (name.length() > 10) {
            name = name.substring(0, 10);
        }
        int currentLap = d.getCurrentLap() == null ? 0 : d.getLapCount() + 1;
        Object pitInfo = "";
        if (heat.getTotalPits() > 0) {
            int required;
            int pits = d.getPitstops();
            pitInfo = pits < (required = heat.getTotalPits().intValue()) ? " \u00a7c\u25cf" + pits : " \u00a7a\u25cf" + pits;
        }
        return String.format("%s%d. \u00a7f%s \u00a77V%d%s", posColor, position, name, currentLap, pitInfo);
    }

    private String getTitleForState(HeatState state, Player viewer) {
        return switch (state) {
            default -> throw new MatchException(null, null);
            case HeatState.SETUP, HeatState.IDLE -> this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_waiting", new String[0]);
            case HeatState.PRACTICE -> this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_practice", new String[0]);
            case HeatState.QUALIFYING -> this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_qualifying", new String[0]);
            case HeatState.LOADED -> this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_waiting", new String[0]);
            case HeatState.STARTING -> this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_starting", new String[0]);
            case HeatState.RACING -> this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_race", new String[0]);
            case HeatState.FINISHED -> this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_finished", new String[0]);
        };
    }

    private List<String> getLinesSetup(Heats heat, Player viewer) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
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
        lines.add("\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        lines.add("\u00a7ewolfnetwork.com.br");
        return lines;
    }

    private List<String> getLinesLoaded(Heats heat, Driver driver, Player viewer) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        if (heat.isLonely()) {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_qualifying", new String[0]));
            lines.add("");
            lines.add("\u00a77Modo: \u00a7fIndividual");
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_laps", "{laps}", String.valueOf(heat.getTotalLaps())));
            lines.add("");
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_waiting", new String[0]) + "...");
        } else {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_grid_pos", "{pos}", ""));
            lines.add("");
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_position_f", "{pos}", String.valueOf(driver.getStartPosition())));
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_laps", "{laps}", String.valueOf(heat.getTotalLaps())));
            if (heat.getTotalPits() > 0) {
                lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_pits", "{pits}", String.valueOf(heat.getTotalPits())));
            }
            lines.add("");
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_prepare_start", new String[0]));
        }
        lines.add("\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        lines.add("\u00a7ewolfnetwork.com.br");
        return lines;
    }

    private List<String> getLinesStarting(Heats heat, Driver driver, Player viewer) {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        if (heat.isLonely()) {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_qualifying", new String[0]));
            lines.add("");
            lines.add("\u00a77Partida: \u00a7fSpawn");
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_laps", "{laps}", String.valueOf(heat.getTotalLaps())));
            lines.add("");
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_state_preparing", new String[0]));
        } else {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_lights_out", new String[0]));
            lines.add("");
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_position_f", "{pos}", String.valueOf(driver.getStartPosition())));
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_laps", "{laps}", String.valueOf(heat.getTotalLaps())));
            lines.add("");
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_lights_out", new String[0]));
        }
        lines.add("\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        lines.add("\u00a7ewolfnetwork.com.br");
        return lines;
    }

    private List<String> getLinesRacing(Heats heat, Driver driver, Player viewer) {
        if (driver.isFinished()) {
            return this.getLinesFinished(heat, driver, viewer);
        }
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        int positionVal = driver.getPosition();
        String posColor2 = this.getPositionColor(positionVal);
        int totalLaps = heat.getTotalLaps();
        int lapCount = driver.getCurrentLap() == null ? 0 : Math.min(totalLaps, driver.getLapCount() + 1);
        lines.add("\u00a7eP\u00a7f" + positionVal + " \u00a78| " + this.plugin.getTranslationUtil().getTranslated(viewer, "actionbar_practice_lap", "{lap}", lapCount + "\u00a77/\u00a7f" + totalLaps));
        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_header_classification", new String[0]));
        List sortedDrivers = heat.getDrivers().values().stream().sorted(Comparator.comparingInt(Driver::getPosition)).collect(Collectors.toList());
        int totalDrivers = sortedDrivers.size();
        int playerPosition = driver.getPosition();
        int POSITIONS_ABOVE = 2;
        int POSITIONS_BELOW = 2;
        int MAX_VISIBLE = 5;
        if (totalDrivers <= 5) {
            for (int i = 0; i < sortedDrivers.size(); ++i) {
                Driver d = (Driver)sortedDrivers.get(i);
                lines.add(this.formatDriverLine(d, driver.getUuid(), i + 1, heat));
            }
        } else {
            boolean showingP1;
            int startPos = Math.max(1, playerPosition - 2);
            int endPos = Math.min(totalDrivers, playerPosition + 2);
            if (endPos - startPos + 1 < 5) {
                if (startPos == 1) {
                    endPos = Math.min(totalDrivers, startPos + 5 - 1);
                } else {
                    startPos = Math.max(1, endPos - 5 + 1);
                }
            }
            boolean bl = showingP1 = startPos == 1;
            if (!showingP1) {
                Driver leader = (Driver)sortedDrivers.get(0);
                lines.add(this.formatDriverLine(leader, driver.getUuid(), 1, heat));
                lines.add("\u00a78  ...");
            }
            for (int pos = startPos; pos <= endPos; ++pos) {
                Driver d = (Driver)sortedDrivers.get(pos - 1);
                lines.add(this.formatDriverLine(d, driver.getUuid(), pos, heat));
            }
            if (endPos < totalDrivers) {
                lines.add("\u00a78  ...");
            }
        }
        lines.add("");
        if (driver.getFastestLap() != null) {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_best", "{time}", this.formatTime(driver.getFastestLap().getLapTime())));
        }
        if (heat.getTotalPits() > 0) {
            int pitsRemaining = heat.getTotalPits() - driver.getPitstops();
            if (pitsRemaining > 0) {
                lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_pits_remaining", "{pits}", String.valueOf(pitsRemaining)));
            } else {
                lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_pits_complete", new String[0]));
            }
        }
        lines.add("\u00a77\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501");
        lines.add("\u00a7ewolfnetwork.com.br");
        return lines;
    }

    private String formatDriverLine(Driver d, UUID currentPlayerUuid, int position, Heats heat) {
        Player p = Bukkit.getPlayer(d.getUuid());
        if (p == null) {
            return String.format("§7%d. §8[Offline]", position);
        }

        String posColor = position == 1 ? "§a" : (position == 2 ? "§e" : (position == 3 ? "§6" : "§7"));

        String name = p.getName();
        if (name.length() > 10) {
            name = name.substring(0, 10);
        }

        // Correção lógica: Se d.getCurrentLap() for null, usa 0, senão d.getLapCount() + 1
        int currentLap = (d.getCurrentLap() == null) ? 0 : d.getLapCount() + 1;

        String pitInfo = ""; // Mudado de Object para String
        if (heat.getTotalPits() > 0) {
            int required = heat.getTotalPits();
            int pits = d.getPitstops();
            // Símbolo ● (u25cf) para indicar pits
            pitInfo = (pits < required) ? " §c●" + pits : " §a●" + pits;
        }

        String line = String.format("%s%d. §f%s §7V%d%s", posColor, position, name, currentLap, pitInfo);

        if (d.getUuid().equals(currentPlayerUuid)) {
            line = "§l► " + line; // Removido cast (String) desnecessário
        }

        return line;
    }

    private List<String> getLinesFinished(Heats heat, Driver driver, Player viewer) {
        List<String> lines = new ArrayList<>();
        lines.add("§7━━━━━━━━━━━━━━━");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_title_finished"));
        lines.add("");

        if (driver.isFinished()) {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_position_f", "{pos}", "§f#" + driver.getPosition()));
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_time", "{time}", this.formatTime(driver.getTotalTime())));

            if (driver.getFastestLap() != null) {
                lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_best", "{time}", this.formatTime(driver.getFastestLap().getLapTime())));
            }

            if (heat.getTotalPits() > 0) {
                lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_label_pits", "{pits}", driver.getPitstops() + "§7/§f" + heat.getTotalPits()));
            }
        } else if (driver.isDnf()) {
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_status_dnf"));
            lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_status_dnf_desc"));
        }

        lines.add("");
        lines.add(this.plugin.getTranslationUtil().getTranslated(viewer, "scoreboard_header_final"));

        // CORREÇÃO: Especificando o tipo <Driver> no Stream
        List<Driver> finishedDrivers = heat.getDrivers().values().stream()
                .filter(Driver::isFinished)
                .sorted(Comparator.comparingInt(Driver::getPosition))
                .limit(5)
                .collect(Collectors.toList());

        for (Driver d : finishedDrivers) {
            Player p = Bukkit.getPlayer(d.getUuid());
            if (p == null) continue;

            String name = p.getName();
            if (name.length() > 10) {
                name = name.substring(0, 10);
            }
            lines.add(String.format("§7%d. §f%s §8%s", d.getPosition(), name, this.formatTime(d.getTotalTime())));
        }

        lines.add("§7━━━━━━━━━━━━━━━");
        lines.add("§ewolfnetwork.com.br");
        return lines;
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

    private String getPositionColor(int position) {
        return switch (position) {
            case 1 -> "\u00a7a";
            case 2 -> "\u00a7e";
            case 3 -> "\u00a76";
            default -> "\u00a77";
        };
    }
}
