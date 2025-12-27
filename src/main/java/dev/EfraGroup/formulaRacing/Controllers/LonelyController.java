package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.BoatUtils.NocolManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.Bukkit;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Player;

import java.util.*;

public class LonelyController {

    private final DatabaseManager databaseManager;
    private final FormulaRacing plugin;


    // guarda quem está visível para cada player
    private final Map<UUID, Set<UUID>> lastVisibleMap = new HashMap<>();

    public LonelyController(DatabaseManager databaseManager, FormulaRacing plugin) {
        this.databaseManager = databaseManager;
        this.plugin = plugin;

    }

    /**
     * Atualiza a visibilidade e colisão de um jogador em relação aos outros
     */
    public void updateVisibilityFor(Player player) {
        boolean lonely = databaseManager.getLonelyModePlayer(player.getUniqueId());
        boolean inBoat = player.getVehicle() instanceof Boat || player.getVehicle() instanceof ChestBoat;
        boolean hasMod = plugin.hasOpenBoatUtilsMod(player); // check do mod real

        Set<UUID> currentlyVisible = new HashSet<>();

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player)) continue;
            currentlyVisible.add(other.getUniqueId());

            if (lonely && inBoat && hasMod) {
                // sem colisão enquanto estiver em lonely mode dentro de barco
                NocolManager.setCollisionMode(player, false);
            } else {
                // colisão normal
                NocolManager.setCollisionMode(player, true);
            }
        }

        lastVisibleMap.put(player.getUniqueId(), currentlyVisible);
    }

    /**
     * Atualiza todos os jogadores online
     */
    public void updateAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateVisibilityFor(player);
        }
    }

    /**
     * Limpa cache quando um jogador sai
     */
    public void removePlayer(Player player) {
        lastVisibleMap.remove(player.getUniqueId());
    }
}
