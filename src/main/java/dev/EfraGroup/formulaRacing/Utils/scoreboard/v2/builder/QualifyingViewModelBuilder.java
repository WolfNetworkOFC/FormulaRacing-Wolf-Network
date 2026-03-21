package dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.builder;

import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.model.ScoreboardContext;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.model.ScoreboardViewModel;
import java.util.ArrayList;
import java.util.List;

public class QualifyingViewModelBuilder implements StateViewModelBuilder {
    @Override
    public boolean supports(HeatState state) {
        return state == HeatState.QUALIFYING;
    }

    @Override
    public ScoreboardViewModel build(ScoreboardContext context) {
        List<String> lines = new ArrayList<>();
        lines.add(BuilderSupport.heatContext(context));
        lines.add(BuilderSupport.spacer(0));
        int fixedLines = 8;
        long remaining = context.heat().getSessionTimeRemaining();
        if (remaining >= 0L) {
            String timer = context.plugin().getTranslationUtil().getTranslated(
                    context.viewer(),
                    "scoreboard_v2_time",
                    "{time}",
                    "§b" + BuilderSupport.formatTime(remaining)
            );
            lines.add(timer);
            fixedLines++;
        }
        if (!context.spectator() && context.viewerDriver() != null) {
            lines.add(BuilderSupport.formatBestLap(context, context.viewerDriver()));
            fixedLines++;
        }
        lines.add(BuilderSupport.spacer(1));
        lines.addAll(BuilderSupport.buildClassificationLines(context, true, fixedLines));
        lines.add(BuilderSupport.spacer(2));
        lines.add(BuilderSupport.commonSeparator(context));
        lines.add(BuilderSupport.commonFooter(context));
        int minHeight = BuilderSupport.minHeightForClassification(context.heat().getDriverCount());
        BuilderSupport.padToMinHeight(lines, minHeight);
        return new ScoreboardViewModel(
                BuilderSupport.scoreboardTitle(context, "scoreboard_title_qualifying"),
                lines,
                false
        );
    }
}
