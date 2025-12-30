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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FormulaRacingListener implements Listener {
    private final APIFormulaRacing api;
    private final TimerUtils timerUtils;
    private final FormulaRacing plugin;
    private final DatabaseManager db;
    private final PacketSender packetSender;

    // Cooldown para mensagens de "não pode sair do barco"
    private final Map<UUID, Long> dismountMessageCooldown = new ConcurrentHashMap<>();
    private static final long MESSAGE_COOLDOWN_MS = 3000; // 3 segundos

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
        }.runTaskTimer(plugin, 1200L, 6000L); // 1 minuto delay inicial | Roda a cada 5 minutos
    }

    @EventHandler
    public void onBoatExit(VehicleExitEvent event) {
        if (!(event.getVehicle() instanceof Boat boat)) return;
        if (!(event.getExited() instanceof Player player)) return;

        // 1. Verifica se o player que está saindo é de fato o MOTORISTA (passageiro 0)
        // Usamos getPassengers().stream().findFirst() para segurança total
        boolean isDriver = !boat.getPassengers().isEmpty() && boat.getPassengers().get(0).equals(player);

        if (!isDriver) return; // Se for passageiro, não fazemos nada

        // verificamos no banco se ele está em um duelo ativo
        if (db.isPlayerInActiveDuel(player.getUniqueId())) {
            event.setCancelled(true);

            // Cooldown de mensagens para evitar spam
            UUID uuid = player.getUniqueId();
            long now = System.currentTimeMillis();
            Long lastMessage = dismountMessageCooldown.get(uuid);

            if (lastMessage == null || (now - lastMessage) >= MESSAGE_COOLDOWN_MS) {
                String langCode = db.getPlayerLanguage(uuid);
                player.sendMessage(plugin.getDirectTranslation("duel_cannot_dismount", langCode));
                player.sendMessage(plugin.getDirectTranslation("duel_use_quit", langCode));
                dismountMessageCooldown.put(uuid, now);
            }
            return;
        }

        // 3. TimeTrial
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