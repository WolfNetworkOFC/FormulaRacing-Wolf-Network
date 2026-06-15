package dev.EfraGroup.formulaRacing.Utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class PaperScheduler implements TaskScheduler {

    private static class PaperFRTask implements FRTask {
        private final BukkitTask task;

        public PaperFRTask(BukkitTask task) {
            this.task = task;
        }

        @Override
        public Plugin getOwningPlugin() {
            return task.getOwner();
        }

        @Override
        public boolean isRepeating() {
            return false;
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
        return new PaperFRTask(Bukkit.getScheduler().runTask(plugin, runnable));
    }

    @Override
    public FRTask runDelayedTask(Plugin plugin, Runnable runnable, long delayTicks) {
        return new PaperFRTask(Bukkit.getScheduler().runTaskLater(plugin, runnable, Math.max(1, delayTicks)));
    }

    @Override
    public FRTask runTaskTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        return new PaperFRTask(Bukkit.getScheduler().runTaskTimer(plugin, runnable, Math.max(1, delayTicks), Math.max(1, periodTicks)));
    }

    @Override
    public FRTask runTaskAtLocation(Plugin plugin, Location location, Runnable runnable) {
        return runTask(plugin, runnable);
    }

    @Override
    public FRTask runDelayedTaskAtLocation(Plugin plugin, Location location, Runnable runnable, long delayTicks) {
        return runDelayedTask(plugin, runnable, delayTicks);
    }

    @Override
    public FRTask runTaskTimerAtLocation(Plugin plugin, Location location, Runnable runnable, long delayTicks, long periodTicks) {
        return runTaskTimer(plugin, runnable, delayTicks, periodTicks);
    }

    @Override
    public FRTask runTaskTimerAt(Plugin plugin, World world, Runnable runnable, long delayTicks, long periodTicks) {
        return runTaskTimer(plugin, runnable, delayTicks, periodTicks);
    }

    @Override
    public FRTask runTaskAtEntity(Plugin plugin, Entity entity, Runnable runnable, Runnable retired) {
        return runTask(plugin, runnable);
    }

    @Override
    public FRTask runDelayedTaskAtEntity(Plugin plugin, Entity entity, Runnable runnable, Runnable retired, long delayTicks) {
        return runDelayedTask(plugin, runnable, delayTicks);
    }

    @Override
    public FRTask runTaskTimerAtEntity(Plugin plugin, Entity entity, Runnable runnable, Runnable retired, long delayTicks, long periodTicks) {
        return runTaskTimer(plugin, runnable, delayTicks, periodTicks);
    }

    @Override
    public FRTask runAsync(Plugin plugin, Runnable runnable) {
        return new PaperFRTask(Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    @Override
    public FRTask runAsyncDelayed(Plugin plugin, Runnable runnable, long delayTicks) {
        return new PaperFRTask(Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, runnable, Math.max(1, delayTicks)));
    }

    @Override
    public FRTask runAsyncTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks) {
        return new PaperFRTask(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, runnable, Math.max(1, delayTicks), Math.max(1, periodTicks)));
    }

    @Override
    public void cancelTasks(Plugin plugin) {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}
