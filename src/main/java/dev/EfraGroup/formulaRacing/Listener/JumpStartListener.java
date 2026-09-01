package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.RaceCountdown;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Input;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;

/**
 * Detects jump starts in F1-start heats from the player's INPUT packets.
 *
 * <p>Physics-based detection (velocity/displacement of the anchored boat) is
 * deliberately NOT used: a lagging player gets position corrections and
 * rubber-banding that look exactly like movement and would cause false
 * penalties. Input packets, on the other hand, can only be delayed by lag —
 * never fabricated — so "forward pressed while the lights are still on" is
 * always a real key press. Players who react late (input arrives after lights
 * out) are simply not flagged.
 */
public class JumpStartListener implements Listener {

    private final FormulaRacing plugin;

    public JumpStartListener(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerInput(PlayerInputEvent event) {
        Input input = event.getInput();
        // Only throttle-type inputs count as a launch attempt.
        if (!input.isForward() && !input.isBackward() && !input.isJump() && !input.isSprint()) {
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Optional<Heats> heatOpt = this.plugin.getRaceEventManager().getPlayerActiveHeat(uuid);
        if (heatOpt.isEmpty()) {
            return;
        }
        Heats heat = heatOpt.get();
        if (heat.getHeatState() != HeatState.STARTING) {
            return;
        }

        RaceCountdown countdown = heat.getActiveCountdown();
        if (countdown != null) {
            countdown.flagJumpStart(player);
        }
    }
}
