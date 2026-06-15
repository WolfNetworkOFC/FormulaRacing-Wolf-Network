package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class QualificationManager {
    private final FormulaRacing plugin;
    private final DebugManager debug;

    public QualificationManager(FormulaRacing plugin) {
        this.plugin = plugin;
        this.debug = plugin.getDebugManager();
    }

    public void processQualificationResults(Events event, Rounds qualificationRound) {
        this.debug.logQualificationSystem("Processando resultados da qualificação...");
        Rounds finalRound = (Rounds)event.getEventSchedule().getRound(qualificationRound.getRoundIndex() + 1).orElse(null);
        if (finalRound == null) {
            this.debug.logQualificationSystem("ERRO: Round final não encontrado!");
        } else {
            List<QualificationResult> results = this.collectQualificationResults(qualificationRound);
            if (results.isEmpty()) {
                this.debug.logQualificationSystem("AVISO: Nenhum resultado de qualificação encontrado!");
            } else {
                results.sort(Comparator.comparingLong(QualificationResult::getBestLapTime));
                this.debug.logQualificationSystem("Resultados da qualificação:");

                for(int i = 0; i < results.size(); ++i) {
                    QualificationResult result = (QualificationResult)results.get(i);
                    this.debug.logQualificationSystem(String.format("  P%d: %s - %s", i + 1, result.getDriverUUID(), this.formatTime(result.getBestLapTime())));
                }

                this.applyGridToFinalRound(finalRound, results);
                event.getAnnouncements().broadcastQualificationResults(event, results);
                this.debug.logQualificationSystem("Grid de largada definido com sucesso!");
            }
        }
    }

    private List<QualificationResult> collectQualificationResults(Rounds qualificationRound) {
        List<QualificationResult> results = new ArrayList();

        for(Heats heat : qualificationRound.getHeats().values()) {
            for(Driver driver : heat.getDrivers().values()) {
                long bestTime = Long.MAX_VALUE;
                int laps = driver.getLapCount();
                if (driver.getFastestLap() != null && driver.getFastestLap().getLapTime() > 0L) {
                    bestTime = driver.getFastestLap().getLapTime();
                } else {
                    this.debug.logQualificationSystem(String.format("AVISO: Driver %s não completou nenhuma volta válida - será colocado no fim do grid", driver.getUuid()));
                }

                results.add(new QualificationResult(driver.getUuid(), bestTime, laps));
            }
        }

        return results;
    }

    private void applyGridToFinalRound(Rounds finalRound, List<QualificationResult> results) {
        Heats finalHeat = (Heats)finalRound.getHeat(1).orElse(null);
        if (finalHeat == null) {
            this.debug.logQualificationSystem("ERRO: Heat final não encontrado!");
        } else {
            if (finalHeat.getId() > 0) {
                this.plugin.getRaceEventManager().getDatabaseManager().clearHeatDriversSync(finalHeat.getId());
            }
            finalHeat.getDrivers().clear();

            for(int i = 0; i < results.size(); ++i) {
                QualificationResult result = (QualificationResult)results.get(i);
                int gridPosition = i + 1;
                boolean added = finalHeat.addDriver(result.getDriverUUID(), gridPosition);
                if (added) {
                    this.debug.logQualificationSystem(String.format("Grid P%d: %s (Quali: %s, %d voltas)", gridPosition, result.getDriverUUID(), this.formatTime(result.getBestLapTime()), result.getTotalLaps()));
                } else {
                    this.debug.logQualificationSystem(String.format("AVISO: Não foi possível adicionar %s ao grid (limite atingido ou driver já existe)", result.getDriverUUID()));
                }
            }

            this.debug.logQualificationSystem(String.format("%d pilotos adicionados ao grid final", results.size()));
        }
    }

    private String formatTime(long timeMs) {
        if (timeMs > 0L && timeMs != Long.MAX_VALUE) {
            long minutes = timeMs / 60000L;
            long seconds = timeMs % 60000L / 1000L;
            long millis = timeMs % 1000L;
            return minutes > 0L ? String.format("%d:%02d.%03d", minutes, seconds, millis) : String.format("%d.%03d", seconds, millis);
        } else {
            return "N/A";
        }
    }

    public static class QualificationResult {
        private final UUID driverUUID;
        private final long bestLapTime;
        private final int totalLaps;

        public QualificationResult(UUID driverUUID, long bestLapTime, int totalLaps) {
            this.driverUUID = driverUUID;
            this.bestLapTime = bestLapTime;
            this.totalLaps = totalLaps;
        }

        public UUID getDriverUUID() {
            return this.driverUUID;
        }

        public long getBestLapTime() {
            return this.bestLapTime;
        }

        public int getTotalLaps() {
            return this.totalLaps;
        }
    }
}
