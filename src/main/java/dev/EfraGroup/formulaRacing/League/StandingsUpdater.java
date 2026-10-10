package dev.EfraGroup.formulaRacing.League;

import dev.EfraGroup.formulaRacing.League.scoring.ScoringRegistry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class StandingsUpdater {

    public record DriverEventResult(
        String eventId,
        String categoryName,
        int position,
        UUID playerUUID,
        int points
    ) {}

    public record CalculationResult(
        Map<UUID, Integer> driverPoints,
        Map<UUID, List<DriverEventResult>> driverHistory,
        Map<UUID, List<DriverEventResult>> driverMulliganed,
        Map<Integer, Integer> teamPoints,
        Map<Integer, List<TeamEventResult>> teamHistory,
        Map<Integer, List<TeamEventResult>> teamMulliganed
    ) {
        public record TeamEventResult(String eventId, String categoryName, int points) {}
    }

    public static CalculationResult recalculate(
        League league,
        Map<UUID, List<DriverEventResult>> driverResults,
        Map<UUID, Integer> driverTeam
    ) {
        Map<UUID, Integer> driverPoints = new LinkedHashMap<>();
        Map<UUID, List<DriverEventResult>> driverHistory = new LinkedHashMap<>();
        Map<UUID, List<DriverEventResult>> driverMulliganed = new LinkedHashMap<>();

        if (league.isDriverStandingsEnabled()) {
            Map<String, List<DriverEventResult>> leagueAll = new LinkedHashMap<>();
            for (Map.Entry<UUID, List<DriverEventResult>> entry : driverResults.entrySet()) {
                for (DriverEventResult der : entry.getValue()) {
                    leagueAll
                        .computeIfAbsent(der.categoryName() == null ? "" : der.categoryName(), k -> new ArrayList<>())
                        .add(der);
                }
            }
            int leagueMulligans = league.getMulliganCount();
            for (Map.Entry<UUID, List<DriverEventResult>> entry : driverResults.entrySet()) {
                UUID uuid = entry.getKey();
                List<DriverEventResult> results = entry.getValue();
                Map<String, List<DriverEventResult>> pools = new LinkedHashMap<>();
                for (DriverEventResult der : results) {
                    String pool = der.categoryName() == null ? "" : der.categoryName();
                    pools.computeIfAbsent(pool, k -> new ArrayList<>()).add(der);
                }
                List<DriverEventResult> scored = new ArrayList<>();
                List<DriverEventResult> dropped = new ArrayList<>();
                for (List<DriverEventResult> poolResults : pools.values()) {
                    int poolMulligans = poolMulligans(league, poolResults);
                    int effectiveMulligans = leagueMulligans > 0
                        ? Math.max(leagueMulligans, poolMulligans)
                        : poolMulligans;
                    splitByMulligan(poolResults, effectiveMulligans, scored, dropped);
                }
                int total = 0;
                for (DriverEventResult der : scored) {
                    total += der.points();
                }
                driverPoints.put(uuid, total);
                driverHistory.put(uuid, scored);
                if (!dropped.isEmpty()) {
                    driverMulliganed.put(uuid, dropped);
                }
            }
        }

        Map<Integer, Integer> teamPoints = new LinkedHashMap<>();
        Map<Integer, List<CalculationResult.TeamEventResult>> teamHistory = new LinkedHashMap<>();
        Map<Integer, List<CalculationResult.TeamEventResult>> teamMulliganed = new LinkedHashMap<>();

        if (league.isTeamStandingsEnabled()) {
            Map<String, String> eventCategory = new LinkedHashMap<>();
            Map<String, List<DriverEventResult>> byEvent = new LinkedHashMap<>();
            for (List<DriverEventResult> results : driverResults.values()) {
                for (DriverEventResult der : results) {
                    byEvent.computeIfAbsent(der.eventId(), k -> new ArrayList<>()).add(der);
                    eventCategory.putIfAbsent(der.eventId(), der.categoryName());
                }
            }

            Map<Integer, Map<String, Integer>> teamEventPoints = new LinkedHashMap<>();
            for (LeagueTeam team : league.getTeamsView()) {
                Map<String, Integer> eventPoints = new LinkedHashMap<>();
                for (Map.Entry<String, List<DriverEventResult>> eventEntry : byEvent.entrySet()) {
                    int points = teamEventPoints(league, team, eventEntry.getValue());
                    if (points > 0) {
                        eventPoints.put(eventEntry.getKey(), points);
                    }
                }
                teamEventPoints.put(team.getId(), eventPoints);
            }

            int leagueMulligans = league.getMulliganCount();
            for (Map.Entry<Integer, Map<String, Integer>> entry : teamEventPoints.entrySet()) {
                int teamId = entry.getKey();
                Map<String, List<CalculationResult.TeamEventResult>> pools = new LinkedHashMap<>();
                for (Map.Entry<String, Integer> eventEntry : entry.getValue().entrySet()) {
                    String category = eventCategory.get(eventEntry.getKey());
                    String pool = category == null ? "" : category;
                    pools
                        .computeIfAbsent(pool, k -> new ArrayList<>())
                        .add(new CalculationResult.TeamEventResult(
                            eventEntry.getKey(),
                            category,
                            eventEntry.getValue()
                        ));
                }
                List<CalculationResult.TeamEventResult> scored = new ArrayList<>();
                List<CalculationResult.TeamEventResult> dropped = new ArrayList<>();
                for (List<CalculationResult.TeamEventResult> poolResults : pools.values()) {
                    int poolMulligans = poolTeamMulligans(league, poolResults);
                    int effectiveMulligans = leagueMulligans > 0
                        ? Math.max(leagueMulligans, poolMulligans)
                        : poolMulligans;
                    splitTeamByMulligan(poolResults, effectiveMulligans, scored, dropped);
                }
                int total = 0;
                for (CalculationResult.TeamEventResult ter : scored) {
                    total += ter.points();
                }
                if (total > 0) {
                    teamPoints.put(teamId, total);
                }
                teamHistory.put(teamId, scored);
                if (!dropped.isEmpty()) {
                    teamMulliganed.put(teamId, dropped);
                }
            }
        }

        return new CalculationResult(
            driverPoints,
            driverHistory,
            driverMulliganed,
            teamPoints,
            teamHistory,
            teamMulliganed
        );
    }

    private static int teamEventPoints(League league, LeagueTeam team, List<DriverEventResult> eventResults) {
        int counted = team.effectiveCountedScorers(league);
        switch (league.getTeamMode()) {
            case PRIORITY: {
                int total = 0;
                int scored = 0;
                for (UUID uuid : team.getPriorityDrivers()) {
                    if (counted > 0 && scored >= counted) break;
                    DriverEventResult result = findResult(eventResults, uuid);
                    if (result != null) {
                        total += result.points();
                        scored++;
                    }
                }
                return total;
            }
            case HIGHEST: {
                List<DriverEventResult> teamResults = new ArrayList<>();
                for (DriverEventResult result : eventResults) {
                    if (team.isMember(result.playerUUID())) {
                        teamResults.add(result);
                    }
                }
                teamResults.sort(Comparator.comparingInt(DriverEventResult::position));
                int total = 0;
                for (int i = 0; i < teamResults.size(); i++) {
                    if (counted > 0 && i >= counted) break;
                    total += teamResults.get(i).points();
                }
                return total;
            }
            default:
                int total = 0;
                int missingMains = 0;
                for (UUID uuid : team.getMainDrivers()) {
                    DriverEventResult result = findResult(eventResults, uuid);
                    if (result != null) {
                        total += result.points();
                    } else {
                        missingMains++;
                    }
                }
                if (missingMains > 0) {
                    List<DriverEventResult> reserveResults = new ArrayList<>();
                    for (UUID uuid : team.getReserveDrivers()) {
                        DriverEventResult result = findResult(eventResults, uuid);
                        if (result != null) {
                            reserveResults.add(result);
                        }
                    }
                    reserveResults.sort(Comparator.comparingInt(DriverEventResult::position));
                    for (int i = 0; i < reserveResults.size() && i < missingMains; i++) {
                        total += reserveResults.get(i).points();
                    }
                }
                return total;
        }
    }

    private static DriverEventResult findResult(List<DriverEventResult> results, UUID uuid) {
        for (DriverEventResult result : results) {
            if (uuid.equals(result.playerUUID())) {
                return result;
            }
        }
        return null;
    }

    private static int poolMulligans(League league, List<DriverEventResult> poolResults) {
        for (DriverEventResult der : poolResults) {
            String pool = der.categoryName() == null ? "" : der.categoryName();
            LeagueCategory category = pool.isEmpty() ? null : league.getCategory(pool);
            if (category != null && category.getMulliganCount() > 0) {
                return category.getMulliganCount();
            }
        }
        return 0;
    }

    private static int poolTeamMulligans(League league, List<CalculationResult.TeamEventResult> poolResults) {
        for (CalculationResult.TeamEventResult ter : poolResults) {
            String pool = ter.categoryName() == null ? "" : ter.categoryName();
            LeagueCategory category = pool.isEmpty() ? null : league.getCategory(pool);
            if (category != null && category.getMulliganCount() > 0) {
                return category.getMulliganCount();
            }
        }
        return 0;
    }

    private static void splitByMulligan(
        List<DriverEventResult> sorted,
        int mulliganCount,
        List<DriverEventResult> scored,
        List<DriverEventResult> dropped
    ) {
        List<DriverEventResult> ordered = new ArrayList<>(sorted);
        ordered.sort(Comparator.comparingInt(DriverEventResult::points));
        int dropCount = Math.max(0, Math.min(mulliganCount, Math.max(0, ordered.size() - 1)));
        for (int i = 0; i < ordered.size(); i++) {
            if (i < dropCount) {
                dropped.add(ordered.get(i));
            } else {
                scored.add(ordered.get(i));
            }
        }
    }

    private static void splitTeamByMulligan(
        List<CalculationResult.TeamEventResult> poolResults,
        int mulliganCount,
        List<CalculationResult.TeamEventResult> scored,
        List<CalculationResult.TeamEventResult> dropped
    ) {
        List<CalculationResult.TeamEventResult> ordered = new ArrayList<>(poolResults);
        ordered.sort(Comparator.comparingInt(CalculationResult.TeamEventResult::points));
        int dropCount = Math.max(0, Math.min(mulliganCount, Math.max(0, ordered.size() - 1)));
        for (int i = 0; i < ordered.size(); i++) {
            if (i < dropCount) {
                dropped.add(ordered.get(i));
            } else {
                scored.add(ordered.get(i));
            }
        }
    }

    public static int pointsForPosition(League league, String categoryName, int position, int driverCount) {
        LeagueCategory category = categoryName == null ? null : league.getCategory(categoryName);
        String system = category == null ? league.getScoringSystem() : category.getScoringSystem();
        if ("CUSTOM_SCALE".equalsIgnoreCase(system) || "CUSTOM".equalsIgnoreCase(system)) {
            dev.EfraGroup.formulaRacing.Pontuation.PointsConfig scale =
                category == null ? league.getCustomScale() : category.getCustomScale();
            if (scale != null) {
                Integer points = scale.getRacePoints().get(position);
                if (points != null) {
                    return points;
                }
                return 0;
            }
        }
        return ScoringRegistry.get(system).pointsForPosition(position, driverCount);
    }
}
