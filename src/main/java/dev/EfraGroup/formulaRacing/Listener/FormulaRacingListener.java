package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.APIFormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.scheduler.BukkitRunnable;

public class FormulaRacingListener implements Listener {
    private final APIFormulaRacing api;
    private final TimerUtils timerUtils;
    private final FormulaRacing plugin;
    private final DatabaseManager db;
    private final PacketSender packetSender;

    public FormulaRacingListener(FormulaRacing plugin, TimerUtils timerUtils, APIFormulaRacing api, DatabaseManager db, PacketSender packetSender) {
        this.plugin = plugin;
        this.timerUtils = timerUtils;
        this.api = api;
        this.db = db;
        this.packetSender = packetSender;

        // Inicia o limpador automático de barcos abandonados
        startBoatCleaner();
    }

    /**
     * Verifica e remove barcos sem passageiros a cada 5 minutos.
     * Isso evita que barcos bugados fiquem acumulando no servidor.
     */
    private void startBoatCleaner() {
        new BukkitRunnable() {
            @Override
            public void run() {
                int removedCount = 0;
                for (World world : Bukkit.getWorlds()) {
                    for (Boat boat : world.getEntitiesByClass(Boat.class)) {
                        // Se o barco não tem passageiros, ele é deletado
                        if (boat.getPassengers().isEmpty()) {
                            // Usamos a API para garantir que remova conforme sua lógica
                            api.deleteBoat(boat);
                            removedCount++;
                        }
                    }
                }
                if (removedCount > 0) {
                    plugin.getLogger().info("[FormulaRacing] Limpeza: " + removedCount + " barcos abandonados foram removidos.");
                }
            }
        }.runTaskTimer(plugin, 0L, 200L); // 1200L (1 min delay inicial) | 6000L (Roda a cada 5 minutos)
    }

    @EventHandler
    public void onBoatExit(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) return;
        if (!(event.getExited() instanceof Player player)) return;

        // 1. Verifica se o player que está saindo é o motorista (primeiro passageiro)
        if (boat.getPassengers().isEmpty() || !boat.getPassengers().get(0).equals(player)) {
            return;
        }

        // 2. Verifica se o jogador está em um duelo ativo
        // Se estiver em duelo, cancelamos a saída do barco
        if (db.isPlayerInActiveDuel(player.getUniqueId())) {
            event.setCancelled(true);
            // Opcional: avisar o jogador que ele não pode sair durante o duelo
            // player.sendMessage("§cVocê não pode sair do barco durante um duelo!");
            return;
        }

        // 3. Se não for duelo, segue a lógica normal de deletar o barco e parar o timer solo
        api.deleteBoat(boat);
        timerUtils.stopTimer(player);
    }

    // Caso o barco seja destruído ou suma por outro motivo (ex: explosão ou dano)
    @EventHandler
    public void onVehicleDestroy(org.bukkit.event.vehicle.VehicleDestroyEvent event) {
        if (event.getVehicle() instanceof Boat boat) {
            api.deleteBoat(boat);
        }
    }
}