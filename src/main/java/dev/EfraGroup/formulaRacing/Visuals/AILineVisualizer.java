package dev.EfraGroup.formulaRacing.Visuals;

import dev.EfraGroup.formulaRacing.AI.AIRacingLine;
import dev.EfraGroup.formulaRacing.AI.AIRacingLineManager;
import dev.EfraGroup.formulaRacing.Controllers.SpectatorManager;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Participant.Spectator;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders the AI racing line of a heat as particles for the players/spectators
 * of that heat. Mirrors the TrackVisualizer style: a single periodic task
 * draws only the points near each viewer. Only active while a heat that
 * contains at least one AI driver is running.
 */
public class AILineVisualizer {

    private final FormulaRacing plugin;
    private final Map<Integer, AILineCache> activeHeats = new ConcurrentHashMap<>();
    private static final double RENDER_DISTANCE_SQ = 4096.0; // 64 blocks
    private static final Color IDEAL_COLOR = Color.AQUA;
    private static final Color BRAKE_COLOR = Color.RED;
    private static final Color ACCEL_COLOR = Color.LIME;

    public AILineVisualizer(FormulaRacing plugin) {
        this.plugin = plugin;
        this.startTask();
    }

    /**
     * Registers a heat for AI line rendering. Only registers if the heat
     * contains at least one AI-controlled driver and has a usable racing line.
     */
    public void registerHeat(Heats heat) {
        if (heat == null || heat.getId() <= 0) {
            return;
        }

        boolean hasAI = heat.getDrivers().values().stream().anyMatch(Driver::isAiControlled);
        if (!hasAI) {
            return;
        }

        AIRacingLineManager lineManager = plugin.getAIRacingLineManager();
        if (lineManager == null) {
            return;
        }

        String trackNameWS = heat.getTrackNameWS();
        if (trackNameWS == null || trackNameWS.isEmpty()) {
            return;
        }

        // Ensure the track has a usable line so we always have something to render.
        if (!lineManager.hasRacingLine(trackNameWS)) {
            lineManager.generateBasicRacingLine(trackNameWS);
            lineManager.saveAllRacingLines();
        }

        AILineCache cache = buildCache(heat);
        if (cache == null || cache.isEmpty()) {
            return;
        }

        activeHeats.put(heat.getId(), cache);
        plugin.getDebugManager().logRaceSystem(
            "[AI-VIS] Rendering AI line for heat " + heat.getId() +
            " (" + cache.ideal.size() + " points)"
        );
    }

    public void unregisterHeat(int heatId) {
        activeHeats.remove(heatId);
    }

    public void clear() {
        activeHeats.clear();
    }

    private AILineCache buildCache(Heats heat) {
        AIRacingLineManager lineManager = plugin.getAIRacingLineManager();
        if (lineManager == null) {
            return null;
        }

        String trackName = heat.getTrackNameWS();
        if (trackName == null || trackName.isEmpty()) {
            return null;
        }

        AIRacingLine line = lineManager.getRacingLine(trackName);
        if (line == null || !line.isUsable()) {
            return null;
        }

        return new AILineCache(
            heat,
            line.getIdealLine(),
            line.getBrakingPoints(),
            line.getAccelerationPoints()
        );
    }

    private void startTask() {
        SchedulerHelper.runTaskTimer(plugin, (scheduledTask) -> {
            if (activeHeats.isEmpty()) {
                return;
            }

            for (AILineCache cache : activeHeats.values()) {
                Set<UUID> rendered = new HashSet<>();
                // Players in the heat
                for (Driver driver : cache.heat.getDrivers().values()) {
                    Player p = plugin.getServer().getPlayer(driver.getUuid());
                    if (p != null && p.isOnline()) {
                        renderForPlayer(p, cache);
                        rendered.add(p.getUniqueId());
                    }
                }
                // Spectators bound to the heat
                if (plugin.getSpectatorManager() != null && cache.heat.getRound() != null) {
                    Events event = cache.heat.getRound().getEvent();
                    if (event != null) {
                        for (Spectator spec : plugin.getSpectatorManager().getEventSpectators(event)) {
                            if (rendered.contains(spec.getUuid())) {
                                continue;
                            }
                            if (plugin.getSpectatorManager().getSpectatorBoundHeat(spec.getUuid()) == null) {
                                continue;
                            }
                            if (plugin.getSpectatorManager().getSpectatorBoundHeat(spec.getUuid()).getId() != cache.heat.getId()) {
                                continue;
                            }
                            Player p = plugin.getServer().getPlayer(spec.getUuid());
                            if (p != null && p.isOnline()) {
                                renderForPlayer(p, cache);
                            }
                        }
                    }
                }
            }
        }, 0L, 10L);
    }

    private void renderForPlayer(Player player, AILineCache cache) {
        String worldName = player.getWorld().getName();
        Location playerLoc = player.getLocation();

        Particle.DustOptions idealDust = new Particle.DustOptions(IDEAL_COLOR, 1.0F);
        Particle.DustOptions brakeDust = new Particle.DustOptions(BRAKE_COLOR, 1.2F);
        Particle.DustOptions accelDust = new Particle.DustOptions(ACCEL_COLOR, 1.2F);

        double y = playerLoc.getY();
        for (Location loc : cache.ideal) {
            if (loc.getWorld() == null || !loc.getWorld().getName().equalsIgnoreCase(worldName)) {
                continue;
            }
            if (playerLoc.distanceSquared(loc) < RENDER_DISTANCE_SQ) {
                spawnLinePoint(player, loc, idealDust);
            }
        }

        for (Location loc : cache.braking) {
            if (loc.getWorld() == null || !loc.getWorld().getName().equalsIgnoreCase(worldName)) {
                continue;
            }
            if (playerLoc.distanceSquared(loc) < RENDER_DISTANCE_SQ) {
                spawnLinePoint(player, loc, brakeDust);
            }
        }

        for (Location loc : cache.acceleration) {
            if (loc.getWorld() == null || !loc.getWorld().getName().equalsIgnoreCase(worldName)) {
                continue;
            }
            if (playerLoc.distanceSquared(loc) < RENDER_DISTANCE_SQ) {
                spawnLinePoint(player, loc, accelDust);
            }
        }
    }

    private void spawnLinePoint(Player player, Location loc, Particle.DustOptions dust) {
        Location at = new Location(loc.getWorld(), loc.getX(), loc.getY() + 0.2, loc.getZ());
        player.spawnParticle(Particle.DUST, at, 1, dust);
    }

    private static class AILineCache {
        final Heats heat;
        final List<Location> ideal;
        final List<Location> braking;
        final List<Location> acceleration;

        AILineCache(Heats heat, List<Location> ideal, List<Location> braking, List<Location> acceleration) {
            this.heat = heat;
            this.ideal = ideal;
            this.braking = braking;
            this.acceleration = acceleration;
        }

        boolean isEmpty() {
            return ideal.isEmpty() && braking.isEmpty() && acceleration.isEmpty();
        }
    }
}
