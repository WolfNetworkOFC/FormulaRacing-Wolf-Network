package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.Event.Driver.DriverNewLapEvent;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.GimmickManager;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class GimmickListener implements Listener {

    private final FormulaRacing plugin;
    private final GimmickManager gimmickManager;

    public GimmickListener(FormulaRacing plugin, GimmickManager gimmickManager) {
        this.plugin = plugin;
        this.gimmickManager = gimmickManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDriverNewLap(DriverNewLapEvent event) {
        Driver driver = event.getDriver();
        if (driver == null) return;

        int heatId = driver.getHeatId();
        if (heatId <= 0) return;

        Heats heat = plugin.getRaceEventManager().getHeat(heatId).orElse(null);
        if (heat == null) return;

        if (heat.getHeatState() != dev.EfraGroup.formulaRacing.Heat.HeatState.RACING) return;

        int currentLap = driver.getLapCount() + 1;

        gimmickManager.triggerGimmicks(heatId, currentLap);
    }
}
