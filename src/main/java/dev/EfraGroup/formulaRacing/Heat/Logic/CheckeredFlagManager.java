package dev.EfraGroup.formulaRacing.Heat.Logic;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatConfig;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Gerenciador de Checkered Flag (Bandeira Quadriculada)
 * Controla o fluxo de finalização quando o primeiro colocado cruza a linha
 */
public class CheckeredFlagManager {

    private final FormulaRacing plugin;
    private boolean checkeredFlagShown = false;
    private Driver winner = null;

    public CheckeredFlagManager(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    /**
     * Verifica se deve mostrar a bandeira quadriculada
     */
    public boolean shouldShowCheckeredFlag(Heats heat, Driver driver) {
        HeatConfig config = heat.getHeatConfig();

        if (!config.isEnableCheckeredFlagFlow()) {
            return false;
        }

        if (checkeredFlagShown) {
            return false;
        }

        // Verificar se o piloto completou os requisitos de vitória
        if (!hasCompletedRaceRequirements(heat, driver)) {
            return false;
        }

        // Verificar se é o primeiro a completar
        Optional<Driver> firstFinisher = getFirstFinisher(heat);

        if (firstFinisher.isPresent() && firstFinisher.get().getUuid().equals(driver.getUuid())) {
            return true;
        }

        return false;
    }

    /**
     * Mostra a bandeira quadriculada e finaliza a corrida para todos
     */
    public void showCheckeredFlag(Heats heat, Driver driver) {
        HeatConfig config = heat.getHeatConfig();

        if (checkeredFlagShown) {
            return;
        }

        checkeredFlagShown = true;
        winner = driver;

        // Anunciar vitória
        announceWinner(heat, driver);

        // Marcar corrida como finalizada para todos
        config.setRaceFinishedForAll(true);

        plugin.getDebugManager().logRaceSystem(
            "[CHECKERED FLAG] Bandeira quadriculada mostrada para " + driver.getUuid() +
            " - Corrida finalizada para todos"
        );

        // Finalizar todos os pilotos que já completaram a corrida
        finalizeAllDrivers(heat);
    }

    /**
     * Verifica se um piloto deve ser finalizado após a bandeira quadriculada
     */
    public boolean shouldFinalizeDriver(Heats heat, Driver driver) {
        HeatConfig config = heat.getHeatConfig();

        if (!config.isRaceFinishedForAll()) {
            return false;
        }

        if (driver.isFinished() || driver.isDnf()) {
            return false;
        }

        // Verificar se o piloto cruzou a linha de chegada
        return hasCrossedFinishLine(heat, driver);
    }

    /**
     * Finaliza um piloto após a bandeira quadriculada
     */
    public void finalizeDriver(Heats heat, Driver driver) {
        if (driver.isFinished() || driver.isDnf()) {
            return;
        }

        // Marcar como finalizado
        driver.setFinished(true);

        // Travar posição atual
        int currentPosition = driver.getPosition();
        driver.setPosition(currentPosition);

        // Remover do barco
        Player player = Bukkit.getPlayer(driver.getUuid());
        if (player != null && player.isOnline()) {
            if (player.getVehicle() != null) {
                player.getVehicle().remove();
            }

            // Teleportar para spawn
            Location spawnLoc = plugin.getDatabaseManager().getTrackSpawn(heat.getTrackNameWS());
            if (spawnLoc != null) {
                SchedulerHelper.teleport(player,spawnLoc);
            }

            plugin.getDebugManager().logRaceSystem(
                "[CHECKERED FLAG] Piloto " + player.getName() + " finalizado na posição " + currentPosition
            );
        }
    }

    /**
     * Verifica se o piloto completou os requisitos de vitória
     */
    private boolean hasCompletedRaceRequirements(Heats heat, Driver driver) {
        // Verificar se completou o número de voltas necessário
        if (heat.getTotalLaps() != null && driver.getLapCount() < heat.getTotalLaps()) {
            return false;
        }

        // Verificar se completou pits obrigatórios
        if (heat.getTotalPits() != null && heat.getTotalPits() > 0) {
            if (!driver.hasCompletedMandatoryPits(heat.getTotalPits())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Obtém o primeiro piloto a completar a corrida
     */
    private Optional<Driver> getFirstFinisher(Heats heat) {
        // Otimização: Usar stream eficiente para encontrar o primeiro finalizador
        return heat.getDrivers().values().stream()
            .filter(d -> !d.isFinished() && !d.isDnf())
            .filter(d -> hasCompletedRaceRequirements(heat, d))
            .min((d1, d2) -> Long.compare(d1.getTotalTime(), d2.getTotalTime()));
    }

    /**
     * Verifica se o piloto cruzou a linha de chegada
     */
    private boolean hasCrossedFinishLine(Heats heat, Driver driver) {
        // Verificar se completou pelo menos uma volta
        return driver.getLapCount() > 0;
    }

    /**
     * Anuncia o vencedor
     */
    private void announceWinner(Heats heat, Driver driver) {
        String winnerName = "Unknown";

        Player player = Bukkit.getPlayer(driver.getUuid());
        if (player != null) {
            winnerName = player.getName();
        }

        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(ChatColor.GOLD + "🏁 BANDEIRA QUADRICULADA! 🏁");
        Bukkit.broadcastMessage(ChatColor.GREEN + "🏆 VENCEDOR: " + ChatColor.WHITE + winnerName);
        Bukkit.broadcastMessage(ChatColor.GRAY + "Posição final: " + ChatColor.YELLOW + "#" + driver.getPosition());
        Bukkit.broadcastMessage(ChatColor.GRAY + "Tempo: " + ChatColor.YELLOW + formatTime(driver.getTotalTime()));
        Bukkit.broadcastMessage("");
    }

    /**
     * Finaliza todos os pilotos que completaram a corrida
     */
    private void finalizeAllDrivers(Heats heat) {
        // Otimização: Processar apenas pilotos que completaram requisitos
        heat.getDrivers().values().stream()
            .filter(d -> !d.isFinished() && !d.isDnf())
            .filter(d -> hasCompletedRaceRequirements(heat, d))
            .forEach(d -> finalizeDriver(heat, d));
    }

    /**
     * Formata tempo em milissegundos para formato legível
     */
    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        long ms = milliseconds % 1000;

        return String.format("%d:%02d.%03d", minutes, remainingSeconds, ms);
    }

    /**
     * Reseta o gerenciador
     */
    public void reset() {
        checkeredFlagShown = false;
        winner = null;
    }

    /**
     * Verifica se a bandeira quadriculada foi mostrada
     */
    public boolean isCheckeredFlagShown() {
        return checkeredFlagShown;
    }

    /**
     * Obtém o vencedor
     */
    public Optional<Driver> getWinner() {
        return Optional.ofNullable(winner);
    }
}
