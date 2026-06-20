package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Controllers.SpectatorManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LightningRodListener implements Listener {

    private final FormulaRacing plugin;
    private final Map<UUID, ItemDisplay> playerRods = new ConcurrentHashMap<>();
    private int tickCounter = 0;
    private static final int DEBUG_INTERVAL = 100;

    private static final Transformation FLIPPED_ROD = new Transformation(
        new Vector3f(0, 0.625F, 0),
        new Quaternionf(new AxisAngle4f((float) Math.PI, 1, 0, 0)),
        new Vector3f(0.75F, 0.75F, 0.75F),
        new Quaternionf()
    );

    public LightningRodListener(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    public void start() {
        SchedulerHelper.runTaskTimer(plugin, this::processRods, 1L, 1L);
    }

    private void processRods() {
        tickCounter++;
        boolean rodsEnabled = LightningRodManager.isLightningRodsEnabled();
        boolean shouldDebug = tickCounter % DEBUG_INTERVAL == 0;

        if (!rodsEnabled) {
            if (shouldDebug) {
                Bukkit.getLogger().info("[RodDebug] Global rods disabled, removing all rods");
            }
            removeAllRods();
            return;
        }

        for (Player rodOwner : Bukkit.getOnlinePlayers()) {
            if (PlatformUtils.isFoliaRuntime()) {
                UUID ownerId = rodOwner.getUniqueId();
                SchedulerHelper.runTaskFor(plugin, rodOwner, () -> {
                    Entity vehicle = rodOwner.getVehicle();
                    if (vehicle == null) {
                        if (shouldDebug && playerRods.containsKey(ownerId)) {
                            Bukkit.getLogger().info("[RodDebug] " + rodOwner.getName() + ": Not in vehicle, removing rod");
                        }
                        removeRodForPlayer(ownerId);
                        return;
                    }
                    if (!shouldPlayerHaveRod(rodOwner)) {
                        if (shouldDebug && playerRods.containsKey(ownerId)) {
                            Bukkit.getLogger().info("[RodDebug] " + rodOwner.getName() + ": Not in valid heat state, removing rod");
                        }
                        removeRodForPlayer(ownerId);
                        return;
                    }
                    Location targetLocation = rodOwner.getLocation().clone();
                    targetLocation.setYaw(0F);
                    targetLocation.setPitch(0F);
                    SchedulerHelper.runTaskFor(plugin, vehicle, () -> {
                        updateRodOnVehicle(rodOwner, vehicle, ownerId, targetLocation, shouldDebug);
                    });
                });
            } else {
                updateRodForOwner(rodOwner, shouldDebug);
            }
        }
    }

    private void updateRodForOwner(Player rodOwner, boolean shouldDebug) {
        UUID ownerId = rodOwner.getUniqueId();
        Entity vehicle = rodOwner.getVehicle();

        if (vehicle == null
            || vehicle.getPassengers().isEmpty()
            || !vehicle.getPassengers().getFirst().getUniqueId().equals(ownerId)
            || !shouldPlayerHaveRod(rodOwner)) {
            if (shouldDebug && playerRods.containsKey(ownerId)) {
                Bukkit.getLogger().info("[RodDebug] " + rodOwner.getName() + ": Not in vehicle or heat, removing rod");
            }
            removeRodForPlayer(ownerId);
            return;
        }

        ItemDisplay rod = playerRods.get(ownerId);
        Location targetLocation = rodOwner.getLocation().clone();
        targetLocation.setYaw(0F);
        targetLocation.setPitch(0F);

        if (rod == null || !rod.isValid()) {
            rod = createRodForPlayer(rodOwner.getUniqueId(), targetLocation);
            if (rod != null) {
                playerRods.put(ownerId, rod);
                if (shouldDebug) {
                    Bukkit.getLogger().info("[RodDebug] " + rodOwner.getName() + ": Created new rod");
                }
            }
        } else {
            SchedulerHelper.teleportAsync(rod, targetLocation);
        }

        if (rod == null) return;

        updateRodVisibility(rod, rodOwner);
    }

    private void updateRodOnVehicle(Player rodOwner, Entity vehicle, UUID ownerId, Location targetLocation, boolean shouldDebug) {
        if (vehicle.getPassengers().isEmpty()
            || !vehicle.getPassengers().getFirst().getUniqueId().equals(ownerId)) {
            if (shouldDebug && playerRods.containsKey(ownerId)) {
                Bukkit.getLogger().info("[RodDebug] " + rodOwner.getName() + ": Not primary passenger, removing rod");
            }
            removeRodForPlayer(ownerId);
            return;
        }

        ItemDisplay rod = playerRods.get(ownerId);
        if (rod == null || !rod.isValid()) {
            rod = createRodForPlayer(ownerId, targetLocation);
            if (rod != null) {
                playerRods.put(ownerId, rod);
                if (shouldDebug) {
                    Bukkit.getLogger().info("[RodDebug] " + rodOwner.getName() + ": Created new rod");
                }
            }
        } else {
            SchedulerHelper.teleportAsync(rod, targetLocation);
        }

        if (rod == null) return;

        updateRodVisibility(rod, rodOwner);
    }

    private void updateRodVisibility(ItemDisplay rod, Player rodOwner) {
        if (PlatformUtils.isFoliaRuntime()) {
            SchedulerHelper.runTaskFor(plugin, rod, () -> {
                if (!rod.isValid()) return;
                World rodWorld = rod.getWorld();
                int rodRegionX = rod.getLocation().getBlockX() >> 9;
                int rodRegionZ = rod.getLocation().getBlockZ() >> 9;
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (viewer.equals(rodOwner)) continue;
                    boolean canSee = canViewerSeeRod(viewer, rodOwner);
                    SchedulerHelper.runTaskFor(plugin, viewer, () -> {
                        if (!viewer.isOnline()) return;
                        if (!rodWorld.equals(viewer.getWorld())) return;
                        int viewerRegionX = viewer.getLocation().getBlockX() >> 9;
                        int viewerRegionZ = viewer.getLocation().getBlockZ() >> 9;
                        if (rodRegionX != viewerRegionX || rodRegionZ != viewerRegionZ) return;
                        if (canSee) {
                            viewer.showEntity(plugin, rod);
                        } else {
                            viewer.hideEntity(plugin, rod);
                        }
                    });
                }
            });
        } else {
            SchedulerHelper.runTask(plugin, () -> {
                if (!rod.isValid()) return;
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    boolean canSee = canViewerSeeRod(viewer, rodOwner);
                    if (canSee) {
                        viewer.showEntity(plugin, rod);
                    } else {
                        viewer.hideEntity(plugin, rod);
                    }
                }
            });
        }
    }

    private ItemDisplay createRodForPlayer(UUID ownerId, Location location) {
        return location.getWorld().spawn(location, ItemDisplay.class, entity -> {
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setGravity(false);
            entity.setItemStack(new ItemStack(Material.LIGHTNING_ROD));
            entity.setTransformation(FLIPPED_ROD);
            entity.getPersistentDataContainer().set(
                Objects.requireNonNull(NamespacedKey.fromString("lightning_rod", plugin)),
                PersistentDataType.INTEGER, 1);
            entity.getPersistentDataContainer().set(
                Objects.requireNonNull(NamespacedKey.fromString("rod_owner", plugin)),
                PersistentDataType.STRING, ownerId.toString());
        });
    }

    private void removeRodForPlayer(UUID playerId) {
        ItemDisplay rod = playerRods.remove(playerId);
        if (rod != null && rod.isValid()) {
            if (PlatformUtils.isFoliaRuntime()) {
                SchedulerHelper.runTaskFor(plugin, rod, () -> {
                    if (rod.isValid()) rod.remove();
                });
            } else {
                SchedulerHelper.runTask(plugin, () -> {
                    if (rod.isValid()) rod.remove();
                });
            }
        }
    }

    private void removeAllRods() {
        for (UUID playerId : playerRods.keySet()) {
            removeRodForPlayer(playerId);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeRodForPlayer(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        Bukkit.getLogger().info("[RodDebug] Removing all rods on shutdown");
        removeAllRods();
    }

    private boolean shouldPlayerHaveRod(Player player) {
        Heats heat = plugin.getDriverLookup().getHeat(player.getUniqueId());
        if (heat == null) return false;
        HeatState state = heat.getHeatState();
        return state == HeatState.LOADED || state == HeatState.STARTING || state == HeatState.RACING;
    }

    private boolean canViewerSeeRod(Player viewer, Player rodOwner) {
        if (!LightningRodManager.canPlayerSeeRods(viewer.getUniqueId())) return false;

        Heats ownerHeat = plugin.getDriverLookup().getHeat(rodOwner.getUniqueId());
        if (ownerHeat == null) return false;
        HeatState ownerState = ownerHeat.getHeatState();
        if (ownerState != HeatState.LOADED && ownerState != HeatState.STARTING && ownerState != HeatState.RACING) return false;

        Heats viewerHeat = plugin.getDriverLookup().getHeat(viewer.getUniqueId());
        if (viewerHeat != null && viewerHeat.getId() == ownerHeat.getId()) return true;

        SpectatorManager sm = plugin.getSpectatorManager();
        if (sm != null) {
            Heats spectatorHeat = sm.getSpectatorBoundHeat(viewer.getUniqueId());
            if (spectatorHeat != null && spectatorHeat.getId() == ownerHeat.getId()) return true;
        }

        return false;
    }
}
