package dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.render;

import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.model.ScoreboardContext;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.model.ScoreboardViewModel;
import java.util.List;

public class ScoreboardRenderer {
    private final LineBudgetPolicy lineBudgetPolicy;

    public ScoreboardRenderer(LineBudgetPolicy lineBudgetPolicy) {
        this.lineBudgetPolicy = lineBudgetPolicy;
    }

    public ScoreboardViewModel render(ScoreboardContext context, ScoreboardViewModel viewModel) {
        List<String> fitted = this.lineBudgetPolicy.fit(viewModel.lines(), context.maxRows());
        // TimingSystem never auto-enables compact just because rows were cut.
        return new ScoreboardViewModel(viewModel.title(), fitted, viewModel.compact());
    }
}
