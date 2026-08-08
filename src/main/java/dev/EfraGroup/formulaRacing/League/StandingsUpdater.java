package dev.EfraGroup.formulaRacing.League;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StandingsUpdater {

    private StandingsUpdater() {}

    public record DriverEventResult(String eventId, String categoryName, int points) {}

    public static final class CalculationResult {
        public final Map<UUID, Integer> driverPoints = new LinkedHashMap<>();
        public final Map<Integer, Integer> teamPoints = new LinkedHashMap<>();
        public final Map<UUID, List<DriverEventResult>> driverHistory = new LinkedHashMap<>();
        public final Map<UUID, List<String>> driverMulliganed = new LinkedHashMap<>();
    }

    /**
     * Recalculates driver and team standings from per-event results.
     * Applies category-aware mulligans (drop N worst events per category pool).
     *
     * @param league       the league (holds mulligan counts + categories)
     * @param driverResults map of driver UUID -> list of per-event results
     * @param driverTeam    map of driver UUID -> team id (for team aggregation)
     */
    public static CalculationResult recalculate(
            League league,
            Map<UUID, List<DriverEventResult>> driverResults,
            Map<UUID, Integer> driverTeam) {

        CalculationResult result = new CalculationResult();

        // Build per-driver history and compute mulligan-aware totals
        for (Map.Entry<UUID, List<DriverEventResult>> entry : driverResults.entrySet()) {
            UUID uuid = entry.getKey();
            List<DriverEventResult> results = entry.getValue();
            result.driverHistory.computeIfAbsent(uuid, k -> new ArrayList<>()).addAll(results);

            // Group by category pool
            Map<String, Map<String, Integer>> pools = new LinkedHashMap<>();
            for (DriverEventResult der : results) {
                String catKey = (der.categoryName() != null && !der.categoryName().isBlank())
                        ? der.categoryName().toLowerCase() : "__uncategorised__";
                pools.computeIfAbsent(catKey, k -> new LinkedHashMap<>())
                        .merge(der.eventId(), der.points(), Integer::sum);
            }

            int total = 0;
            List<String> mulliganed = new ArrayList<>();
            for (Map.Entry<String, Map<String, Integer>> pool : pools.entrySet()) {
                int mulligan = mulliganForPool(league, pool.getKey());
                List<Map.Entry<String, Integer>> events = new ArrayList<>(pool.getValue().entrySet());
                events.sort(Comparator.comparingInt(Map.Entry::getValue));
                List<String> dropped = new ArrayList<>();
                if (mulligan > 0 && events.size() > mulligan) {
                    for (int i = 0; i < mulligan; i++) {
                        dropped.add(events.get(i).getKey());
                    }
                }
                mulliganed.addAll(dropped);
                for (Map.Entry<String, Integer> ev : events) {
                    if (!dropped.contains(ev.getKey())) {
                        total += ev.getValue();
                    }
                }
            }
            result.driverPoints.put(uuid, total);
            result.driverMulliganed.put(uuid, mulliganed);
        }

        // Team points: sum of driver points per team, using the mulligan-adjusted
        // driver total so dropped events are not counted twice.
        for (Map.Entry<UUID, List<DriverEventResult>> entry : driverResults.entrySet()) {
            UUID uuid = entry.getKey();
            Integer teamId = driverTeam.get(uuid);
            if (teamId == null) continue;
            int sum = result.driverPoints.getOrDefault(uuid, 0);
            result.teamPoints.merge(teamId, sum, Integer::sum);
        }

        return result;
    }

    private static int mulliganForPool(League league, String catKey) {
        if ("__uncategorised__".equals(catKey)) {
            return league.getMulliganCount();
        }
        LeagueCategory cat = league.getCategory(catKey);
        return cat != null ? cat.getMulliganCount() : 0;
    }
}
