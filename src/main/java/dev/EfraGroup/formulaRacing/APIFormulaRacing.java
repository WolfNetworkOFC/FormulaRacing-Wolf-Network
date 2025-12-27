package dev.EfraGroup.formulaRacing;

import dev.EfraGroup.formulaRacing.Cosmetics.BoatTrailManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class APIFormulaRacing {

    private final JavaPlugin plugin;
    private final BoatTrailManager trailManager;
    private final DatabaseManager databaseManager;

    private static final Map<UUID, ArmorStand> lockedBoats = new HashMap<>();

    // Sistema de votos
    private final Map<String, Integer> votos = new HashMap<>();
    private final Map<UUID, String> votoJogador = new HashMap<>();
    private final Map<UUID, Long> cooldownVoto = new HashMap<>();

    public APIFormulaRacing(JavaPlugin plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.trailManager = new BoatTrailManager(plugin);
        this.databaseManager = databaseManager;
    }

    public EntityType getPlayerBoatType(UUID uuid) {
        int boatId = databaseManager.getPlayerBoatType(uuid);

        return switch (boatId) {
            case 1 -> EntityType.OAK_BOAT;
            case 2 -> EntityType.BIRCH_BOAT;
            case 3 -> EntityType.SPRUCE_BOAT;
            case 4 -> EntityType.JUNGLE_BOAT;
            case 5 -> EntityType.ACACIA_BOAT;
            case 6 -> EntityType.DARK_OAK_BOAT;
            case 7 -> EntityType.MANGROVE_BOAT;
            case 8 -> EntityType.CHERRY_BOAT;
            case 9 -> EntityType.BAMBOO_RAFT;
            case 10 -> EntityType.OAK_CHEST_BOAT;
            case 11 -> EntityType.BIRCH_CHEST_BOAT;
            case 12 -> EntityType.SPRUCE_CHEST_BOAT;
            case 13 -> EntityType.JUNGLE_CHEST_BOAT;
            case 14 -> EntityType.ACACIA_CHEST_BOAT;
            case 15 -> EntityType.DARK_OAK_CHEST_BOAT;
            case 16 -> EntityType.MANGROVE_CHEST_BOAT;
            case 17 -> EntityType.CHERRY_CHEST_BOAT;
            case 18 -> EntityType.BAMBOO_CHEST_RAFT;
            default -> EntityType.OAK_BOAT; // fallback seguro
        };
    }

    /**
     * Spawna o barco do jogador conforme o tipo salvo no banco de dados.
     */
    public void spawnBoat(Player player, boolean trail, boolean locked, boolean checkground) {
        Location loc = player.getLocation();
        UUID uuid = player.getUniqueId();

        if (player.getVehicle() instanceof Boat) return;

        if (checkground && !player.isOnGround()) {
            player.sendMessage("§cEsteja no chão para executar este comando.");
            return;
        }

        // Pega o tipo de barco do jogador
        EntityType boatType = getPlayerBoatType(uuid);

        // Spawna o barco
        Entity entity = loc.getWorld().spawnEntity(loc, boatType);
        if (!(entity instanceof Boat boat)) {
            player.sendMessage("§cErro ao spawnar barco!");
            return;
        }

        boat.addPassenger(player);

        if (locked) {
            ArmorStand ar = (ArmorStand) loc.getWorld().spawnEntity(loc.clone().add(0, -1.5, 0), EntityType.ARMOR_STAND);
            ar.setInvulnerable(true);
            ar.setGravity(false);
            ar.setVisible(false);
            ar.addPassenger(boat);
            lockedBoats.put(player.getUniqueId(), ar);
        }

        if (trail) {
            //trailManager.setTrail(boat);
        }

        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.ADVENTURE);
        }

    }


    public void releaseBoat(Player player) {
            ArmorStand ar = lockedBoats.get(player.getUniqueId());
        if (ar != null) {
            ar.remove();
            lockedBoats.remove(player.getUniqueId());
        }
    }

    public void deleteBoat(Entity boat) {
        if (boat instanceof Boat) {
            //trailManager.removeTrail((Boat) boat);

            lockedBoats.values().removeIf(as -> {
                if (as.getPassengers().contains(boat)) {
                    as.remove();
                    return true;
                }
                return false;
            });

            boat.remove();
        }
    }

    public BoatTrailManager getTrailManager() {
        return trailManager;
    }

    /** Voto do jogador */
    public void votar(Player player, String pista) {
        long now = System.currentTimeMillis();
        if (cooldownVoto.containsKey(player.getUniqueId())) {
            long lastVote = cooldownVoto.get(player.getUniqueId());
            if (now - lastVote < 30_000) { // 30 segundos
                player.sendMessage("§cVocê precisa esperar 30 segundos para votar novamente!");
                return;
            }
        }

        // Remove voto anterior, se houver
        String votoAnterior = votoJogador.get(player.getUniqueId());
        if (votoAnterior != null) {
            votos.put(votoAnterior, votos.get(votoAnterior) - 1);
        }

        votos.put(pista, votos.getOrDefault(pista, 0) + 1);
        votoJogador.put(player.getUniqueId(), pista);
        cooldownVoto.put(player.getUniqueId(), now);

        Bukkit.broadcastMessage("§a" + player.getName() + " votou para §e" + pista + "§a!");
    }

    /** Retorna a pista mais votada que tenha 30% dos jogadores online */
    public String pistaMaisVotada() {
        int online = Bukkit.getOnlinePlayers().size();
        if (online == 0) return null;

        return votos.entrySet().stream()
                .filter(e -> e.getValue() >= Math.ceil(online * 0.3)) // pelo menos 30%
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /** Reseta os votos */
    public void resetarVotacao() {
        votos.clear();
        votoJogador.clear();
        cooldownVoto.clear();
    }

    /** Inicia uma corrida apenas se a pista tiver votos suficientes */
    public boolean podeIniciarCorrida(String pista) {
        return pistaMaisVotada() != null && pistaMaisVotada().equals(pista);
    }

}
