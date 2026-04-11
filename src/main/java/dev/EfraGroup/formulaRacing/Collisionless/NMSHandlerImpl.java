package dev.EfraGroup.formulaRacing.Collisionless;

import dev.EfraGroup.formulaRacing.NMSHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.bukkit.Location;
import org.bukkit.craftbukkit.v1_21_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R3.entity.boat.*;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.util.Objects;

public class NMSHandlerImpl implements NMSHandler {
    private String boatType = "OAK_BOAT";

    @Override
    public void setBoatType(String boatType) {
        this.boatType = boatType.toUpperCase();
    }

    @Override
    public org.bukkit.entity.Boat spawnBoat(Location location) {
        ServerLevel level = ((CraftWorld) Objects.requireNonNull(location.getWorld())).getHandle();
        float yaw = Location.normalizeYaw(location.getYaw());
        double x = location.getX();
        double y = location.getY() + 0.5; // Ajustado de 2.5 para 0.5 (evita spawnar no céu)
        double z = location.getZ();

        return switch (this.boatType) {
            case "OAK_BOAT" -> createBoat(EntityType.OAK_BOAT, level, x, y, z, yaw, CraftOakBoat::new);
            case "SPRUCE_BOAT" -> createBoat(EntityType.SPRUCE_BOAT, level, x, y, z, yaw, CraftSpruceBoat::new);
            case "BIRCH_BOAT" -> createBoat(EntityType.BIRCH_BOAT, level, x, y, z, yaw, CraftBirchBoat::new);
            case "JUNGLE_BOAT" -> createBoat(EntityType.JUNGLE_BOAT, level, x, y, z, yaw, CraftJungleBoat::new);
            case "ACACIA_BOAT" -> createBoat(EntityType.ACACIA_BOAT, level, x, y, z, yaw, CraftAcaciaBoat::new);
            case "DARK_OAK_BOAT" -> createBoat(EntityType.DARK_OAK_BOAT, level, x, y, z, yaw, CraftDarkOakBoat::new);
            case "MANGROVE_BOAT" -> createBoat(EntityType.MANGROVE_BOAT, level, x, y, z, yaw, CraftMangroveBoat::new);
            case "CHERRY_BOAT" -> createBoat(EntityType.CHERRY_BOAT, level, x, y, z, yaw, CraftCherryBoat::new);
            case "BAMBOO_RAFT" -> createRaft(EntityType.BAMBOO_RAFT, level, x, y, z, yaw, CraftBambooRaft::new);

            // Chest Boats
            case "OAK_CHEST_BOAT" -> createChestBoat(EntityType.OAK_CHEST_BOAT, level, x, y, z, yaw, CraftOakChestBoat::new);
            case "SPRUCE_CHEST_BOAT" -> createChestBoat(EntityType.SPRUCE_CHEST_BOAT, level, x, y, z, yaw, CraftSpruceChestBoat::new);
            case "BIRCH_CHEST_BOAT" -> createChestBoat(EntityType.BIRCH_CHEST_BOAT, level, x, y, z, yaw, CraftBirchChestBoat::new);
            case "JUNGLE_CHEST_BOAT" -> createChestBoat(EntityType.JUNGLE_CHEST_BOAT, level, x, y, z, yaw, CraftJungleChestBoat::new);
            case "ACACIA_CHEST_BOAT" -> createChestBoat(EntityType.ACACIA_CHEST_BOAT, level, x, y, z, yaw, CraftAcaciaChestBoat::new);
            case "DARK_OAK_CHEST_BOAT" -> createChestBoat(EntityType.DARK_OAK_CHEST_BOAT, level, x, y, z, yaw, CraftDarkOakChestBoat::new);
            case "MANGROVE_CHEST_BOAT" -> createChestBoat(EntityType.MANGROVE_CHEST_BOAT, level, x, y, z, yaw, CraftMangroveChestBoat::new);
            case "CHERRY_CHEST_BOAT" -> createChestBoat(EntityType.CHERRY_CHEST_BOAT, level, x, y, z, yaw, CraftCherryChestBoat::new);
            case "BAMBOO_CHEST_RAFT" -> createChestRaft(EntityType.BAMBOO_CHEST_RAFT, level, x, y, z, yaw, CraftBambooChestRaft::new);

            default -> createBoat(EntityType.OAK_BOAT, level, x, y, z, yaw, CraftOakBoat::new);
        };
    }

    // Métodos auxiliares para reduzir repetição (DRY)
    private org.bukkit.entity.Boat createBoat(EntityType<? extends Boat> type, ServerLevel level, double x, double y, double z, float yaw, java.util.function.BiFunction<org.bukkit.craftbukkit.v1_21_R3.CraftServer, AbstractBoat, org.bukkit.entity.Boat> constructor) {
        CollisionlessBoat boat = new CollisionlessBoat(type, level, () -> Items.AIR);
        setupEntity(boat, x, y, z, yaw, level);
        return constructor.apply(level.getCraftServer(), boat);
    }

    private org.bukkit.entity.Boat createRaft(EntityType<? extends Raft> type, ServerLevel level, double x, double y, double z, float yaw, java.util.function.BiFunction<org.bukkit.craftbukkit.v1_21_R3.CraftServer, AbstractBoat, org.bukkit.entity.Boat> constructor) {
        CollisionlessRaft raft = new CollisionlessRaft(type, level, () -> Items.AIR);
        setupEntity(raft, x, y, z, yaw, level);
        return constructor.apply(level.getCraftServer(), raft);
    }

    private org.bukkit.entity.ChestBoat createChestBoat(EntityType<? extends ChestBoat> type, ServerLevel level, double x, double y, double z, float yaw, java.util.function.BiFunction<org.bukkit.craftbukkit.v1_21_R3.CraftServer, AbstractChestBoat, org.bukkit.entity.ChestBoat> constructor) {
        CollisionlessChestBoat boat = new CollisionlessChestBoat(type, level, () -> Items.AIR);
        setupEntity(boat, x, y, z, yaw, level);
        return constructor.apply(level.getCraftServer(), boat);
    }

    private org.bukkit.entity.ChestBoat createChestRaft(EntityType<? extends ChestRaft> type, ServerLevel level, double x, double y, double z, float yaw, java.util.function.BiFunction<org.bukkit.craftbukkit.v1_21_R3.CraftServer, AbstractChestBoat, org.bukkit.entity.ChestBoat> constructor) {
        CollisionlessChestRaft raft = new CollisionlessChestRaft(type, level, () -> Items.AIR);
        setupEntity(raft, x, y, z, yaw, level);
        return constructor.apply(level.getCraftServer(), raft);
    }

    private void setupEntity(Entity entity, double x, double y, double z, float yaw, ServerLevel level) {
        entity.setPos(x, y, z);
        entity.setYRot(yaw);
        entity.yRotO = yaw;
        level.addFreshEntity(entity, CreatureSpawnEvent.SpawnReason.CUSTOM);
    }

    @Override
    public org.bukkit.entity.ChestBoat spawnChestBoat(Location location) {
        // Implementado para usar o sistema de tipos atual
        if (!this.boatType.contains("CHEST")) {
            this.boatType = "OAK_CHEST_BOAT";
        }
        return (org.bukkit.entity.ChestBoat) spawnBoat(location);
    }
}