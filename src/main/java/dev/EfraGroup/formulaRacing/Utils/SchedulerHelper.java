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

    private SchedulerHelper() {}

    public static void runTask(Plugin plugin, Runnable task) {
        try {
            GLOBAL.execute(plugin, task);
        } catch (Exception e) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    public static ScheduledTask runTaskLater(Plugin plugin, Runnable task, long delayTicks) {
        try {
            return GLOBAL.runDelayed(plugin, t -> task.run(), Math.max(1, delayTicks));
        } catch (Exception e) {
            return Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1, delayTicks));
        }
    }

    public static ScheduledTask runTaskTimer(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        try {
            return GLOBAL.runAtFixedRate(plugin, t -> task.run(), Math.max(1, delayTicks), Math.max(1, periodTicks));
        } catch (Exception e) {
            return Bukkit.getScheduler().runTaskTimer(plugin, task, Math.max(1, delayTicks), Math.max(1, periodTicks));
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Region Scheduler (runs on region thread for chunk)
    // ═══════════════════════════════════════════════════════════

    public static void runTaskAt(Plugin plugin, Location location, Runnable task) {
        REGION.execute(plugin, location, task);
    }

    public static void runTaskAt(Plugin plugin, World world, int chunkX, int chunkZ, Runnable task) {
        REGION.execute(plugin, world, chunkX, chunkZ, task);
    }

    public static ScheduledTask runTaskTimerAt(Plugin plugin, Location location, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        return REGION.runAtFixedRate(plugin, location, task, Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    // ═══════════════════════════════════════════════════════════
    //  Entity Scheduler (runs on entity's owning region)
    // ═══════════════════════════════════════════════════════════

    public static void runTaskFor(Plugin plugin, Entity entity, Runnable task) {
        entity.getScheduler().execute(plugin, task, null, 1L);
    }

    public static ScheduledTask runTaskTimerFor(Plugin plugin, Entity entity, Consumer<ScheduledTask> task, long delayTicks, long periodTicks) {
        return entity.getScheduler().runAtFixedRate(plugin, task, null, Math.max(1, delayTicks), Math.max(1, periodTicks));
    }

    // ═══════════════════════════════════════════════════════════
    //  Async Scheduler
    // ═══════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════
    //  Teleport (Folia-safe)
    // ═══════════════════════════════════════════════════════════

    public static CompletableFuture<Boolean> teleport(Entity entity, Location destination) {
        if (entity instanceof Player player) {
            Entity vehicle = player.getVehicle();
            if (vehicle != null) {
                vehicle.removePassenger(player);
            }
        }
        return entity.teleportAsync(destination);
    }

    public static void teleportNextTick(Plugin plugin, Entity entity, Location destination) {
        runTaskFor(plugin, entity, () -> teleport(entity, destination));
    }

    // ═══════════════════════════════════════════════════════════
    //  Cancel tasks
    // ═══════════════════════════════════════════════════════════

    public static void cancelAllTasks(Plugin plugin) {
        GLOBAL.cancelTasks(plugin);
        ASYNC.cancelTasks(plugin);
    }

    public static void shutdownAsyncPool() {
    }
}
