package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.Event.Driver.DriverNewLapEvent;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.GimmickManager;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class GimmickListener implements Listener {

    private final FormulaRacing plugin;

    public GimmickListener(FormulaRacing plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDriverNewLap(DriverNewLapEvent event) {
        Driver driver = event.getDriver();
        if (driver == null) return;

        int heatId = driver.getHeatId();
        if (heatId <= 0) return;

        Heats heat = plugin.getRaceEventManager().getHeat(heatId).orElse(null);
        if (heat == null) return;
        if (heat.getHeatState() != HeatState.RACING) return;

        // Resolved here instead of held in a field: the manager is replaced on reload.
        GimmickManager gimmickManager = plugin.getGimmickManager();
        if (gimmickManager == null) return;

        // This event fires once per driver, so the lap is derived from the heat itself.
        // GimmickManager only pastes each gimmick one time per race.
        gimmickManager.triggerGimmicks(heat, gimmickManager.currentHeatLap(heat));
    }
}
