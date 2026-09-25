package dev.EfraGroup.formulaRacing.League;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Pontuation.PointsConfig;
import dev.EfraGroup.formulaRacing.League.scoring.ScoringRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LeagueJsonStore {

    private final FormulaRacing plugin;
    private final Gson gson;
    private final Path directory;
    private final Map<Integer, List<LeagueEventResult>> resultsByLeague;
    private final Map<Integer, Map<UUID, Integer>> adjustmentsByLeague;
    private final Map<Integer, List<LeagueStanding>> driverStandingsByLeague;
    private final Map<Integer, List<LeagueTeamStanding>> teamStandingsByLeague;

    public LeagueJsonStore(FormulaRacing plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        this.directory = plugin.getDataFolder().toPath().resolve("leagues");
        this.resultsByLeague = new LinkedHashMap<>();
        this.adjustmentsByLeague = new LinkedHashMap<>();
        this.driverStandingsByLeague = new LinkedHashMap<>();
        this.teamStandingsByLeague = new LinkedHashMap<>();
    }

    public synchronized List<League> loadLeagues() {
        List<League> leagues = new ArrayList<>();
        this.resultsByLeague.clear();
        this.adjustmentsByLeague.clear();
        this.driverStandingsByLeague.clear();
        this.teamStandingsByLeague.clear();
        try {
            Files.createDirectories(this.directory);
            try (var files = Files.list(this.directory)) {
                List<Path> paths = files
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
                for (Path path : paths) {
                    try {
                        LeagueDocument document = this.gson.fromJson(
                            Files.readString(path, StandardCharsets.UTF_8),
                            LeagueDocument.class
                        );
                        if (document == null) {
                            continue;
                        }
                        League league = document.toLeague();
                        leagues.add(league);
                        this.resultsByLeague.put(league.getId(), document.eventResults);
                        this.adjustmentsByLeague.put(league.getId(), document.adjustments);
                        this.driverStandingsByLeague.put(league.getId(), document.driverStandings);
                        this.teamStandingsByLeague.put(league.getId(), document.teamStandings);
                    } catch (JsonSyntaxException | IllegalArgumentException exception) {
                        this.plugin.getDebugManager().logDatabaseOperation(
                            "[LeagueJSON] Ignorando arquivo inválido " + path.getFileName() + ": " + exception.getMessage()
                        );
                    }
                }
            }
        } catch (IOException exception) {
            this.plugin.getDebugManager().logDatabaseOperation(
                "[LeagueJSON] Erro ao carregar ligas: " + exception.getMessage()
            );
        }
        return leagues;
    }

    public synchronized League createLeague(UUID creatorUUID, String name) throws SQLException {
        int nextId = 1;
        List<League> existing = this.loadLeagues();
        for (League league : existing) {
            nextId = Math.max(nextId, league.getId() + 1);
        }
        League league = new League(nextId, creatorUUID, name, LeagueStatus.SETUP);
        this.resultsByLeague.put(nextId, new ArrayList<>());
        this.adjustmentsByLeague.put(nextId, new LinkedHashMap<>());
        this.driverStandingsByLeague.put(nextId, new ArrayList<>());
        this.teamStandingsByLeague.put(nextId, new ArrayList<>());
        this.saveLeague(league);
        return league;
    }

    public synchronized void saveLeague(League league) throws SQLException {
        try {
            Files.createDirectories(this.directory);
            Path target = this.directory.resolve("league-" + league.getId() + ".json");
            Path temporary = this.directory.resolve("league-" + league.getId() + ".json.tmp");
            Files.writeString(temporary, this.gson.toJson(this.toDocument(league)), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new SQLException("Não foi possível salvar a liga em JSON", exception);
        }
    }

    public synchronized LeagueTeam addTeam(League league, String teamName) throws SQLException {
        int nextId = 1;
        for (LeagueTeam team : league.getTeams().values()) {
            nextId = Math.max(nextId, team.getId() + 1);
        }
        LeagueTeam team = new LeagueTeam(nextId, league.getId(), teamName);
        league.getTeams().put(nextId, team);
        this.saveLeague(league);
        return team;
    }

    public synchronized LeagueDriver addDriver(League league, UUID playerUUID, Integer teamId) throws SQLException {
        int nextId = 1;
        for (LeagueDriver driver : league.getDrivers().values()) {
            nextId = Math.max(nextId, driver.getId() + 1);
        }
        LeagueDriver driver = new LeagueDriver(nextId, league.getId(), playerUUID, teamId);
        league.getDrivers().put(playerUUID, driver);
        this.saveLeague(league);
        return driver;
    }

    public synchronized void linkEvent(League league, int eventId, int roundNumber) throws SQLException {
        LeagueCalendarEntry entry = league.getCalendar().computeIfAbsent(eventId, LeagueCalendarEntry::new);
        entry.setRoundNumber(roundNumber);
        this.saveLeague(league);
    }

    public synchronized void unlinkEvent(League league, int eventId) throws SQLException {
        league.getCalendar().remove(eventId);
        this.saveLeague(league);
    }

    public synchronized boolean isEventLinkedToLeague(League league, int eventId) {
        return league.getCalendar().containsKey(eventId);
    }

    public synchronized void saveLeagueConfig(League league) throws SQLException {
        this.saveLeague(league);
    }

    public synchronized void saveCategory(League league, LeagueCategory category) throws SQLException {
        if (category.getId() <= 0) {
            int nextId = 1;
            for (LeagueCategory existing : league.getCategories().values()) {
                nextId = Math.max(nextId, existing.getId() + 1);
            }
            category.setId(nextId);
        }
        league.getCategories().put(category.getName().toLowerCase(), category);
        this.saveLeague(league);
    }

    public synchronized void removeCategory(League league, String name) throws SQLException {
        league.getCategories().remove(name.toLowerCase());
        this.saveLeague(league);
    }

    public synchronized void setEventMeta(
        League league,
        int eventId,
        String categoryName,
        Integer pinnedHeatId
    ) throws SQLException {
        LeagueCalendarEntry entry = league.getCalendar().computeIfAbsent(eventId, LeagueCalendarEntry::new);
        if (categoryName != null) {
            entry.setCategoryName(categoryName);
        }
        if (pinnedHeatId != null) {
            entry.setPinnedHeatId(pinnedHeatId);
        }
        this.saveLeague(league);
    }

    public synchronized boolean storeEventResults(
        League league,
        int eventId,
        List<Driver> results,
        String categoryName,
        Integer pinnedHeatId
    ) throws SQLException {
        if (league == null || results == null || results.isEmpty()) {
            return false;
        }
        String categoryKey = categoryName == null || categoryName.isBlank()
            ? null
            : categoryName.toLowerCase();
        List<LeagueEventResult> stored = this.resultsByLeague.computeIfAbsent(league.getId(), ignored -> new ArrayList<>());
        stored.removeIf(result ->
            result.eventId == eventId && java.util.Objects.equals(result.categoryName, categoryKey)
        );
        for (int index = 0; index < results.size(); index++) {
            Driver driver = results.get(index);
            LeagueDriver leagueDriver = league.getDrivers().get(driver.getUuid());
            Integer teamId = leagueDriver == null ? null : leagueDriver.getTeamId();
            stored.add(new LeagueEventResult(
                eventId,
                categoryKey,
                index + 1,
                driver.getUuid(),
                teamId,
                this.computePoints(league, categoryKey, index + 1, results.size()),
                pinnedHeatId
            ));
        }
        LeagueCalendarEntry entry = league.getCalendar().computeIfAbsent(eventId, LeagueCalendarEntry::new);
        entry.setPointsApplied(true);
        this.recalculateStandings(league);
        return true;
    }

    private int computePoints(League league, String categoryKey, int position, int driverCount) {
        LeagueCategory category = categoryKey == null ? null : league.getCategory(categoryKey);
        String system = category == null ? league.getScoringSystem() : category.getScoringSystem();
        PointsConfig customScale = category == null ? league.getCustomScale() : category.getCustomScale();
        if (customScale != null) {
            return customScale.getRacePoints().getOrDefault(position, 0);
        }
        return ScoringRegistry.get(system).pointsForPosition(position, driverCount);
    }

    public synchronized void recalculateStandings(League league) throws SQLException {
        Map<UUID, List<StandingsUpdater.DriverEventResult>> driverResults = new LinkedHashMap<>();
        Map<UUID, Integer> driverTeams = new LinkedHashMap<>();
        Map<UUID, Integer> eventsByDriver = new LinkedHashMap<>();
        Map<UUID, Integer> wins = new LinkedHashMap<>();
        Map<UUID, Integer> podiums = new LinkedHashMap<>();
        for (LeagueEventResult result : this.resultsByLeague.getOrDefault(league.getId(), List.of())) {
            driverResults
                .computeIfAbsent(result.playerUUID, ignored -> new ArrayList<>())
                .add(new StandingsUpdater.DriverEventResult(
                    String.valueOf(result.eventId),
                    result.categoryName,
                    result.points
                ));
            if (result.teamId != null) {
                driverTeams.put(result.playerUUID, result.teamId);
            }
            eventsByDriver.merge(result.playerUUID, 1, Integer::sum);
            if (result.position == 1) {
                wins.merge(result.playerUUID, 1, Integer::sum);
            }
            if (result.position >= 1 && result.position <= 3) {
                podiums.merge(result.playerUUID, 1, Integer::sum);
            }
        }
        StandingsUpdater.CalculationResult calculation = StandingsUpdater.recalculate(league, driverResults, driverTeams);
        Map<UUID, Integer> adjustments = this.adjustmentsByLeague.getOrDefault(league.getId(), Map.of());

        List<LeagueStanding> driverStandings = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : calculation.driverPoints.entrySet()) {
            UUID uuid = entry.getKey();
            driverStandings.add(new LeagueStanding(
                uuid,
                Math.max(0, entry.getValue() + adjustments.getOrDefault(uuid, 0)),
                wins.getOrDefault(uuid, 0),
                podiums.getOrDefault(uuid, 0),
                eventsByDriver.getOrDefault(uuid, 0)
            ));
        }
        for (Map.Entry<UUID, Integer> adjustment : adjustments.entrySet()) {
            if (driverStandings.stream().noneMatch(standing -> standing.getPlayerUUID().equals(adjustment.getKey()))) {
                driverStandings.add(new LeagueStanding(
                    adjustment.getKey(),
                    Math.max(0, adjustment.getValue()),
                    0,
                    0,
                    0
                ));
            }
        }
        driverStandings.sort(Comparator
            .comparingInt(LeagueStanding::getPoints).reversed()
            .thenComparing(Comparator.comparingInt(LeagueStanding::getWins).reversed())
            .thenComparing(Comparator.comparingInt(LeagueStanding::getPodiums).reversed()));

        Map<Integer, String> teamNames = new LinkedHashMap<>();
        for (LeagueTeam team : league.getTeams().values()) {
            teamNames.put(team.getId(), team.getName());
        }
        List<LeagueTeamStanding> teamStandings = new ArrayList<>();
        for (Map.Entry<Integer, Integer> entry : calculation.teamPoints.entrySet()) {
            String teamName = teamNames.get(entry.getKey());
            if (teamName != null) {
                teamStandings.add(new LeagueTeamStanding(entry.getKey(), teamName, entry.getValue(), 0, 0));
            }
        }
        teamStandings.sort(Comparator.comparingInt(LeagueTeamStanding::getPoints).reversed());

        this.driverStandingsByLeague.put(league.getId(), driverStandings);
        this.teamStandingsByLeague.put(league.getId(), teamStandings);
        this.saveLeague(league);
    }

    public synchronized void adjustDriverPoints(
        League league,
        UUID playerUUID,
        int delta,
        String source
    ) throws SQLException {
        Map<UUID, Integer> adjustments = this.adjustmentsByLeague.computeIfAbsent(
            league.getId(),
            ignored -> new LinkedHashMap<>()
        );
        adjustments.merge(playerUUID, delta, Integer::sum);
        this.recalculateStandings(league);
    }

    public synchronized void transferDriverPoints(
        League league,
        UUID fromUUID,
        UUID toUUID,
        int amount
    ) throws SQLException {
        Map<UUID, Integer> adjustments = this.adjustmentsByLeague.computeIfAbsent(
            league.getId(),
            ignored -> new LinkedHashMap<>()
        );
        adjustments.merge(fromUUID, -amount, Integer::sum);
        adjustments.merge(toUUID, amount, Integer::sum);
        this.recalculateStandings(league);
    }

    public synchronized List<LeagueStanding> loadDriverStandings(int leagueId) {
        return new ArrayList<>(this.driverStandingsByLeague.getOrDefault(leagueId, List.of()));
    }

    public synchronized List<LeagueTeamStanding> loadTeamStandings(int leagueId) {
        return new ArrayList<>(this.teamStandingsByLeague.getOrDefault(leagueId, List.of()));
    }

    public synchronized void deleteLeague(League league) throws SQLException {
        try {
            Files.deleteIfExists(this.directory.resolve("league-" + league.getId() + ".json"));
        } catch (IOException exception) {
            throw new SQLException("Não foi possível excluir a liga em JSON", exception);
        }
        this.resultsByLeague.remove(league.getId());
        this.adjustmentsByLeague.remove(league.getId());
        this.driverStandingsByLeague.remove(league.getId());
        this.teamStandingsByLeague.remove(league.getId());
    }

    private LeagueDocument toDocument(League league) {
        return new LeagueDocument(
            league,
            new ArrayList<>(this.resultsByLeague.getOrDefault(league.getId(), List.of())),
            new LinkedHashMap<>(this.adjustmentsByLeague.getOrDefault(league.getId(), Map.of())),
            new ArrayList<>(this.driverStandingsByLeague.getOrDefault(league.getId(), List.of())),
            new ArrayList<>(this.teamStandingsByLeague.getOrDefault(league.getId(), List.of()))
        );
    }

    private static final class LeagueDocument {
        private int schemaVersion = 1;
        private int id;
        private String creatorUuid;
        private String name;
        private String status;
        private String scoringSystem = "BASIC";
        private String teamMode = "MAIN_RESERVE";
        private int mulliganCount;
        private TeamConfig teamConfig = new TeamConfig();
        private PointsConfig customScale;
        private List<LeagueTeam> teams = new ArrayList<>();
        private List<LeagueDriver> drivers = new ArrayList<>();
        private List<LeagueCategory> categories = new ArrayList<>();
        private List<LeagueCalendarEntry> events = new ArrayList<>();
        private List<LeagueEventResult> eventResults = new ArrayList<>();
        private Map<UUID, Integer> adjustments = new LinkedHashMap<>();
        private List<LeagueStanding> driverStandings = new ArrayList<>();
        private List<LeagueTeamStanding> teamStandings = new ArrayList<>();

        private LeagueDocument() {}

        private LeagueDocument(
            League league,
            List<LeagueEventResult> eventResults,
            Map<UUID, Integer> adjustments,
            List<LeagueStanding> driverStandings,
            List<LeagueTeamStanding> teamStandings
        ) {
            this.id = league.getId();
            this.creatorUuid = league.getCreatorUUID().toString();
            this.name = league.getName();
            this.status = league.getStatus().name();
            this.scoringSystem = league.getScoringSystem();
            this.teamMode = league.getTeamMode().name();
            this.mulliganCount = league.getMulliganCount();
            this.teamConfig = league.getTeamConfig();
            this.customScale = league.getCustomScale();
            this.teams = new ArrayList<>(league.getTeams().values());
            this.drivers = new ArrayList<>(league.getDrivers().values());
            this.categories = new ArrayList<>(league.getCategories().values());
            this.events = new ArrayList<>(league.getCalendar().values());
            this.eventResults = eventResults;
            this.adjustments = adjustments;
            this.driverStandings = driverStandings;
            this.teamStandings = teamStandings;
        }

        private League toLeague() {
            League league = new League(
                this.id,
                UUID.fromString(this.creatorUuid),
                this.name,
                LeagueStatus.valueOf(this.status)
            );
            league.setScoringSystem(this.scoringSystem);
            league.setTeamMode(TeamMode.valueOf(this.teamMode));
            league.setMulliganCount(this.mulliganCount);
            if (this.teamConfig != null) {
                league.setTeamConfig(this.teamConfig);
            }
            league.setCustomScale(this.customScale);
            for (LeagueTeam team : this.teams) {
                league.getTeams().put(team.getId(), team);
            }
            for (LeagueDriver driver : this.drivers) {
                league.getDrivers().put(driver.getPlayerUUID(), driver);
            }
            for (LeagueCategory category : this.categories) {
                league.getCategories().put(category.getName().toLowerCase(), category);
            }
            for (LeagueCalendarEntry event : this.events) {
                league.getCalendar().put(event.getEventId(), event);
            }
            return league;
        }
    }

    private static final class LeagueEventResult {
        private int eventId;
        private String categoryName;
        private int position;
        private UUID playerUUID;
        private Integer teamId;
        private int points;
        private Integer heatId;

        private LeagueEventResult() {}

        private LeagueEventResult(
            int eventId,
            String categoryName,
            int position,
            UUID playerUUID,
            Integer teamId,
            int points,
            Integer heatId
        ) {
            this.eventId = eventId;
            this.categoryName = categoryName;
            this.position = position;
            this.playerUUID = playerUUID;
            this.teamId = teamId;
            this.points = points;
            this.heatId = heatId;
        }
    }
}
