package dev.EfraGroup.formulaRacing.Loneliness;

import dev.EfraGroup.formulaRacing.Controllers.SpectatorManager;
import dev.EfraGroup.formulaRacing.Duels.TimeTrialDuels;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.Heats;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves the {@link VisibilityScope} for a given player.
 * <p>
 * Resolution order (first match wins):
 * <ol>
 *   <li>Active heat (driver registered in any running Heat)</li>
 *   <li>Spectator bound to a heat (watching an active heat)</li>
 *   <li>Active duel (player is in an active TimeTrialDuel)</li>
 *   <li>Active solo TimeTrial (player has a running timer)</li>
 *   <li>{@link Optional#empty()} — open world</li>
 * </ol>
 */
public final class ScopeResolver {

    private final FormulaRacing plugin;

    public ScopeResolver(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    public Optional<VisibilityScope> resolve(Player player) {
        UUID uuid = player.getUniqueId();

        // 1. Heat (as driver)
        Optional<Heats> heatOpt = plugin.getRaceEventManager().getPlayerActiveHeat(uuid);
        if (heatOpt.isPresent()) {
            Heats heat = heatOpt.get();
            if (heat.isPlayerActivelyRacing(uuid)) {
                Set<UUID> spectators = collectSpectators(heat);
                return Optional.of(new HeatScope(heat, spectators));
            }
        }

        // 2. Spectator bound to a heat
        SpectatorManager sm = plugin.getSpectatorManager();
        if (sm != null && sm.isSpectator(uuid)) {
            Heats boundHeat = sm.getSpectatorBoundHeat(uuid);
            if (boundHeat != null) {
                Set<UUID> spectators = collectSpectators(boundHeat);
                return Optional.of(new HeatScope(boundHeat, spectators));
            }
        }

        // 3. Duel
        if (plugin.getTimeTrialDuels() != null) {
            int duelId = plugin.getTimeTrialDuels().getActiveDuelIdCached(uuid);
            if (duelId != -1) {
                TimeTrialDuels.DuelState duelState = plugin.getTimeTrialDuels().getDuelState(duelId);
                if (duelState != null) {
                    Set<UUID> players = new HashSet<>(duelState.getPlayers());
                    return Optional.of(new DuelScope(duelId, players, duelState.isLonely()));
                }
            }
        }

        // 4. Solo TimeTrial — build participant set from all online players on the same track
        String activeTrack = plugin.getTimerUtils().getActiveTrack(player);
        if (activeTrack != null) {
            Set<UUID> ttParticipants = new HashSet<>();
            ttParticipants.add(uuid);
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(player)) continue;
                String otherTrack = plugin.getTimerUtils().getActiveTrack(other);
                if (activeTrack.equalsIgnoreCase(otherTrack)) {
                    ttParticipants.add(other.getUniqueId());
                }
            }
            return Optional.of(new TimeTrialScope(activeTrack, ttParticipants));
        }

        return Optional.empty();
    }

    /**
     * Collects all spectator UUIDs that are bound to the given heat so they can
     * be included in the heat's visibility scope.
     */
    private Set<UUID> collectSpectators(Heats heat) {
        Set<UUID> result = new HashSet<>();
        SpectatorManager sm = plugin.getSpectatorManager();
        if (sm == null) return result;

        for (Player online : Bukkit.getOnlinePlayers()) {
            UUID uid = online.getUniqueId();
            if (sm.isSpectator(uid)) {
                Heats bound = sm.getSpectatorBoundHeat(uid);
                if (bound != null && bound.getId() == heat.getId()) {
                    result.add(uid);
                }
            }
        }
        return result;
    }
}
