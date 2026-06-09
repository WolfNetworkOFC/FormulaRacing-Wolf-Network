package dev.EfraGroup.formulaRacing.Utils;

import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler;
import io.papermc.paper.threadedregions.scheduler.RegionScheduler;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class SchedulerHelper {

    private static final GlobalRegionScheduler GLOBAL = Bukkit.getGlobalRegionScheduler();
    private static final RegionScheduler REGION = Bukkit.getRegionScheduler();
    private static final AsyncScheduler ASYNC = Bukkit.getAsyncScheduler();
    private static Plugin staticPlugin;

    public static void init(Plugin plugin) {
        staticPlugin = plugin;
    }

    private SchedulerHelper() {}

    public static void runTask(Plugin plugin, Runnable task) {
        GLOBAL.execute(plugin, task);
    }

    public static ScheduledTask runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        return GLOBAL.runDelayed(plugin, scheduledTask -> task.run(), Math.max(1, delayTicks));
    }

    public static ScheduledTask runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return GLOBAL.runAtFixedRate(plugin, scheduledTask -> task.run(), Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    public static ScheduledTask runTaskTimer(Plugin plugin, Runnable task) {
        return runTaskTimer(plugin, task, 1L, 1L);
    }

    public static ScheduledTask runTaskTimer(Plugin plugin, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        return GLOBAL.runAtFixedRate(plugin, task, Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    public static ScheduledTask runTaskTimer(Plugin plugin, Consumer<ScheduledTask> task) {
        return runTaskTimer(plugin, task, 1L, 1L);
    }

    public static void runTaskAt(Plugin plugin, Location location, Runnable task) {
        World world = location.getWorld();
        if (world == null) {
            GLOBAL.execute(plugin, task);
            return;
        }
        REGION.execute(plugin, world, location.getBlockX() >> 4, location.getBlockZ() >> 4, task);
    }

    public static void runTaskAt(Plugin plugin, World world, int chunkX, int chunkZ, Runnable task) {
        REGION.execute(plugin, world, chunkX, chunkZ, task);
    }

    public static ScheduledTask runTaskTimerAt(Plugin plugin, World world, int chunkX, int chunkZ, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        return REGION.runAtFixedRate(plugin, world, chunkX, chunkZ, task, Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    public static ScheduledTask runTaskTimerAt(Plugin plugin, Location location, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        World world = location.getWorld();
        if (world == null) {
            return GLOBAL.runAtFixedRate(plugin, task, Math.max(1, delayTicks), Math.max(1, periodTicks));
        }
        return REGION.runAtFixedRate(plugin, world, location.getBlockX() >> 4, location.getBlockZ() >> 4, task, Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    public static void runTaskFor(Plugin plugin, Entity entity, Runnable task) {
        entity.getScheduler().execute(plugin, task, null, 1L);
    }

    public static void runTaskFor(Plugin plugin, Entity entity, Consumer<Entity> task) {
        entity.getScheduler().execute(plugin, () -> task.accept(entity), null, 1L);
    }

    public static void runTaskFor(Plugin plugin, Entity entity, Consumer<Entity> task, long delayTicks) {
        entity.getScheduler().execute(plugin, () -> task.accept(entity), null, delayTicks);
    }

    public static void runTaskFor(Plugin plugin, Entity entity, Runnable task, long delayTicks) {
        entity.getScheduler().execute(plugin, task, null, delayTicks);
    }

    public static void runTaskTimerFor(Plugin plugin, Entity entity, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        entity.getScheduler().runAtFixedRate(plugin, task, null, Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    public static void runAsync(Plugin plugin, Runnable task) {
        ASYNC.runNow(plugin, t -> task.run());
    }

    public static CompletableFuture<Void> runAsyncFuture(Plugin plugin, Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        ASYNC.runNow(plugin, t -> {
            try {
                task.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public static void runAsyncLater(Plugin plugin, Runnable task, long delayTicks) {
        ASYNC.runDelayed(plugin, t -> task.run(), delayTicks * 50L, TimeUnit.MILLISECONDS);
    }

    public static void runAsyncTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        ASYNC.runAtFixedRate(plugin, t -> task.run(), delayTicks * 50L, periodTicks * 50L, TimeUnit.MILLISECONDS);
    }

    public static void teleportNextTick(Plugin plugin, Entity entity, Location destination) {
        runTaskFor(plugin, entity, e -> {
            if (e instanceof Player player) {
                Entity vehicle = player.getVehicle();
                if (vehicle != null) {
                    vehicle.removePassenger(player);
                }
            }
            e.teleportAsync(destination);
        });
    }

    public static void teleport(Plugin plugin, Player player, Location destination) {
        teleportNextTick(plugin, player, destination);
    }

    public static CompletableFuture<Boolean> teleport(Player player, Location destination) {
        if (player == null) return CompletableFuture.completedFuture(false);
        return player.teleportAsync(destination);
    }

    public static void teleport(Entity entity, Location destination) {
        if (entity == null || staticPlugin == null) return;
        entity.getScheduler().execute(staticPlugin, () -> entity.teleport(destination), null, 1L);
    }

    public static CompletableFuture<Boolean> teleportAsync(Plugin plugin, Entity entity, Location destination) {
        runTaskFor(plugin, entity, e -> {
            if (e instanceof Player player) {
                Entity vehicle = player.getVehicle();
                if (vehicle != null) {
                    vehicle.removePassenger(player);
                }
            }
        });
        return entity.teleportAsync(destination);
    }

    public static void cancelAllTasks(Plugin plugin) {
        GLOBAL.cancelTasks(plugin);
        ASYNC.cancelTasks(plugin);
    }

    public static void shutdownAsyncPool() {
    }
}