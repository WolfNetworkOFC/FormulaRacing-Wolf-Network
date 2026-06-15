package dev.EfraGroup.formulaRacing.Utils;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public class FoliaScheduler implements TaskScheduler {

    private static class FoliaFRTask implements FRTask {
        private final ScheduledTask task;

        public FoliaFRTask(ScheduledTask task) {
            this.task = task;
        }

        @Override
        public Plugin getOwningPlugin() {
            return task.getOwningPlugin();
        }

        @Override
        public boolean isRepeating() {
            return task.isRepeatingTask();
        }

        @Override
        public void cancel() {
            task.cancel();
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }
    }

    @Override
    public FRTask runTask(Plugin plugin, Runnable runnable) {
        return new FoliaFRTask(Bukkit.getGlobalRegionScheduler().run(plugin, t -> runnable.run()));
    }

    @Override
    public FRTask runDelayedTask(Plugin plugin, Runnable runnable, long delayTicks) {
        return new FoliaFRTask(Bukkit.getGlobalRegionScheduler().runDelayed(plugin, t -> runnable.run(), Math.max(1, delayTicks)));
    }

    @Override
    public FRTask runTaskTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        return new FoliaFRTask(Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> runnable.run(), Math.max(1, delayTicks), Math.max(1, periodTicks)));
    }

    @Override
    public FRTask runTaskAtLocation(Plugin plugin, Location location, Runnable runnable) {
        return new FoliaFRTask(Bukkit.getRegionScheduler().run(plugin, location, t -> runnable.run()));
    }

    @Override
    public FRTask runDelayedTaskAtLocation(Plugin plugin, Location location, Runnable runnable, long delayTicks) {
        return new FoliaFRTask(Bukkit.getRegionScheduler().runDelayed(plugin, location, t -> runnable.run(), Math.max(1, delayTicks)));
    }

    @Override
    public FRTask runTaskTimerAtLocation(Plugin plugin, Location location, Runnable runnable, long delayTicks, long periodTicks) {
        return new FoliaFRTask(Bukkit.getRegionScheduler().runAtFixedRate(plugin, location, t -> runnable.run(), Math.max(1, delayTicks), Math.max(1, periodTicks)));
    }

    @Override
    public FRTask runTaskAtEntity(Plugin plugin, Entity entity, Runnable runnable, Runnable retired) {
        return new FoliaFRTask(entity.getScheduler().run(plugin, t -> runnable.run(), retired));
    }

    @Override
    public FRTask runDelayedTaskAtEntity(Plugin plugin, Entity entity, Runnable runnable, Runnable retired, long delayTicks) {
        return new FoliaFRTask(entity.getScheduler().runDelayed(plugin, t -> runnable.run(), retired, Math.max(1, delayTicks)));
    }

    @Override
    public FRTask runTaskTimerAtEntity(Plugin plugin, Entity entity, Runnable runnable, Runnable retired, long delayTicks, long periodTicks) {
        return new FoliaFRTask(entity.getScheduler().runAtFixedRate(plugin, t -> runnable.run(), retired, Math.max(1, delayTicks), Math.max(1, periodTicks)));
    }

    @Override
    public FRTask runAsync(Plugin plugin, Runnable runnable) {
        return new FoliaFRTask(Bukkit.getAsyncScheduler().runNow(plugin, t -> runnable.run()));
    }

    @Override
    public FRTask runAsyncDelayed(Plugin plugin, Runnable runnable, long delayTicks) {
        return new FoliaFRTask(Bukkit.getAsyncScheduler().runDelayed(plugin, t -> runnable.run(), delayTicks * 50, TimeUnit.MILLISECONDS));
    }

    @Override
    public FRTask runAsyncTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        return new FoliaFRTask(Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> runnable.run(), delayTicks * 50, periodTicks * 50, TimeUnit.MILLISECONDS));
    }

    @Override
    public void cancelTasks(Plugin plugin) {
        Bukkit.getGlobalRegionScheduler().cancelTasks(plugin);
        Bukkit.getAsyncScheduler().cancelTasks(plugin);
    }
}
