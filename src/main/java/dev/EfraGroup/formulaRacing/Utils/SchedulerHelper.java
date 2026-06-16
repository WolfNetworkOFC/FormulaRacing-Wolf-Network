package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class SchedulerHelper {

    private static final TaskScheduler SCHEDULER = PlatformUtils.isFolia() ? new FoliaScheduler() : new PaperScheduler();

    public static void init(FormulaRacing plugin) {
    }

    public static FRTask runTask(Plugin plugin, Runnable runnable) {
        return SCHEDULER.runTask(plugin, runnable);
    }

    public static FRTask runTask(FormulaRacing plugin, Runnable runnable) {
        return SCHEDULER.runTask(plugin, runnable);
    }

    public static FRTask runTask(Plugin plugin, Consumer<FRTask> runnable) {
        return SCHEDULER.runTask(plugin, runnable);
    }

    public static FRTask runTask(FormulaRacing plugin, Consumer<FRTask> runnable) {
        return SCHEDULER.runTask(plugin, runnable);
    }

    public static FRTask runDelayedTask(Plugin plugin, Runnable runnable, long delayTicks) {
        return SCHEDULER.runDelayedTask(plugin, runnable, delayTicks);
    }

    public static FRTask runDelayedTask(FormulaRacing plugin, Runnable runnable, long delayTicks) {
        return SCHEDULER.runDelayedTask(plugin, runnable, delayTicks);
    }

    public static FRTask runTaskTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        return SCHEDULER.runTaskTimer(plugin, runnable, delayTicks, periodTicks);
    }

    public static FRTask runTaskTimer(FormulaRacing plugin, Runnable runnable, long delayTicks, long periodTicks) {
        return SCHEDULER.runTaskTimer(plugin, runnable, delayTicks, periodTicks);
    }

    public static FRTask runTaskTimer(Plugin plugin, Consumer<FRTask> runnable, long delayTicks, long periodTicks) {
        return SCHEDULER.runTaskTimer(plugin, runnable, delayTicks, periodTicks);
    }

    public static FRTask runTaskTimer(FormulaRacing plugin, Consumer<FRTask> runnable, long delayTicks, long periodTicks) {
        return SCHEDULER.runTaskTimer(plugin, runnable, delayTicks, periodTicks);
    }

    public static FRTask runTaskLater(Plugin plugin, Runnable runnable, long delayTicks) {
        return SCHEDULER.runDelayedTask(plugin, runnable, delayTicks);
    }

    public static FRTask runTaskAtLocation(Plugin plugin, Location location, Runnable runnable) {
        return SCHEDULER.runTaskAtLocation(plugin, location, runnable);
    }

    public static FRTask runDelayedTaskAtLocation(Plugin plugin, Location location, Runnable runnable, long delayTicks) {
        return SCHEDULER.runDelayedTaskAtLocation(plugin, location, runnable, delayTicks);
    }

    public static FRTask runTaskTimerAtLocation(Plugin plugin, Location location, Runnable runnable, long delayTicks, long periodTicks) {
        return SCHEDULER.runTaskTimerAtLocation(plugin, location, runnable, delayTicks, periodTicks);
    }

    public static FRTask runTaskTimerAt(Plugin plugin, World world, Runnable runnable, long delayTicks, long periodTicks) {
        return SCHEDULER.runTaskTimerAt(plugin, world, runnable, delayTicks, periodTicks);
    }

    public static FRTask runTaskAt(Plugin plugin, Location location, Runnable runnable) {
        return SCHEDULER.runTaskAtLocation(plugin, location, runnable);
    }

    public static FRTask runTaskFor(Plugin plugin, Entity entity, Runnable runnable) {
        return SCHEDULER.runTaskAtEntity(plugin, entity, runnable, null);
    }

    public static FRTask runTaskFor(Plugin plugin, Player player, Runnable runnable) {
        return SCHEDULER.runTaskAtEntity(plugin, player, runnable, null);
    }

    public static FRTask runTaskFor(Plugin plugin, Player player, Runnable runnable, long delayTicks) {
        return SCHEDULER.runDelayedTaskAtEntity(plugin, player, runnable, null, delayTicks);
    }

    public static boolean teleport(Entity entity, Location destination) {
        if (PlatformUtils.isFolia()) {
            return entity.teleport(destination);
        } else {
            return entity.teleport(destination);
        }
    }

    public static CompletableFuture<Boolean> teleportAsync(Entity entity, Location destination) {
        if (PlatformUtils.isFolia()) {
            return entity.teleportAsync(destination);
        } else {
            CompletableFuture<Boolean> future = new CompletableFuture<>();
            future.complete(entity.teleport(destination));
            return future;
        }
    }

    public static CompletableFuture<Boolean> teleportAsync(FormulaRacing plugin, Player player, Location destination) {
        return teleportAsync(player, destination);
    }

    public static void cancelAllTasks(Plugin plugin) {
        SCHEDULER.cancelTasks(plugin);
    }

    public static void shutdownAsyncPool() {
    }

    public static FRTask runTaskAtEntity(Plugin plugin, Entity entity, Runnable runnable, Runnable retired) {
        return SCHEDULER.runTaskAtEntity(plugin, entity, runnable, retired);
    }

    public static FRTask runDelayedTaskAtEntity(Plugin plugin, Entity entity, Runnable runnable, long delayTicks) {
        return SCHEDULER.runDelayedTaskAtEntity(plugin, entity, runnable, null, delayTicks);
    }

    public static FRTask runDelayedTaskAtEntity(Plugin plugin, Entity entity, Runnable runnable, Runnable retired, long delayTicks) {
        return SCHEDULER.runDelayedTaskAtEntity(plugin, entity, runnable, retired, delayTicks);
    }

    public static FRTask runTaskTimerAtEntity(Plugin plugin, Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
        return SCHEDULER.runTaskTimerAtEntity(plugin, entity, runnable, null, delayTicks, periodTicks);
    }

    public static FRTask runTaskTimerAtEntity(Plugin plugin, Entity entity, Runnable runnable, Runnable retired, long delayTicks, long periodTicks) {
        return SCHEDULER.runTaskTimerAtEntity(plugin, entity, runnable, retired, delayTicks, periodTicks);
    }

    public static FRTask runAsync(Plugin plugin, Runnable runnable) {
        return SCHEDULER.runAsync(plugin, runnable);
    }

    public static FRTask runAsync(FormulaRacing plugin, Runnable runnable) {
        return SCHEDULER.runAsync(plugin, runnable);
    }

    public static FRTask runAsyncDelayed(Plugin plugin, Runnable runnable, long delayTicks) {
        return SCHEDULER.runAsyncDelayed(plugin, runnable, delayTicks);
    }

    public static FRTask runAsyncTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        return SCHEDULER.runAsyncTimer(plugin, runnable, delayTicks, periodTicks);
    }
}
