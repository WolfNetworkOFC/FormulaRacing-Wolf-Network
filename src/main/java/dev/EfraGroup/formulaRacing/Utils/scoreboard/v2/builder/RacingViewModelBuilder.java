package dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.builder;

import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.model.ScoreboardContext;
import dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.model.ScoreboardViewModel;
import java.util.ArrayList;
import java.util.List;

public class RacingViewModelBuilder implements StateViewModelBuilder {
    @Override
    public boolean supports(HeatState state) {
        return state == HeatState.RACING;
    }

    @Override
    public ScoreboardViewModel build(ScoreboardContext context) {
        List<String> lines = new ArrayList<>();
        lines.add(BuilderSupport.heatContext(context));
        lines.add(BuilderSupport.spacer(0));
        lines.addAll(BuilderSupport.buildClassificationLines(context, false, 6));
        lines.add(BuilderSupport.spacer(1));
        lines.add("§ewolfnetwork.com.br");
        int minHeight = BuilderSupport.minHeightForClassification(context.heat().getDriverCount());
        BuilderSupport.padToMinHeight(lines, minHeight);
        return new ScoreboardViewModel(
                BuilderSupport.scoreboardTitle(context, "scoreboard_title_race"),
                lines,
                false
        );
    }
}
