package dev.EfraGroup.formulaRacing.Utils;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

public interface TaskScheduler {
    FRTask runTask(Plugin plugin, Runnable runnable);
    FRTask runTask(Plugin plugin, Consumer<FRTask> runnable);
    FRTask runDelayedTask(Plugin plugin, Runnable runnable, long delayTicks);
    FRTask runTaskTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks);
    FRTask runTaskTimer(Plugin plugin, Consumer<FRTask> runnable, long delayTicks, long periodTicks);

    FRTask runTaskAtLocation(Plugin plugin, Location location, Runnable runnable);
    FRTask runDelayedTaskAtLocation(Plugin plugin, Location location, Runnable runnable, long delayTicks);
    FRTask runTaskTimerAtLocation(Plugin plugin, Location location, Runnable runnable, long delayTicks, long periodTicks);

    FRTask runTaskTimerAt(Plugin plugin, World world, Runnable runnable, long delayTicks, long periodTicks);

    FRTask runTaskAtEntity(Plugin plugin, Entity entity, Runnable runnable, Runnable retired);
    FRTask runTaskAtEntity(Plugin plugin, Entity entity, Consumer<FRTask> runnable, Runnable retired);
    FRTask runDelayedTaskAtEntity(Plugin plugin, Entity entity, Runnable runnable, Runnable retired, long delayTicks);
    FRTask runTaskTimerAtEntity(Plugin plugin, Entity entity, Runnable runnable, Runnable retired, long delayTicks, long periodTicks);

    FRTask runAsync(Plugin plugin, Runnable runnable);
    FRTask runAsyncDelayed(Plugin plugin, Runnable runnable, long delayTicks);
    FRTask runAsyncTimer(Plugin plugin, Runnable runnable, long delayTicks, long periodTicks);

    void cancelTasks(Plugin plugin);
}
