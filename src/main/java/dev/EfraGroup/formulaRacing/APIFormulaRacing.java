package dev.EfraGroup.formulaRacing;

import dev.EfraGroup.formulaRacing.Cosmetics.BoatTrailManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

public class APIFormulaRacing {
    private final JavaPlugin plugin;
    private final BoatTrailManager trailManager;
    private final DatabaseManager databaseManager;
    private final NMSHandler nmsHandler;
    private static final Map<UUID, ArmorStand> lockedBoats = new ConcurrentHashMap<>();

    public APIFormulaRacing(JavaPlugin plugin, DatabaseManager databaseManager, NMSHandler nmsHandler) {
        this.plugin = plugin;
        this.trailManager = new BoatTrailManager(plugin);
        this.databaseManager = databaseManager;
        this.nmsHandler = nmsHandler;
    }

    public EntityType getPlayerBoatType(UUID uuid) {
        int boatId = this.databaseManager.getPlayerBoatType(uuid);
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
            default -> EntityType.OAK_BOAT;
        };
    }

    public void spawnBoat(Player player, boolean trail, boolean locked, boolean checkground) {
        this.spawnBoat(player, trail, locked, checkground, false);
    }

    public void spawnBoat(Player player, boolean trail, boolean locked, boolean checkground, boolean collidable) {
        Location loc = player.getLocation().clone();
        this.spawnBoatAt(player, loc, trail, locked, checkground, collidable);
    }

    public void spawnBoatAt(Player player, Location location, boolean trail, boolean locked, boolean checkground) {
        this.spawnBoatAt(player, location, trail, locked, checkground, false);
    }

    public void spawnBoatAt(Player player, Location location, boolean trail, boolean locked, boolean checkground, boolean collidable) {
        Location loc = location.clone();
        SchedulerHelper.runTaskAt(this.plugin, loc, () -> this.spawnBoatNow(player, loc, trail, locked, checkground, collidable));
    }

    private void spawnBoatNow(Player player, Location loc, boolean trail, boolean locked, boolean checkground, boolean collidable) {
        UUID uuid = player.getUniqueId();
        boolean recovered = this.recoverPlayerBoatState(player);
        if (checkground && !player.isOnGround() && !recovered) {
            player.sendMessage("§cEsteja no chão para executar este comando.");
        } else {
            EntityType boatType = this.getPlayerBoatType(uuid);
            Boat boat = null;

            try {
                if (this.nmsHandler != null) {
                    this.nmsHandler.setBoatType(boatType.name());
                    boat = this.nmsHandler.spawnBoat(loc, collidable);
                } else {
                    boat = (Boat)loc.getWorld().spawnEntity(loc, boatType);
                }
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }

            if (boat != null) {
                if (locked) {
                    Location anchorLoc = loc.clone();
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

    public void queueSpawnBoat(Player player, boolean trail, boolean locked, boolean checkground) {
        this.spawnBoat(player, trail, locked, checkground);
    }

    public boolean recoverPlayerBoatState(Player player) {
        boolean recovered = false;
        UUID uuid = player.getUniqueId();
        ArmorStand lockedAnchor = (ArmorStand)lockedBoats.get(uuid);
        if (lockedAnchor != null) {
            recovered = true;
        }

        this.releaseBoat(player);
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            boolean temporaryMetadata = !player.hasMetadata("fr_resetting");
            if (temporaryMetadata) {
                player.setMetadata("fr_resetting", new FixedMetadataValue(this.plugin, true));
            }

            player.leaveVehicle();

            if (vehicle instanceof Boat boat) {
                boolean tempMeta = temporaryMetadata;
                Player finalPlayer = player;
                SchedulerHelper.runTaskFor(this.plugin, boat, () -> {
                    if (boat.getPassengers().contains(finalPlayer)) {
                        boat.removePassenger(finalPlayer);
                    }
                    deleteBoat(boat);
                    if (tempMeta) {
                        SchedulerHelper.runTaskFor(this.plugin, finalPlayer, () -> {
                            finalPlayer.removeMetadata("fr_resetting", this.plugin);
                        });
                    }
                });
            } else if (temporaryMetadata) {
                player.removeMetadata("fr_resetting", this.plugin);
            }

            recovered = true;
        }
        return recovered;
    }

    public void respawnBoat(Player player, boolean trail, boolean locked, boolean checkground) {
        this.spawnBoat(player, trail, locked, checkground);
    }

    public void releaseBoat(Player player) {
        ArmorStand ar = (ArmorStand)lockedBoats.get(player.getUniqueId());
        if (ar != null) {
            lockedBoats.remove(player.getUniqueId());
            SchedulerHelper.runTaskFor(this.plugin, ar, () -> ar.remove());
        }

    }

    public void deleteBoat(Entity boat) {
        if (!(boat instanceof Boat)) return;

        SchedulerHelper.runTaskFor(this.plugin, boat, () -> {
            Entity vehicle = boat.getVehicle();
            if (vehicle instanceof ArmorStand as) {
                lockedBoats.values().remove(as);
                SchedulerHelper.runTaskFor(this.plugin, as, () -> {
                    if (as.isValid()) as.remove();
                });
            }
            if (boat.isValid()) {
                boat.remove();
            }
        });
    }

    public void queueDeleteBoat(Entity boat) {
        if (!(boat instanceof Boat)) return;

        SchedulerHelper.runTaskFor(this.plugin, boat, () -> {
            Entity vehicle = boat.getVehicle();
            if (vehicle instanceof ArmorStand as) {
                lockedBoats.values().remove(as);
                SchedulerHelper.runTaskFor(this.plugin, as, () -> {
                    if (as.isValid()) as.remove();
                });
            }
            if (boat.isValid()) {
                boat.remove();
            }
        });
    }
}