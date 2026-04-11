package dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.builder;

import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.model.ScoreboardContext;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.model.ScoreboardViewModel;
import java.util.ArrayList;
import java.util.List;

public class FinishedViewModelBuilder implements StateViewModelBuilder {
    @Override
    public boolean supports(HeatState state) {
        return state == HeatState.FINISHED;
    }

    @Override
    public ScoreboardViewModel build(ScoreboardContext context) {
        List<String> lines = new ArrayList<>();
        lines.addAll(BuilderSupport.buildClassificationLines(context, false, 5));
        lines.add(BuilderSupport.spacer(0));
        int minHeight = BuilderSupport.minHeightForFinished(context.heat().getDriverCount());
        BuilderSupport.padToMinHeight(lines, minHeight);
        lines.add(BuilderSupport.commonSeparator(context));
        lines.add(BuilderSupport.commonFooter(context));

        return new ScoreboardViewModel(
                BuilderSupport.scoreboardTitle(context, "scoreboard_title_finished"),
                lines,
                false
        );
    }
}
