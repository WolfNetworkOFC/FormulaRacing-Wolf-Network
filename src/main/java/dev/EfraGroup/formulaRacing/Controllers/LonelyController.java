package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.BoatUtils.NocolManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Duels.TimeTrialDuels;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import org.bukkit.Bukkit;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.scoreboard.Team.Option;
import org.bukkit.scoreboard.Team.OptionStatus;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LonelyController implements Listener {
    private static final long VISIBILITY_UPDATE_DELAY = 1L;

    private final DatabaseManager databaseManager;
    private final FormulaRacing plugin;
    private final Map<UUID, Boolean> lonelyCache = new ConcurrentHashMap<>();
    private final Set<UUID> ghostedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> obuInfoShown = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> collisionReasonCache = new ConcurrentHashMap<>();

    private volatile boolean emergencyMode;

    public LonelyController(DatabaseManager databaseManager, FormulaRacing plugin) {
        this.databaseManager = databaseManager;
        this.plugin = plugin;
    }

    public void setLonelyMode(Player player, boolean enabled) {
        this.databaseManager.setLonelyModePlayer(player.getUniqueId(), enabled);
        this.lonelyCache.put(player.getUniqueId(), enabled);
        this.reconcilePlayer(player);
    }

    public boolean isLonely(UUID uuid) {
        return this.lonelyCache.computeIfAbsent(uuid, this.databaseManager::getLonelyModePlayer);
    }

    public boolean isGhosted(UUID uuid) {
        return this.ghostedPlayers.contains(uuid);
    }

    public boolean ghost(UUID uuid) {
        boolean added = this.ghostedPlayers.add(uuid);
        if (added) {
            this.reconcileAll();
        }

        return added;
    }

    public boolean unghost(UUID uuid) {
        boolean removed = this.ghostedPlayers.remove(uuid);
        if (removed) {
            this.reconcileAll();
        }

        return removed;
    }

    public boolean clearGhost(UUID uuid) {
        return this.unghost(uuid);
    }

    public void clearGhostForPlayers(Iterable<UUID> uuids) {
        boolean changed = false;

        for (UUID uuid : uuids) {
            changed |= this.ghostedPlayers.remove(uuid);
        }

        if (changed) {
            this.reconcileAll();
        }
    }

    public boolean isEmergencyMode() {
        return this.emergencyMode;
    }

    public void setEmergencyMode(boolean enabled, String actorName) {
        boolean changed = this.emergencyMode != enabled;
        this.emergencyMode = enabled;
        if (changed) {
            String mode = enabled ? "ON" : "OFF";
            this.plugin.getDebugManager().logRaceSystem("[LONELY EMERGENCY] " + actorName + " -> " + mode);
            this.reconcileAll();
        }
    }

    public String getPlayerStateDebug(Player player) {
        PlayerContext context = this.resolveContext(player);
        String reason = this.collisionReasonCache.getOrDefault(player.getUniqueId(), "NONE");
        return "domain=" + context.domain +
                ", lonely=" + this.isLonely(player.getUniqueId()) +
                ", ghosted=" + this.isGhosted(player.getUniqueId()) +
                ", emergency=" + this.emergencyMode +
                ", reason=" + reason;
    }

    public void reconcileAll() {
        this.scheduleReconcile(() -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                this.reconcileViewerImmediate(online);
            }
        });
    }

    public void reconcilePlayer(Player player) {
        this.updatePlayersVisibility(player);
        this.updatePlayerVisibility(player);
    }

    public void updatePlayersVisibility(Player player) {
        this.scheduleReconcile(() -> this.reconcileViewerImmediate(player));
    }

    public void updatePlayerVisibility(Player target) {
        this.scheduleReconcile(() -> this.reconcileTargetImmediate(target));
    }

    private void reconcileViewerImmediate(Player viewer) {
        if (!viewer.isOnline()) {
            return;
        }

        PlayerContext viewerContext = this.resolveContext(viewer);
        this.applyCollision(viewer, viewerContext);

        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(viewer)) {
                continue;
            }

            PlayerContext targetContext = this.resolveContext(target);
            VisibilityDecision decision = this.decideVisibility(viewer, target, viewerContext, targetContext);
            this.applyVisibility(viewer, target, decision);
        }
    }

    private void reconcileTargetImmediate(Player target) {
        if (!target.isOnline()) {
            return;
        }

        PlayerContext targetContext = this.resolveContext(target);
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) {
                continue;
            }

            PlayerContext viewerContext = this.resolveContext(viewer);
            VisibilityDecision decision = this.decideVisibility(viewer, target, viewerContext, targetContext);
            this.applyVisibility(viewer, target, decision);
        }
    }

    private VisibilityDecision decideVisibility(Player viewer, Player target, PlayerContext viewerContext, PlayerContext targetContext) {
        if (!viewer.getWorld().equals(target.getWorld())) {
            return VisibilityDecision.hide("DIFFERENT_WORLD");
        }

        if (this.emergencyMode) {
            return VisibilityDecision.show("EMERGENCY_SHOW_ALL");
        }

        if (this.isGhosted(target.getUniqueId())) {
            return VisibilityDecision.hide("MANUAL_GHOST");
        }

        return switch (viewerContext.domain) {
            case HEAT_ACTIVE -> this.decideHeatVisibility(viewerContext, targetContext);
            case DUEL_ACTIVE -> this.decideDuelVisibility(viewerContext, targetContext);
            case TIMETRIAL_ACTIVE -> this.decideTimeTrialVisibility(viewerContext, targetContext);
            case OPEN_WORLD -> this.decideOpenWorldVisibility(viewer, targetContext);
        };
    }

    private VisibilityDecision decideHeatVisibility(PlayerContext viewerContext, PlayerContext targetContext) {
        if (viewerContext.heat == null) {
            return VisibilityDecision.hide("HEAT_CONTEXT_MISSING");
        }

        if (targetContext.domain != Domain.HEAT_ACTIVE || targetContext.heat == null || targetContext.heat.getId() != viewerContext.heat.getId()) {
            return VisibilityDecision.hide("HEAT_ISOLATION");
        }

        if (viewerContext.heat.isLonely()) {
            return VisibilityDecision.hide("HEAT_LONELY");
        }

        return VisibilityDecision.show("HEAT_VISIBLE");
    }

    private VisibilityDecision decideDuelVisibility(PlayerContext viewerContext, PlayerContext targetContext) {
        if (viewerContext.duelId == -1) {
            return VisibilityDecision.hide("DUEL_CONTEXT_MISSING");
        }

        if (targetContext.domain != Domain.DUEL_ACTIVE || viewerContext.duelId != targetContext.duelId) {
            return VisibilityDecision.hide("DUEL_ISOLATION");
        }

        if (viewerContext.duelLonely) {
            return VisibilityDecision.hide("DUEL_LONELY");
        }

        return VisibilityDecision.show("DUEL_VISIBLE");
    }

    private VisibilityDecision decideTimeTrialVisibility(PlayerContext viewerContext, PlayerContext targetContext) {
        if (viewerContext.timeTrialTrack == null) {
            return VisibilityDecision.hide("TIMETRIAL_CONTEXT_MISSING");
        }

        if (targetContext.domain != Domain.TIMETRIAL_ACTIVE || targetContext.timeTrialTrack == null) {
            return VisibilityDecision.hide("TIMETRIAL_ISOLATION");
        }

        if (!viewerContext.timeTrialTrack.equalsIgnoreCase(targetContext.timeTrialTrack)) {
            return VisibilityDecision.hide("TIMETRIAL_TRACK_MISMATCH");
        }

        return VisibilityDecision.show("TIMETRIAL_VISIBLE");
    }

    private VisibilityDecision decideOpenWorldVisibility(Player viewer, PlayerContext targetContext) {
        if (targetContext.domain != Domain.OPEN_WORLD) {
            return VisibilityDecision.hide("OPEN_WORLD_ISOLATION");
        }

        if (this.isLonely(viewer.getUniqueId())) {
            return VisibilityDecision.hide("PLAYER_LONELY");
        }

        return VisibilityDecision.show("OPEN_WORLD_VISIBLE");
    }

    private void applyCollision(Player player, PlayerContext context) {
        if (!this.isPlayerInBoat(player)) {
            this.setVanillaCollision(player, false);
            this.collisionReasonCache.put(player.getUniqueId(), "NOT_IN_BOAT");
            return;
        }

        if (this.emergencyMode) {
            this.setVanillaCollision(player, false);
            NocolManager.setCollisionMode(player, true);
            this.collisionReasonCache.put(player.getUniqueId(), "EMERGENCY_VANILLA");
            return;
        }

        switch (context.domain) {
            case HEAT_ACTIVE -> {
                if (context.heat == null) {
                    this.setVanillaCollision(player, false);
                    NocolManager.setCollisionMode(player, true);
                    this.collisionReasonCache.put(player.getUniqueId(), "HEAT_CONTEXT_MISSING");
                    return;
                }

                boolean practiceOrQualifying = context.heatState == HeatState.PRACTICE || context.heatState == HeatState.QUALIFYING;
                boolean hasObu = FormulaRacing.hasOpenBoatUtilsMod(player);
                if (context.heat.isLonely()) {
                    this.setVanillaCollision(player, true);
                    NocolManager.setCollisionMode(player, false);
                    this.collisionReasonCache.put(player.getUniqueId(), "HEAT_LONELY");
                } else if (practiceOrQualifying && hasObu) {
                    this.setVanillaCollision(player, true);
                    NocolManager.setCollisionMode(player, false);
                    this.collisionReasonCache.put(player.getUniqueId(), "HEAT_PRACTICE_QUALIFYING");
                } else {
                    this.setVanillaCollision(player, false);
                    NocolManager.setCollisionMode(player, true);
                    this.collisionReasonCache.put(player.getUniqueId(), "HEAT_VANILLA");
                }
            }
            case DUEL_ACTIVE, TIMETRIAL_ACTIVE -> {
                this.setVanillaCollision(player, true);
                NocolManager.setCollisionMode(player, false);
                this.collisionReasonCache.put(player.getUniqueId(), context.domain == Domain.DUEL_ACTIVE ? "DUEL_ISOLATED" : "TIMETRIAL_ISOLATED");
            }
            case OPEN_WORLD -> {
                if (this.isLonely(player.getUniqueId())) {
                    this.setVanillaCollision(player, true);
                    NocolManager.setCollisionMode(player, false);
                    this.collisionReasonCache.put(player.getUniqueId(), "OPEN_WORLD_LONELY");
                } else {
                    this.setVanillaCollision(player, false);
                    NocolManager.setCollisionMode(player, true);
                    this.collisionReasonCache.put(player.getUniqueId(), "OPEN_WORLD_VANILLA");
                }
            }
        }
    }

    private void applyVisibility(Player viewer, Player target, VisibilityDecision decision) {
        if (decision.show) {
            this.showPlayer(viewer, target);
        } else {
            this.hidePlayer(viewer, target);
        }
    }

    private PlayerContext resolveContext(Player player) {
        UUID uuid = player.getUniqueId();

        Optional<Heats> heatOpt = this.plugin.getRaceEventManager().getPlayerActiveHeat(uuid);
        if (heatOpt.isPresent()) {
            Heats heat = heatOpt.get();
            if (heat.isPlayerActivelyRacing(uuid)) {
                return PlayerContext.heat(heat);
            }
        }

        int duelId = this.plugin.getTimeTrialDuels() != null ? this.plugin.getTimeTrialDuels().getActiveDuelIdCached(uuid) : -1;
        if (duelId != -1) {
            TimeTrialDuels.DuelState duelState = this.plugin.getTimeTrialDuels().getDuelState(duelId);
            boolean duelLonely = duelState != null && duelState.isLonely();
            return PlayerContext.duel(duelId, duelLonely);
        }

        String activeTrack = this.plugin.getTimerUtils().getActiveTrack(player);
        if (activeTrack != null) {
            return PlayerContext.timeTrial(activeTrack);
        }

        return PlayerContext.openWorld();
    }

    private void showPlayer(Player viewer, Player target) {
        viewer.showPlayer(this.plugin, target);
        Entity vehicle = target.getVehicle();
        if (vehicle != null) {
            viewer.showEntity(this.plugin, vehicle);
            if (this.plugin.getConfig().getBoolean("FrostHexAddOn", false) && !vehicle.getPassengers().isEmpty()) {
                for (Entity entity : vehicle.getPassengers()) {
                    viewer.showEntity(this.plugin, entity);
                }
            }
        }
    }

    private void hidePlayer(Player viewer, Player target) {
        viewer.hidePlayer(this.plugin, target);
        Entity vehicle = target.getVehicle();
        if (vehicle != null) {
            viewer.hideEntity(this.plugin, vehicle);
            if (this.plugin.getConfig().getBoolean("FrostHexAddOn", false) && !vehicle.getPassengers().isEmpty()) {
                for (Entity entity : vehicle.getPassengers()) {
                    viewer.hideEntity(this.plugin, entity);
                }
            }
        }
    }

    private boolean isPlayerInBoat(Player player) {
        Entity vehicle = player.getVehicle();
        return vehicle instanceof Boat || vehicle instanceof ChestBoat;
    }

    private void scheduleReconcile(Runnable runnable) {
        Bukkit.getScheduler().runTaskLater(this.plugin, runnable, VISIBILITY_UPDATE_DELAY);
    }

    public boolean hasSeenObuInfo(UUID uuid) {
        return this.obuInfoShown.contains(uuid);
    }

    public void markObuInfoShown(UUID uuid) {
        this.obuInfoShown.add(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        this.isLonely(player.getUniqueId());
        this.reconcilePlayer(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        this.lonelyCache.remove(uuid);
        this.ghostedPlayers.remove(uuid);
        this.obuInfoShown.remove(uuid);
        this.collisionReasonCache.remove(uuid);
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        Entity entered = event.getEntered();
        if (entered instanceof Player player) {
            this.reconcilePlayer(player);
        }
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        LivingEntity exited = event.getExited();
        if (exited instanceof Player player) {
            this.reconcilePlayer(player);
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        this.reconcilePlayer(event.getPlayer());
    }

    private void setVanillaCollision(Player player, boolean preventCollision) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam("fr_nocol");
        if (team == null) {
            team = scoreboard.registerNewTeam("fr_nocol");
            team.setOption(Option.COLLISION_RULE, OptionStatus.NEVER);
            team.setCanSeeFriendlyInvisibles(false);
            this.plugin.getDebugManager().logPacketHandling("[FormulaRacing] Time 'fr_nocol' criado no MainScoreboard.");
        }

        if (preventCollision) {
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }
        } else if (team.hasEntry(player.getName())) {
            team.removeEntry(player.getName());
        }
    }

    private enum Domain {
        HEAT_ACTIVE,
        DUEL_ACTIVE,
        TIMETRIAL_ACTIVE,
        OPEN_WORLD
    }

    private static final class PlayerContext {
        private final Domain domain;
        private final Heats heat;
        private final HeatState heatState;
        private final int duelId;
        private final boolean duelLonely;
        private final String timeTrialTrack;

        private PlayerContext(Domain domain, Heats heat, HeatState heatState, int duelId, boolean duelLonely, String timeTrialTrack) {
            this.domain = domain;
            this.heat = heat;
            this.heatState = heatState;
            this.duelId = duelId;
            this.duelLonely = duelLonely;
            this.timeTrialTrack = timeTrialTrack;
        }

        private static PlayerContext heat(Heats heat) {
            return new PlayerContext(Domain.HEAT_ACTIVE, heat, heat.getHeatState(), -1, false, null);
        }

        private static PlayerContext duel(int duelId, boolean duelLonely) {
            return new PlayerContext(Domain.DUEL_ACTIVE, null, null, duelId, duelLonely, null);
        }

        private static PlayerContext timeTrial(String trackName) {
            return new PlayerContext(Domain.TIMETRIAL_ACTIVE, null, null, -1, false, trackName);
        }

        private static PlayerContext openWorld() {
            return new PlayerContext(Domain.OPEN_WORLD, null, null, -1, false, null);
        }
    }

    private static final class VisibilityDecision {
        private final boolean show;
        private final String reasonCode;

        private VisibilityDecision(boolean show, String reasonCode) {
            this.show = show;
            this.reasonCode = reasonCode;
        }

        private static VisibilityDecision show(String reasonCode) {
            return new VisibilityDecision(true, reasonCode);
        }

        private static VisibilityDecision hide(String reasonCode) {
            return new VisibilityDecision(false, reasonCode);
        }
    }
}
