package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.AI.AIRacingLineRecorder;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import org.bukkit.event.Listener;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.HashMap;
import java.util.Map;

/**
 * Listener para gravação de linhas de corrida.
 * Usa polling periódico para detectar transições de estado do heat
 * e iniciar/finalizar gravações automaticamente.
 */
public class AIRacingLineRecorderListener implements Listener {

    private final FormulaRacing plugin;
    private final Map<Integer, HeatState> heatStates;
    private ScheduledTask pollTask;

    public AIRacingLineRecorderListener(FormulaRacing plugin) {
        this.plugin = plugin;
        this.heatStates = new HashMap<>();
        startPolling();
    }

    private void startPolling() {
        pollTask = SchedulerHelper.runTaskTimer(plugin, () -> {
            AIRacingLineRecorder recorder = plugin.getAIRacingLineManager() != null
                    ? plugin.getAIRacingLineManager().getRecorder() : null;
            if (recorder == null) {
                return;
            }

            // Coleta todos os heats ativos iterando sobre eventos e rounds
            for (Events event : plugin.getRaceEventManager().getActiveEvents()) {
                if (event == null || event.getEventSchedule() == null) continue;

                for (Rounds round : event.getEventSchedule().getRounds().values()) {
                    if (round == null) continue;

                    for (Heats heat : round.getHeats().values()) {
                        if (heat == null) continue;

                        int heatId = heat.getId();
                        HeatState currentState = heat.getHeatState();
                        HeatState previousState = heatStates.get(heatId);

                        if (previousState == null) {
                            heatStates.put(heatId, currentState);
                            continue;
                        }

                        // Transição para RACING → inicia gravação
                        if (previousState != HeatState.RACING && currentState == HeatState.RACING) {
                            recorder.onHeatRacing(heat);
                        }

                        // Transição para FINISHED → finaliza gravação
                        if (previousState != HeatState.FINISHED && currentState == HeatState.FINISHED) {
                            recorder.onHeatFinished(heat);
                        }

                        // Limpa heats já finalizados há mais de um ciclo
                        if (currentState == HeatState.FINISHED && previousState == HeatState.FINISHED) {
                            heatStates.remove(heatId);
                            continue;
                        }

                        heatStates.put(heatId, currentState);
                    }
                }
            }
        }, 20L, 10L); // A cada 0.5 segundos
    }

    public void cleanup() {
        if (pollTask != null && !pollTask.isCancelled()) {
            pollTask.cancel();
        }
        heatStates.clear();
    }
}
