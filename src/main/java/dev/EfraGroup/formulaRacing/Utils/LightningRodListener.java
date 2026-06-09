package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.Controllers.SpectatorManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import com.destroystokyo.paper.event.server.ServerTickStartEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

    @EventHandler
    public void onTickStart(ServerTickStartEvent e) {
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
            rodOwner.getScheduler().execute(plugin, () -> {
                updateRodForOwner(rodOwner, shouldDebug);
            }, null, 1L);
        }
    }

    private void updateRodForOwner(Player rodOwner, boolean shouldDebug) {
        UUID ownerId = rodOwner.getUniqueId();

        if (rodOwner.getVehicle() == null
            || rodOwner.getVehicle().getPassengers().isEmpty()
            || !((Entity) rodOwner.getVehicle().getPassengers().getFirst()).getUniqueId().equals(ownerId)
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
            rod = createRodForPlayer(rodOwner, targetLocation);
            if (rod != null) {
                playerRods.put(ownerId, rod);
                if (shouldDebug) {
                    Bukkit.getLogger().info("[RodDebug] " + rodOwner.getName() + ": Created new rod");
                }
            }
        } else {
            rod.teleportAsync(targetLocation);
        }

        if (rod == null) return;

        ItemDisplay finalRod = rod;
        finalRod.getScheduler().execute(plugin, () -> {
            if (!finalRod.isValid()) return;
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                boolean canSee = canViewerSeeRod(viewer, rodOwner);
                if (canSee) {
                    viewer.showEntity(plugin, finalRod);
                } else {
                    viewer.hideEntity(plugin, finalRod);
                }
            }
        }, null, 1L);
    }

    private ItemDisplay createRodForPlayer(Player rodOwner, Location location) {
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
                PersistentDataType.STRING, rodOwner.getUniqueId().toString());
        });
    }

    private void removeRodForPlayer(UUID playerId) {
        ItemDisplay rod = playerRods.remove(playerId);
        if (rod != null && rod.isValid()) {
            rod.getScheduler().execute(plugin, () -> {
                if (rod.isValid()) rod.remove();
            }, null, 1L);
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
