package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.BoatUtils.NocolManager;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.PlatformUtils;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Heat.CollisionMode;
import dev.EfraGroup.formulaRacing.Loneliness.ScopeResolver;
import dev.EfraGroup.formulaRacing.Loneliness.VisibilityScope;
import org.bukkit.Bukkit;
import org.bukkit.World;
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
        this.scheduleReconcile(viewer, () -> this.processViewerVisibility(viewer));
    }

    /**
     * Recalculates how every online viewer perceives {@code target}.
     * Equivalent to TS {@code updatePlayerVisibility}.
     */
    public void updatePlayerVisibility(Player target) {
        this.scheduleReconcile(target, () -> {
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

    /**
     * Called when a driver finishes a heat race. Refreshes visibility so that
     * active racers can no longer see the finished driver, while spectators and
     * out-of-race players still can.
     */
    public void onDriverFinished(Heats heat, Player finishedPlayer) {
        // Recalculate how every viewer perceives the finished player
        this.updatePlayerVisibility(finishedPlayer);
        // Recalculate what the finished player now sees (they've left the active-racer scope)
        this.updatePlayersVisibility(finishedPlayer);

        // Also nudge all other drivers in the heat so their own visibility recalculates
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(finishedPlayer)) continue;
            if (heat.getDriver(viewer.getUniqueId()) != null) {
                this.updatePlayersVisibility(viewer);
            }
        }
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
     * online players.
     *
     * <p>Regras de visibilidade:
     * <ul>
     *   <li>Pessoas só são ocultadas se o viewer tiver lonely ativado</li>
     *   <li>Heat lonely DESLIGADO: mostra só participantes do heat (independente do personalLonely)</li>
     *   <li>Heat lonely LIGADO + personalLonely DESLIGADO + tem mod: mostra participantes + só remove colisão</li>
     *   <li>Heat lonely LIGADO + personalLonely DESLIGADO + sem mod: oculta todos (fallback)</li>
     *   <li>Heat lonely LIGADO + personalLonely LIGADO: oculta TODOS (inclusive participantes)</li>
     *   <li>Permite mudar lonely no meio do heat</li>
     * </ul>
     */
    private void processViewerVisibility(Player viewer) {
        if (!viewer.isOnline()) return;

        // Collision for the viewer
        if (isPlayerInBoat(viewer)) {
            applyCollisionForViewer(viewer);
        }

        // Emergency mode: show all
        if (emergencyMode) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(viewer)) showPlayer(viewer, other);
            }
            return;
        }

        // Walking player: show all
        if (!isPlayerInBoat(viewer)) {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.equals(viewer)) showPlayer(viewer, other);
            }
            return;
        }

        // --- In a boat ---
        boolean personalLonely = isLonely(viewer.getUniqueId());
        Optional<VisibilityScope> scopeOpt = scopeResolver.resolve(viewer);

        if (scopeOpt.isEmpty()) {
            // Open world: hide only if personalLonely
            if (personalLonely) {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.equals(viewer)) hidePlayer(viewer, other);
                }
            } else {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (!other.equals(viewer)) showPlayer(viewer, other);
                }
            }
            return;
        }

        // --- Inside a scope (heat/duel/timetrial) ---
        VisibilityScope scope = scopeOpt.get();
        Set<UUID> participants = scope.getParticipants();
        boolean heatLonely = scope.isIsolated();
        boolean canNocol = NocolManager.playerHasMod(viewer);

        Set<UUID> finishedParticipants = scope.getFinishedParticipants();
        boolean viewerIsActiveRacer = scope.isActiveRacer(viewer.getUniqueId());

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(viewer)) continue;

            // Ghosted players are always hidden
            if (ghostedPlayers.contains(other.getUniqueId())) {
                hidePlayer(viewer, other);
                continue;
            }

            // Active racers should not see finished drivers in the same heat
            if (viewerIsActiveRacer && finishedParticipants.contains(other.getUniqueId())) {
                hidePlayer(viewer, other);
                continue;
            }

            boolean inScope = participants.contains(other.getUniqueId());

            if (!heatLonely) {
                // Heat NOT lonely: show ONLY participants (regardless of personalLonely)
                if (inScope) {
                    showPlayer(viewer, other);
                } else {
                    hidePlayer(viewer, other);
                }
            } else {
                // Heat IS lonely
                if (personalLonely) {
                    // Personal lonely + heat lonely = hide EVERYONE
                    hidePlayer(viewer, other);
                } else {
                    // Personal NOT lonely, heat IS lonely
                    if (canNocol) {
                        // Has mod: show participants (collision will be removed via packet)
                        if (inScope) {
                            showPlayer(viewer, other);
                        } else {
                            hidePlayer(viewer, other);
                        }
                    } else {
                        // No mod: hide all (can't use nocol for collision removal)
                        hidePlayer(viewer, other);
                    }
                }
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

        if (!isPlayerInBoat(viewer)) {
            showPlayer(viewer, target);
            return;
        }

        boolean personalLonely = isLonely(viewer.getUniqueId());
        Optional<VisibilityScope> scopeOpt = scopeResolver.resolve(viewer);

        if (scopeOpt.isEmpty()) {
            // Open world
            if (personalLonely) {
                hidePlayer(viewer, target);
            } else {
                showPlayer(viewer, target);
            }
            return;
        }

        // --- Inside a scope ---
        VisibilityScope scope = scopeOpt.get();
        Set<UUID> participants = scope.getParticipants();
        boolean heatLonely = scope.isIsolated();
        boolean canNocol = NocolManager.playerHasMod(viewer);
        boolean inScope = participants.contains(target.getUniqueId());

        // Active racers should not see finished drivers in the same heat
        Set<UUID> finishedParticipants = scope.getFinishedParticipants();
        if (scope.isActiveRacer(viewer.getUniqueId()) && finishedParticipants.contains(target.getUniqueId())) {
            hidePlayer(viewer, target);
            return;
        }

        if (!heatLonely) {
            // Heat NOT lonely: show only participants
            if (inScope) {
                showPlayer(viewer, target);
            } else {
                hidePlayer(viewer, target);
            }
        } else {
            // Heat IS lonely
            if (personalLonely) {
                hidePlayer(viewer, target);
            } else {
                if (canNocol) {
                    if (inScope) {
                        showPlayer(viewer, target);
                    } else {
                        hidePlayer(viewer, target);
                    }
                } else {
                    hidePlayer(viewer, target);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Collision helpers
    // -------------------------------------------------------------------------

    private void applyCollisionForViewer(Player player) {
        if (emergencyMode) {
            setVanillaCollision(player, false);
            NocolManager.setCollisionMode(player, true);
            return;
        }

        boolean personalLonely = isLonely(player.getUniqueId());
        Optional<VisibilityScope> scopeOpt = scopeResolver.resolve(player);
        boolean canNocol = NocolManager.playerHasMod(player);

        if (scopeOpt.isEmpty()) {
            // Open-world: nocol if has mod and not lonely
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
        boolean heatLonely = scope.isIsolated();

        if (heatLonely) {
            // Heat is lonely: no collision (nocol), regardless of personalLonely
            setVanillaCollision(player, true);
            NocolManager.setCollisionMode(player, false);
        } else {
            // Heat NOT lonely: use the heat's collision mode
            CollisionMode mode = scope.getCollisionMode();
            switch (mode) {
                case HIGH -> {
                    setVanillaCollision(player, false);
                    NocolManager.setCollisionMode(player, true);
                }
                case LOW -> {
                    setVanillaCollision(player, false);
                    NocolManager.setLowCollisionMode(player);
                }
                case DISABLED -> {
                    setVanillaCollision(player, true);
                    NocolManager.setCollisionMode(player, false);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // show / hide helpers
    // -------------------------------------------------------------------------

    private void showPlayer(Player viewer, Player target) {
        SchedulerHelper.runTaskFor(plugin, viewer, () -> viewer.showPlayer(plugin, target));
        if (PlatformUtils.isFoliaRuntime()) {
            SchedulerHelper.runTaskFor(plugin, target, () -> {
                Entity vehicle = target.getVehicle();
                if (vehicle != null) {
                    SchedulerHelper.runTaskFor(plugin, vehicle, () -> {
                        if (!vehicle.isValid()) return;
                        World vehWorld = vehicle.getWorld();
                        int vehRegionX = vehicle.getLocation().getBlockX() >> 9;
                        int vehRegionZ = vehicle.getLocation().getBlockZ() >> 9;
                        boolean frostHex = plugin.getConfig().getBoolean("FrostHexAddOn", false);
                        Entity[] passengers = frostHex ? vehicle.getPassengers().toArray(new Entity[0]) : null;
                        SchedulerHelper.runTaskFor(plugin, viewer, () -> {
                            if (!viewer.isOnline()) return;
                            if (!viewer.getWorld().equals(vehWorld)) return;
                            int viewerRegionX = viewer.getLocation().getBlockX() >> 9;
                            int viewerRegionZ = viewer.getLocation().getBlockZ() >> 9;
                            if (vehRegionX != viewerRegionX || vehRegionZ != viewerRegionZ) return;
                            viewer.showEntity(plugin, vehicle);
                            if (frostHex && passengers != null) {
                                for (Entity passenger : passengers) {
                                    viewer.showEntity(plugin, passenger);
                                }
                            }
                        });
                    });
                }
            });
        } else {
            SchedulerHelper.runTaskFor(plugin, target, () -> {
                Entity vehicle = target.getVehicle();
                if (vehicle != null) {
                    viewer.showEntity(plugin, vehicle);
                    if (plugin.getConfig().getBoolean("FrostHexAddOn", false)) {
                        for (Entity passenger : vehicle.getPassengers()) {
                            viewer.showEntity(plugin, passenger);
                        }
                    }
                }
            });
        }
    }

    private void hidePlayer(Player viewer, Player target) {
        SchedulerHelper.runTaskFor(plugin, viewer, () -> viewer.hidePlayer(plugin, target));
        if (PlatformUtils.isFoliaRuntime()) {
            SchedulerHelper.runTaskFor(plugin, target, () -> {
                Entity vehicle = target.getVehicle();
                if (vehicle != null) {
                    SchedulerHelper.runTaskFor(plugin, vehicle, () -> {
                        if (!vehicle.isValid()) return;
                        World vehWorld = vehicle.getWorld();
                        int vehRegionX = vehicle.getLocation().getBlockX() >> 9;
                        int vehRegionZ = vehicle.getLocation().getBlockZ() >> 9;
                        boolean frostHex = plugin.getConfig().getBoolean("FrostHexAddOn", false);
                        Entity[] passengers = frostHex ? vehicle.getPassengers().toArray(new Entity[0]) : null;
                        SchedulerHelper.runTaskFor(plugin, viewer, () -> {
                            if (!viewer.isOnline()) return;
                            if (!viewer.getWorld().equals(vehWorld)) return;
                            int viewerRegionX = viewer.getLocation().getBlockX() >> 9;
                            int viewerRegionZ = viewer.getLocation().getBlockZ() >> 9;
                            if (vehRegionX != viewerRegionX || vehRegionZ != viewerRegionZ) return;
                            viewer.hideEntity(plugin, vehicle);
                            if (frostHex && passengers != null) {
                                for (Entity passenger : passengers) {
                                    viewer.hideEntity(plugin, passenger);
                                }
                            }
                        });
                    });
                }
            });
        } else {
            SchedulerHelper.runTaskFor(plugin, target, () -> {
                Entity vehicle = target.getVehicle();
                if (vehicle != null) {
                    viewer.hideEntity(plugin, vehicle);
                    if (plugin.getConfig().getBoolean("FrostHexAddOn", false)) {
                        for (Entity passenger : vehicle.getPassengers()) {
                            viewer.hideEntity(plugin, passenger);
                        }
                    }
                }
            });
        }
    }

    private boolean isPlayerInBoat(Player player) {
        Entity vehicle = player.getVehicle();
        return vehicle instanceof Boat || vehicle instanceof ChestBoat;
    }

    private void scheduleReconcile(Player player, Runnable runnable) {
        SchedulerHelper.runTaskFor(plugin, player, runnable);
    }

    private void scheduleReconcile(Runnable runnable) {
        SchedulerHelper.runTaskLater(plugin, runnable, VISIBILITY_UPDATE_DELAY);
    }

    // -------------------------------------------------------------------------
    // Scoreboard vanilla-collision team
    // -------------------------------------------------------------------------

    private void setVanillaCollision(Player player, boolean preventCollision) {
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
