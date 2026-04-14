//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Round;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.CollisionMode;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.Logic.PracticeSession;
import dev.EfraGroup.formulaRacing.Heat.Logic.SessionLogic;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Participant.Spectator;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.stream.Collectors;

public class PracticeRound extends Rounds {
    public PracticeRound(FormulaRacing plugin, int id, Events event, int roundIndex, RoundType roundType) {
        super(plugin, id, event, roundIndex, roundType);
    }

    public PracticeRound(FormulaRacing plugin) {
    }

    public Heats createHeat(int heatNumber) {
        Heats heat = new Heats(this.plugin, 0, this, heatNumber);
        heat.setCollisionMode(CollisionMode.DISABLED);
        heat.setTotalLaps((Integer)null);
        heat.setCanReset(true);
        this.heats.put(heatNumber, heat);
        return heat;
    }

    public void broadcastResults() {
        this.plugin.getDebugManager().logRaceSystem("Processando resultados de practice...");

        for (Heats heat : this.heats.values()) {
            var sortedDrivers = heat.getDrivers().values().stream()
                .filter(d -> d.getFastestLap() != null && d.getFastestLap().getLapTime() > 0)
                .sorted(Comparator.comparingLong(d -> d.getFastestLap().getLapTime()))
                .collect(Collectors.toList());

            if (sortedDrivers.isEmpty()) {
                continue;
            }

            String heatLabel = this.heats.size() > 1 ? " (Heat " + heat.getHeatNumber() + ")" : "";

            for (int i = 0; i < sortedDrivers.size(); i++) {
                Driver driver = sortedDrivers.get(i);
                Player player = Bukkit.getPlayer(driver.getUuid());
                if (player != null && player.isOnline()) {
                    player.sendMessage(String.format("§7P%d: §f%s §7- §f%s", i + 1, player.getName(), formatTime(driver.getFastestLap().getLapTime())));
                }
            }

            if (this.event != null) {
                for (int i = 0; i < Math.min(3, sortedDrivers.size()); i++) {
                    Driver driver = sortedDrivers.get(i);
                    Player player = Bukkit.getPlayer(driver.getUuid());
                    if (player != null && player.isOnline()) {
                        String message = String.format("§6P%d Practice%s: §f%s §7- §f%s", i + 1, heatLabel, player.getName(), formatTime(driver.getFastestLap().getLapTime()));
                        for (Spectator spectator : this.event.getSpectators().values()) {
                            Player specPlayer = Bukkit.getPlayer(spectator.getUuid());
                            if (specPlayer != null) {
                                specPlayer.sendMessage(message);
                            }
                        }
                        break;
                    }
                }
            }
        }

        this.plugin.getDebugManager().logRaceSystem("Practice finalizado! Resultados anunciados.");
    }

    private String formatTime(long timeMs) {
        if (timeMs <= 0) {
            return "N/A";
        }
        long minutes = timeMs / 60000L;
        long seconds = timeMs % 60000L / 1000L;
        long millis = timeMs % 1000L;
        return minutes > 0 ? String.format("%d:%02d.%03d", minutes, seconds, millis) : String.format("%d.%03d", seconds, millis);
    }

    public SessionLogic getSessionLogic() {
        return new PracticeSession();
    }
}
