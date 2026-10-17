package dev.EfraGroup.formulaRacing.Loneliness;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.CollisionMode;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.Lap;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Round.RoundType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Delta Ghosting — porte do DeltaGhostingController do TimingSystem.
 *
 * <p>Quando o heat tem {@code deltaghosting > 0} (segundos), o piloto que está
 * à frente para de enxergar qualquer piloto que esteja a uma diferença de tempo
 * maior ou igual ao delta configurado. O cálculo é refeito exatamente como no
 * TimingSystem: sempre que um piloto cruza uma volta ou um checkpoint, o
 * progresso atual do piloto de trás (volta + checkpoint) vira o ponto de
 * referência, e o gap é a diferença de tempo entre os dois passarem por esse
 * mesmo ponto.
 *
 * <p>Não se aplica a heats com colisão desabilitada
 * ({@link CollisionMode#DISABLED}) — mesma regra do TimingSystem.
 */
public final class DeltaGhostingController {

    private DeltaGhostingController() {
    }

    /** leadingDriver -> pilotos que ele ainda não vê por causa do delta. */
    private static final Map<Driver, Set<Driver>> deltaGhostedPlayers = new ConcurrentHashMap<>();

    /**
     * Recalcula os deltas do piloto que acabou de cruzar uma volta ou um
     * checkpoint. Chamado pelos hooks do {@link Heats#passLap(Driver)} e do
     * RaceCheckpointListener, como no TimingSystem.
     */
    public static void checkDeltas(FormulaRacing plugin, Heats heat, Driver driver) {
        int ghostingDelta = heat.getDeltaGhosting();
        if (ghostingDelta <= 0) {
            return;
        }

        List<Driver> livePositions = heat.getLivePositions();
        if (livePositions == null) {
            return;
        }

        long ghostingDeltaMs = ghostingDelta * 1000L;

        for (Driver otherDriver : livePositions) {
            if (
                otherDriver.equals(driver) ||
                otherDriver.getUuid().equals(driver.getUuid())
            ) {
                continue;
            }

            if (otherDriver.getPosition() < driver.getPosition()) {
                // otherDriver está à frente -> decide se ele deixa de ver driver
                long delta = timeGap(heat, driver, otherDriver);
                if (Math.abs(delta) >= ghostingDeltaMs) {
                    deltaGhost(plugin, heat, otherDriver, driver);
                } else {
                    unDeltaGhost(plugin, heat, otherDriver, driver);
                }
            } else {
                unDeltaGhost(plugin, heat, otherDriver, driver);
            }
        }
    }

    /**
     * Gap em milissegundos do piloto de trás em relação ao da frente.
     * Positivo = o de trás passou pelo ponto de referência mais tarde.
     * Retorna 0 quando não há dados suficientes (não faz ghost).
     */
    private static long timeGap(Heats heat, Driver behind, Driver ahead) {
        // Qualificação: diferença entre as melhores voltas (como no TimingSystem)
        if (isQualification(heat)) {
            Lap behindBest = behind.getFastestLap();
            Lap aheadBest = ahead.getFastestLap();
            if (behindBest == null || aheadBest == null) {
                return 0;
            }
            return behindBest.getLapTime() - aheadBest.getLapTime();
        }

        if (behind.getCurrentLap() == null) {
            return 0;
        }

        // Ponto de referência: progresso atual do piloto de trás.
        int[] progress = behind.getCurrentProgress();
        int lapIndex = progress[0];
        int checkpointId = progress[1];

        Long behindTime = behind.getAbsoluteTimeAtProgress(lapIndex, checkpointId);
        Long aheadTime = ahead.getAbsoluteTimeAtProgress(lapIndex, checkpointId);

        if (behindTime != null && aheadTime != null) {
            return behindTime - aheadTime;
        }

        // Piloto da frente já terminou: usa a diferença dos tempos de chegada.
        if (
            ahead.isFinished() &&
            ahead.getEndTime() != null &&
            behind.getEndTime() != null
        ) {
            return behind.getEndTime() - ahead.getEndTime();
        }

        return 0;
    }

    private static boolean isQualification(Heats heat) {
        if (heat.getRound() == null) {
            return false;
        }
        RoundType type = heat.getRound().getRoundType();
        return (
            type == RoundType.QUALIFICATION ||
            type == RoundType.SPRINT_QUALIFICATION
        );
    }

    /**
     * Esconde o piloto de trás do piloto da frente (player + barco), se as
     * condições permitirem. Espelha o deltaGhost do TimingSystem.
     */
    private static void deltaGhost(
        FormulaRacing plugin,
        Heats heat,
        Driver leadingDriver,
        Driver trailingDriver
    ) {
        if (
            heat.getCollisionMode() == CollisionMode.DISABLED ||
            plugin.getLonelyController().isGhosted(trailingDriver.getUuid())
        ) {
            return;
        }

        deltaGhostedPlayers
            .computeIfAbsent(leadingDriver, key -> ConcurrentHashMap.newKeySet())
            .add(trailingDriver);

        Player leadingPlayer = Bukkit.getPlayer(leadingDriver.getUuid());
        Player trailingPlayer = Bukkit.getPlayer(trailingDriver.getUuid());
        if (leadingPlayer == null || trailingPlayer == null) {
            return;
        }

        plugin
            .getLonelyController()
            .applyDeltaGhost(leadingPlayer, trailingPlayer, true);
    }

    /**
     * Remove o par do registro e volta a mostrar o piloto de trás para o da
     * frente (player + barco), caso ele tenha sido ocultado pelo delta ghost.
     */
    private static void unDeltaGhost(
        FormulaRacing plugin,
        Heats heat,
        Driver leadingDriver,
        Driver trailingDriver
    ) {
        if (
            heat.getCollisionMode() == CollisionMode.DISABLED ||
            plugin.getLonelyController().isGhosted(trailingDriver.getUuid())
        ) {
            return;
        }

        Set<Driver> ghosted = deltaGhostedPlayers.get(leadingDriver);
        boolean wasGhosted = ghosted != null && ghosted.remove(trailingDriver);
        if (ghosted != null && ghosted.isEmpty()) {
            deltaGhostedPlayers.remove(leadingDriver);
        }

        // Só reexibe quem este controlador escondeu — outras regras de
        // visibilidade (lonely, pilotos finalizados, etc.) continuam valendo.
        if (!wasGhosted) {
            return;
        }

        Player leadingPlayer = Bukkit.getPlayer(leadingDriver.getUuid());
        Player trailingPlayer = Bukkit.getPlayer(trailingDriver.getUuid());
        if (leadingPlayer == null || trailingPlayer == null) {
            return;
        }

        plugin
            .getLonelyController()
            .applyDeltaGhost(leadingPlayer, trailingPlayer, false);
    }

    /**
     * Usado pela {@link HeatScope}: true quando {@code trailing} está >= delta
     * atrás de {@code leading} e portanto não deve ser visto por ele.
     */
    public static boolean isDeltaGhosted(
        Heats heat,
        Driver leadingDriver,
        Driver trailingDriver
    ) {
        if (
            heat.getCollisionMode() == CollisionMode.DISABLED ||
            heat.getDeltaGhosting() <= 0
        ) {
            return false;
        }

        Set<Driver> ghosted = deltaGhostedPlayers.get(leadingDriver);
        return ghosted != null && ghosted.contains(trailingDriver);
    }

    /** Remove os pares registrados de um heat (fim/reset do heat). */
    public static void clearDeltaGhosts(Heats heat) {
        Set<Driver> heatDrivers = new HashSet<>(heat.getDrivers().values());
        if (heatDrivers.isEmpty()) {
            return;
        }
        deltaGhostedPlayers.keySet().removeIf(heatDrivers::contains);
        deltaGhostedPlayers
            .values()
            .forEach(ghosted -> ghosted.removeIf(heatDrivers::contains));
    }

    /** Remove um piloto dos pares registrados (quando ele sai do heat). */
    public static void removeHeatDriver(Driver driver) {
        deltaGhostedPlayers.remove(driver);
        deltaGhostedPlayers.values().forEach(ghosted -> ghosted.remove(driver));
    }
}
