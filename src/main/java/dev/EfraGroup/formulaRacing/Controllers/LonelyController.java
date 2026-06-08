package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.BoatUtils.NocolManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Heat.CollisionMode;
import dev.EfraGroup.formulaRacing.Loneliness.ScopeResolver;
import dev.EfraGroup.formulaRacing.Loneliness.VisibilityScope;
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

/**
 * Controls player visibility and boat collision based on active racing sessions.
 *
 * <p>Policy (mirrors TimingSystem's LonelinessController):
 * <ol>
 *   <li><b>Emergency mode ON</b> → show everyone, no session isolation.</li>
 *   <li><b>Viewer NOT in a boat</b> → show all others (walking spectators see everything).</li>
 *   <li><b>Viewer in boat, no active session</b> → if canUseNocol &amp;&amp; !personalLonely: show all + nocol;
 *       otherwise hide all (vanilla collision).</li>
 *   <li><b>Viewer in boat, inside a session</b> → show only session participants;
 *       apply collision based on session's CollisionMode.</li>
 * </ol>
 *
 * <p>Manual ghost and {@link #isGhosted} act as final-filter overrides on top of the policy.
 */
public class LonelyController implements Listener {

    private static final long VISIBILITY_UPDATE_DELAY = 1L;

    private final DatabaseManager databaseManager;
    private final FormulaRacing plugin;
    private final ScopeResolver scopeResolver;

    private final Map<UUID, Boolean> lonelyCache = new ConcurrentHashMap<>();
    private final Set<UUID> ghostedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> obuInfoShown = ConcurrentHashMap.newKeySet();

    private volatile boolean emergencyMode;

    public LonelyController(DatabaseManager databaseManager, FormulaRacing plugin) {
        this.databaseManager = databaseManager;
        this.plugin = plugin;
        this.scopeResolver = new ScopeResolver(plugin);
    }

    // -------------------------------------------------------------------------
    // Public API — kept stable so existing call-sites compile unchanged
    // -------------------------------------------------------------------------

    public void setLonelyMode(Player player, boolean enabled) {
        this.databaseManager.setLonelyModePlayer(player.getUniqueId(), enabled);
        this.lonelyCache.put(player.getUniqueId(), enabled);
        this.updatePlayersVisibility(player);
    }

    public boolean isLonely(UUID uuid) {
        return this.lonelyCache.getOrDefault(uuid, false);
    }

    public boolean isGhosted(UUID uuid) {
        return this.ghostedPlayers.contains(uuid);
    }

    public boolean ghost(UUID uuid) {
        boolean added = this.ghostedPlayers.add(uuid);
        if (added) {
            Player target = Bukkit.getPlayer(uuid);
            if (target != null) {
                this.updatePlayerVisibility(target);
            }
        }
        return added;
    }

    public boolean unghost(UUID uuid) {
        boolean removed = this.ghostedPlayers.remove(uuid);
        if (removed) {
            Player target = Bukkit.getPlayer(uuid);
            if (target != null) {
                this.updatePlayerVisibility(target);
            }
        }
        return removed;
    }

    public boolean clearGhost(UUID uuid) {
        return this.unghost(uuid);
    }

    public void clearGhostForPlayers(Iterable<UUID> uuids) {
        for (UUID uuid : uuids) {
            boolean removed = this.ghostedPlayers.remove(uuid);
            if (removed) {
                Player target = Bukkit.getPlayer(uuid);
                if (target != null) {
                    this.updatePlayerVisibility(target);
                }
            }
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
            this.scheduleReconcile(() -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    this.processViewerVisibility(online);
                }
            });
        }
    }

    public String getPlayerStateDebug(Player player) {
        Optional<VisibilityScope> scope = this.scopeResolver.resolve(player);
        boolean inBoat = this.isPlayerInBoat(player);
        boolean canNocol = NocolManager.playerHasMod(player);
        return "inBoat=" + inBoat +
                ", scope=" + scope.map(s -> s.getClass().getSimpleName() + "#" + s.getId()).orElse("OPEN_WORLD") +
                ", personalLonely=" + this.isLonely(player.getUniqueId()) +
                ", ghosted=" + this.isGhosted(player.getUniqueId()) +
                ", emergency=" + this.emergencyMode +
                ", canNocol=" + canNocol;
    }

    /**
     * Recalculates what {@code viewer} can see (and their collision mode).
     * Equivalent to TS {@code updatePlayersVisibility}.
     */
    public void updatePlayersVisibility(Player viewer) {
        this.scheduleReconcile(() -> this.processViewerVisibility(viewer));
    }

    /**
     * Recalculates how every online viewer perceives {@code target}.
     * Equivalent to TS {@code updatePlayerVisibility}.
     */
    public void updatePlayerVisibility(Player target) {
        this.scheduleReconcile(() -> {
            if (!target.isOnline()) return;
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(target)) continue;
                this.applyVisibilityForViewer(viewer, target);
            }
        });
    }

    /** Convenience: refresh both directions for a single player. */
    public void reconcilePlayer(Player player) {
        this.updatePlayersVisibility(player);
        this.updatePlayerVisibility(player);
    }

    public boolean hasSeenObuInfo(UUID uuid) {
        return this.obuInfoShown.contains(uuid);
    }

    public void markObuInfoShown(UUID uuid) {
        this.obuInfoShown.add(uuid);
    }

    // -------------------------------------------------------------------------
    // Core policy engine
    // -------------------------------------------------------------------------

    /**
     * Applies the full visibility + collision policy for {@code viewer} against all other
     * online players.  Mirrors TS {@code updatePlayersVisibility} lambda.
     */
    private void processViewerVisibility(Player viewer) {
        if (!viewer.isOnline()) return;

        // --- Collision for the viewer themselves ---
        if (isPlayerInBoat(viewer)) {
            applyCollisionForViewer(viewer);
        } else {
            // Not in a boat: remove from nocol scoreboard team, no OBU packet needed
            setVanillaCollision(viewer, false);
        }

        // --- Visibility of every other player ---
        if (emergencyMode) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(viewer)) showPlayer(viewer, other);
            }
            return;
        }

        if (!isPlayerInBoat(viewer)) {
            // Walking player: show everyone regardless of their session
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(viewer)) showPlayer(viewer, other);
            }
            return;
        }

        boolean canNocol = NocolManager.playerHasMod(viewer);
        boolean personalLonely = isLonely(viewer.getUniqueId());
        Optional<VisibilityScope> scopeOpt = scopeResolver.resolve(viewer);

        if (scopeOpt.isEmpty()) {
            // Open-world boat rider
            if (canNocol && !personalLonely) {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.equals(viewer)) showPlayer(viewer, other);
                }
            } else {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.equals(viewer)) hidePlayer(viewer, other);
                }
            }
            return;
        }

        VisibilityScope scope = scopeOpt.get();
        Set<UUID> participants = scope.getParticipants();

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(viewer)) continue;
            if (ghostedPlayers.contains(other.getUniqueId())) {
                hidePlayer(viewer, other);
                continue;
            }
            boolean inScope = participants.contains(other.getUniqueId());
            if (!inScope) {
                hidePlayer(viewer, other);
                continue;
            }
            if (scope.isIsolated()) {
                hidePlayer(viewer, other);
            } else {
                showPlayer(viewer, other);
            }
        }
    }

    /**
     * Updates only how {@code viewer} sees {@code target}, without touching
     * other pairs.  Used by {@link #updatePlayerVisibility}.
     */
    private void applyVisibilityForViewer(Player viewer, Player target) {
        if (!viewer.isOnline() || !target.isOnline()) return;

        if (!viewer.getWorld().equals(target.getWorld())) {
            hidePlayer(viewer, target);
            return;
        }

        if (emergencyMode) {
            showPlayer(viewer, target);
            return;
        }

        if (ghostedPlayers.contains(target.getUniqueId())) {
            hidePlayer(viewer, target);
            return;
        }

        // Walking viewers see everything
        if (!isPlayerInBoat(viewer)) {
            showPlayer(viewer, target);
            return;
        }

        boolean canNocol = NocolManager.playerHasMod(viewer);
        boolean personalLonely = isLonely(viewer.getUniqueId());
        Optional<VisibilityScope> scopeOpt = scopeResolver.resolve(viewer);

        if (scopeOpt.isEmpty()) {
            if (canNocol && !personalLonely) {
                showPlayer(viewer, target);
            } else {
                hidePlayer(viewer, target);
            }
            return;
        }

        VisibilityScope scope = scopeOpt.get();
        boolean inScope = scope.getParticipants().contains(target.getUniqueId());
        if (!inScope) {
            hidePlayer(viewer, target);
            return;
        }
        if (scope.isIsolated()) {
            hidePlayer(viewer, target);
        } else {
            showPlayer(viewer, target);
        }
    }

    // -------------------------------------------------------------------------
    // Collision helpers
    // -------------------------------------------------------------------------

    private void applyCollisionForViewer(Player player) {
        if (emergencyMode) {
            // Emergency: use vanilla nocol scoreboard team approach, disable OBU nocol
            setVanillaCollision(player, false);
            NocolManager.setCollisionMode(player, true);
            return;
        }

        Optional<VisibilityScope> scopeOpt = scopeResolver.resolve(player);
        boolean canNocol = NocolManager.playerHasMod(player);

        if (scopeOpt.isEmpty()) {
            // Open-world
            boolean personalLonely = isLonely(player.getUniqueId());
            if (canNocol && !personalLonely) {
                setVanillaCollision(player, false);
                NocolManager.setCollisionMode(player, false);
            } else {
                setVanillaCollision(player, false);
                NocolManager.setCollisionMode(player, true);
            }
            return;
        }

        VisibilityScope scope = scopeOpt.get();
        CollisionMode mode = scope.getCollisionMode();

        switch (mode) {
            case HIGH -> {
                // Full vanilla collision
                setVanillaCollision(player, false);
                NocolManager.setCollisionMode(player, true);
            }
            case LOW -> {
                // Filtered: use OBU nocol if available, else vanilla
                if (canNocol) {
                    setVanillaCollision(player, false);
                    NocolManager.setCollisionMode(player, false);
                } else {
                    setVanillaCollision(player, false);
                    NocolManager.setCollisionMode(player, true);
                }
            }
            case DISABLED -> {
                // No collision — also covers heat-lonely, duel, timetrial
                setVanillaCollision(player, true);
                NocolManager.setCollisionMode(player, false);
            }
        }
    }

    // -------------------------------------------------------------------------
    // show / hide helpers
    // -------------------------------------------------------------------------

    private void showPlayer(Player viewer, Player target) {
        viewer.showPlayer(plugin, target);
        Entity vehicle = target.getVehicle();
        if (vehicle != null) {
            viewer.showEntity(plugin, vehicle);
            if (plugin.getConfig().getBoolean("FrostHexAddOn", false)) {
                for (Entity passenger : vehicle.getPassengers()) {
                    viewer.showEntity(plugin, passenger);
                }
            }
        }
    }

    private void hidePlayer(Player viewer, Player target) {
        viewer.hidePlayer(plugin, target);
        Entity vehicle = target.getVehicle();
        if (vehicle != null) {
            viewer.hideEntity(plugin, vehicle);
            if (plugin.getConfig().getBoolean("FrostHexAddOn", false)) {
                for (Entity passenger : vehicle.getPassengers()) {
                    viewer.hideEntity(plugin, passenger);
                }
            }
        }
    }

    private boolean isPlayerInBoat(Player player) {
        Entity vehicle = player.getVehicle();
        return vehicle instanceof Boat || vehicle instanceof ChestBoat;
    }

    private void scheduleReconcile(Runnable runnable) {
        SchedulerHelper.runTaskLater(plugin, runnable, VISIBILITY_UPDATE_DELAY);
    }

    // -------------------------------------------------------------------------
    // Scoreboard vanilla-collision team
    // -------------------------------------------------------------------------

    private void setVanillaCollision(Player player, boolean preventCollision) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam("fr_nocol");
        if (team == null) {
            team = scoreboard.registerNewTeam("fr_nocol");
            team.setOption(Option.COLLISION_RULE, OptionStatus.NEVER);
            team.setCanSeeFriendlyInvisibles(false);
            plugin.getDebugManager().logPacketHandling("[FormulaRacing] Time 'fr_nocol' criado no MainScoreboard.");
        }
        if (preventCollision) {
            if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
        } else {
            if (team.hasEntry(player.getName())) team.removeEntry(player.getName());
        }
    }

    // -------------------------------------------------------------------------
    // Event listeners
    // -------------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Reset personal-lonely to false on every join — avoids "stuck invisible" bug.
        lonelyCache.put(player.getUniqueId(), false);
        reconcilePlayer(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lonelyCache.remove(uuid);
        ghostedPlayers.remove(uuid);
        obuInfoShown.remove(uuid);
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        Entity entered = event.getEntered();
        if (entered instanceof Player player) {
            reconcilePlayer(player);
        }
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        LivingEntity exited = event.getExited();
        if (exited instanceof Player player) {
            reconcilePlayer(player);
        }
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        reconcilePlayer(event.getPlayer());
    }
}
