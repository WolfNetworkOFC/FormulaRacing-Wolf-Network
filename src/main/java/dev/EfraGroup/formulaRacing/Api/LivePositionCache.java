package dev.EfraGroup.formulaRacing.Api;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache de posição (x, y, z, yaw, pitch, world) dos pilotos em heats ativos.
 * Atualizado por um scheduler global leve (default 100ms) que lê a Location
 * de cada piloto via SchedulerHelper.runTaskFor (Folia-safe). A API REST lê
 * apenas deste cache, nunca acessa entidades diretamente — assim o endpoint
 * de mapa ao vivo é barato e não trava o servidor sob concorrência Folia.
 */
public class LivePositionCache {

    public static class Snapshot {
        public final double x;
        public final double y;
        public final double z;
        public final float yaw;
        public final float pitch;
        public final String world;
        public final long timestamp;

        public Snapshot(Location loc, long timestamp) {
            this.x = loc.getX();
            this.y = loc.getY();
            this.z = loc.getZ();
            this.yaw = loc.getYaw();
            this.pitch = loc.getPitch();
            this.world = loc.getWorld() != null ? loc.getWorld().getName() : null;
            this.timestamp = timestamp;
        }
    }

    private final FormulaRacing plugin;
    private final Map<UUID, Snapshot> positions = new ConcurrentHashMap<>();
    private final long intervalTicks;
    private FRTask task;

    public LivePositionCache(FormulaRacing plugin) {
        this.plugin = plugin;
        this.intervalTicks = 2L; // ~100ms a 20 TPS
    }

    public void start() {
        stop();
        this.task = SchedulerHelper.runTaskTimer(plugin, this::refresh, 2L, intervalTicks);
    }

    public void stop() {
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception ignored) {
            }
            task = null;
        }
        positions.clear();
    }

    private void refresh() {
        long now = System.currentTimeMillis();
        for (Events event : plugin.getRaceEventManager().getAllEvents()) {
            for (var round : event.getSchedule().getRoundsList()) {
                for (Heats heat : round.getHeats().values()) {
                    if (heat.getHeatState() != HeatState.RACING && heat.getHeatState() != HeatState.STARTING) {
                        continue;
                    }
                    for (var driver : heat.getLivePositions()) {
                        Player player = plugin.getServer().getPlayer(driver.getUuid());
                        if (player == null || !player.isOnline()) {
                            continue;
                        }
                        // getLocation(Location) faz cópia segura (Folia-safe)
                        Location loc = player.getLocation(new Location(null, 0, 0, 0));
                        positions.put(driver.getUuid(), new Snapshot(loc, now));
                    }
                }
            }
        }
    }

    public Snapshot get(UUID uuid) {
        return positions.get(uuid);
    }

    public Map<UUID, Snapshot> getAll() {
        return positions;
    }
}
