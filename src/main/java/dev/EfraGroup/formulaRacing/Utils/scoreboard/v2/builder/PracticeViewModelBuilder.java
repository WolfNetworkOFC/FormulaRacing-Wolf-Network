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
        int fixedLines = 6;
        if (!context.spectator() && context.viewerDriver() != null) {
            lines.add(BuilderSupport.formatBestLap(context, context.viewerDriver()));
            fixedLines++;
        }
        lines.add(BuilderSupport.spacer(0));
        lines.addAll(BuilderSupport.buildClassificationLines(context, true, fixedLines));
        lines.add(BuilderSupport.spacer(1));
        int minHeight = BuilderSupport.minHeightForPractice(context.heat().getDriverCount());
        BuilderSupport.padToMinHeight(lines, minHeight);
        lines.add(BuilderSupport.commonSeparator(context));
        lines.add(BuilderSupport.commonFooter(context));
        return new ScoreboardViewModel(
                BuilderSupport.scoreboardTitle(context, "scoreboard_title_practice"),
                lines,
                false
        );
    }
}
