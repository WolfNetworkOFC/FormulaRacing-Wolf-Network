package dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.model;

import java.util.List;

public record ScoreboardViewModel(String title, List<String> lines, boolean compact) {
}
