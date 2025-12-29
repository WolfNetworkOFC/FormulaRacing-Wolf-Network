package dev.EfraGroup.formulaRacing.Duels;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.Utils.TimeTrialDuelsAction;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.List;

public class TimeTrialDuels implements Listener {

    private final FormulaRacing plugin;
    private final DatabaseManager dm;
    private final PacketSender packet;
    private final TimeTrialDuelsAction ttda;

    public TimeTrialDuels(FormulaRacing plugin, DatabaseManager dm, PacketSender packet, TimeTrialDuelsAction ttda) {
        this.plugin = plugin;
        this.dm = dm;
        this.packet = packet;
        this.ttda = ttda;
    }

    public void startDuelPreparation(Player p1, Player p2, String trackName, int laps, int timeLimit) {
        Location spawnLoc = dm.getTrackSpawn(trackName);

        if (spawnLoc == null) {
            p1.sendMessage("§cErro ao carregar o spawn da pista.");
            p2.sendMessage("§cErro ao carregar o spawn da pista.");
            return;
        }

        String trackNameWS = trackName.replace(" ", "");
        List<Player> participants = Arrays.asList(p1, p2);

        // 1. Registro no Banco de Dados
        // Se o seu dm.createDuel retornar o ID do duelo, você deve capturá-lo.
        // Se ele for void, precisaremos de uma forma de identificar o duelo ativo.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            dm.createDuel(p1, participants, trackNameWS, laps, timeLimit);
        });

        // 2. Aplicar NBT/Tags dos barcos
        packet.applyBoatUtilsToPlayer(p1, trackNameWS);
        packet.applyBoatUtilsToPlayer(p2, trackNameWS);

        // 3. Ativar Visuais (Action Bar em modo espera)
        // CORREÇÃO: Agora passa o duelId. Se você não tiver o ID real ainda por ser async,
        // pode passar um ID genérico ou garantir que o createDuel seja síncrono para obter o ID.
        int tempDuelId = 0; // Substitua pelo ID real se o seu DB retornar um
        ttda.toggleVisuals(p1, tempDuelId, true);
        ttda.toggleVisuals(p2, tempDuelId, true);

        // 4. Posicionamento no Grid
        setupPlayerInGrid(p1, spawnLoc.clone());
        setupPlayerInGrid(p2, spawnLoc.clone());

        // 5. Inicia a sequência de contagem
        // Dentro deste método, quando a contagem chegar em 0, você deve chamar ttda.toggleTimer
        startFullCountdownSequence(p1, p2);
    }

    private void startFullCountdownSequence(Player p1, Player p2) {
        new BukkitRunnable() {
            int phase1 = 5; // Chat
            int phase2 = 5; // Title
            boolean inPhase2 = false;

            @Override
            public void run() {
                // FASE 1: Mensagens no Chat
                if (!inPhase2) {
                    if (phase1 > 0) {
                        String msg = "§b§lDUEL §8» §fStarting in §e" + phase1 + "s§f...";
                        p1.sendMessage(msg);
                        p2.sendMessage(msg);
                        p1.playSound(p1.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                        p2.playSound(p2.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                        phase1--;
                    } else {
                            inPhase2 = true;
                    }
                    return;
                }

                // FASE 2: Contagem no Title e Largada
                if (phase2 > 0) {
                    String number = "" + phase2;

                    // Envia o Título Grande
                    // Parâmetros: Título, Subtítulo, FadeIn, Stay, FadeOut (em ticks)
                    p1.sendTitle(number, "", 0, 20, 5);
                    p2.sendTitle(number, "", 0, 20, 5);
                    p1.playSound(p1.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1f, 0.5f);
                    p2.playSound(p2.getLocation(), Sound.BLOCK_NOTE_BLOCK_HARP, 1f, 0.5f);

                    phase2--;
                } else {
                    p1.playSound(p1.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                    p2.playSound(p2.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

                    releasePlayers(p1, p2);
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Roda a cada 1 segundo (20 ticks)
    }

    private void releasePlayers(Player p1, Player p2) {
        for (Player p : Arrays.asList(p1, p2)) {
            if (p.getVehicle() instanceof Boat boat) {
                // Se o barco estiver montado no ArmorStand, removemos o stand
                if (boat.getVehicle() instanceof ArmorStand stand) {
                    stand.remove();
                    // Ao remover o stand, o barco cai e o player ganha controle
                }
            }
        }
    }

    private void setupPlayerInGrid(Player player, Location baseLoc) {
        player.teleport(baseLoc);

        Location asLoc = baseLoc.clone().add(0, 1, 0);
        ArmorStand stand = (ArmorStand) baseLoc.getWorld().spawnEntity(asLoc, EntityType.ARMOR_STAND);

        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setMarker(true);

        Boat boat = (Boat) baseLoc.getWorld().spawnEntity(baseLoc, EntityType.OAK_BOAT);

        stand.addPassenger(boat);
        boat.addPassenger(player);

        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f);
    }
}