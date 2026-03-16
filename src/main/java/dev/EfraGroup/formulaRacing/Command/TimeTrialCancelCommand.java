package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Default;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.TimerUtils;
import org.bukkit.entity.Player;

@CommandAlias("timetrialcancel|ttc|timetrialc|ttcancel")
@Description("Cancela o Time Trial atual")
public class TimeTrialCancelCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final TimerUtils timerUtils;

    public TimeTrialCancelCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.timerUtils = plugin.getTimerUtils();
    }

    @Default
    public void onCancel(Player player) {
        // Para o cronômetro do jogador
        this.timerUtils.stopTimer(player);

        // Finaliza a sessão no controlador (se houver)
        if (this.plugin.getTimeTrialController() != null) {
            this.plugin.getTimeTrialController().endSession(player);
        }

        // Tradução ou mensagem direta
        this.plugin.sendMessage(player, "tt_cancelled");

        // Atualiza visibilidade se o modo Lonely estiver ativo
        if (this.plugin.getLonelyController() != null) {
            this.plugin.getLonelyController().updatePlayersVisibility(player);
        }
    }
}