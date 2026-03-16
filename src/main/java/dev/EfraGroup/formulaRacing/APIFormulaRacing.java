//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing;

import dev.EfraGroup.formulaRacing.Cosmetics.BoatTrailManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class APIFormulaRacing {
    private final JavaPlugin plugin;
    private final BoatTrailManager trailManager;
    private final DatabaseManager databaseManager;
    private final NMSHandler nmsHandler;
    private static final Map<UUID, ArmorStand> lockedBoats = new HashMap();

    public APIFormulaRacing(JavaPlugin plugin, DatabaseManager databaseManager, NMSHandler nmsHandler) {
        this.plugin = plugin;
        this.trailManager = new BoatTrailManager(plugin);
        this.databaseManager = databaseManager;
        this.nmsHandler = nmsHandler;
    }

    public EntityType getPlayerBoatType(UUID uuid) {
        int boatId = this.databaseManager.getPlayerBoatType(uuid);
        EntityType var10000;
        switch (boatId) {
            case 1 -> var10000 = EntityType.OAK_BOAT;
            case 2 -> var10000 = EntityType.BIRCH_BOAT;
            case 3 -> var10000 = EntityType.SPRUCE_BOAT;
            case 4 -> var10000 = EntityType.JUNGLE_BOAT;
            case 5 -> var10000 = EntityType.ACACIA_BOAT;
            case 6 -> var10000 = EntityType.DARK_OAK_BOAT;
            case 7 -> var10000 = EntityType.MANGROVE_BOAT;
            case 8 -> var10000 = EntityType.CHERRY_BOAT;
            case 9 -> var10000 = EntityType.BAMBOO_RAFT;
            case 10 -> var10000 = EntityType.OAK_CHEST_BOAT;
            case 11 -> var10000 = EntityType.BIRCH_CHEST_BOAT;
            case 12 -> var10000 = EntityType.SPRUCE_CHEST_BOAT;
            case 13 -> var10000 = EntityType.JUNGLE_CHEST_BOAT;
            case 14 -> var10000 = EntityType.ACACIA_CHEST_BOAT;
            case 15 -> var10000 = EntityType.DARK_OAK_CHEST_BOAT;
            case 16 -> var10000 = EntityType.MANGROVE_CHEST_BOAT;
            case 17 -> var10000 = EntityType.CHERRY_CHEST_BOAT;
            case 18 -> var10000 = EntityType.BAMBOO_CHEST_RAFT;
            default -> var10000 = EntityType.OAK_BOAT;
        }

        return var10000;
    }

    public void spawnBoat(Player player, boolean trail, boolean locked, boolean checkground) {
        Location loc = player.getLocation();
        UUID uuid = player.getUniqueId();
        if (!(player.getVehicle() instanceof Boat)) {
            if (checkground && !player.isOnGround()) {
                player.sendMessage("§cEsteja no chão para executar este comando.");
            } else {
                EntityType boatType = this.getPlayerBoatType(uuid);
                Boat boat = null;

                try {
                    if (this.nmsHandler != null) {
                        this.nmsHandler.setBoatType(boatType.name());
                        boat = this.nmsHandler.spawnBoat(loc);
                    } else {
                        boat = (Boat)loc.getWorld().spawnEntity(loc, boatType);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return;
                }

                if (boat != null) {
                    if (locked) {
                        Location anchorLoc = loc.clone().add((double)0.0F, 1.45, (double)0.0F);
                        ArmorStand anchor = (ArmorStand)loc.getWorld().spawnEntity(anchorLoc, EntityType.ARMOR_STAND);
                        anchor.setInvulnerable(true);
                        anchor.setGravity(false);
                        anchor.setVisible(false);
                        anchor.setSmall(true);
                        anchor.setMarker(true);
                        anchor.addPassenger(boat);
                        lockedBoats.put(uuid, anchor);
                    }

                    boat.addPassenger(player);
                    if (player.getGameMode() == GameMode.SPECTATOR) {
                        player.setGameMode(GameMode.ADVENTURE);
                    }

                }
            }
        }
    }

    public void releaseBoat(Player player) {
        ArmorStand ar = (ArmorStand)lockedBoats.get(player.getUniqueId());
        if (ar != null) {
            ar.remove();
            lockedBoats.remove(player.getUniqueId());
        }

    }

    public void deleteBoat(Entity boat) {
        if (boat instanceof Boat) {
            lockedBoats.values().removeIf((as) -> {
                if (as.getPassengers().contains(boat)) {
                    as.remove();
                    return true;
                } else {
                    return false;
                }
            });
            boat.remove();
        }

    }
}
