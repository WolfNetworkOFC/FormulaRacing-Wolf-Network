package dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.builder;

import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.model.ScoreboardContext;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.model.ScoreboardViewModel;
import java.util.ArrayList;
import java.util.List;

public class PracticeViewModelBuilder implements StateViewModelBuilder {
    @Override
    public boolean supports(HeatState state) {
        return state == HeatState.PRACTICE;
    }

    @Override
    public ScoreboardViewModel build(ScoreboardContext context) {
        List<String> lines = new ArrayList<>();
        // TimingSystem parity: one row per driver, no extra header/footer.
        lines.addAll(BuilderSupport.buildClassificationLines(context, true, 0));
        return new ScoreboardViewModel(
                BuilderSupport.scoreboardTitle(context, "scoreboard_title_practice"),
                lines,
                false
        );
    }
}
