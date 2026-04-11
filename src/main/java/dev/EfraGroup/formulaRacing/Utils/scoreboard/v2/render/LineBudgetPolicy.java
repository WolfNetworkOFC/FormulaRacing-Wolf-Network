package dev.EfraGroup.formulaRacing.Utils.scoreboard.v2.render;

import java.util.ArrayList;
import java.util.List;

public class LineBudgetPolicy {
    public List<String> fit(List<String> lines, int maxRows) {
        if (lines.size() <= maxRows) {
            return lines;
        }

        List<String> fitted = new ArrayList<>(maxRows);
        int topKeep = Math.max(2, maxRows - 2);
        for (int i = 0; i < Math.min(topKeep, lines.size()); i++) {
            fitted.add(lines.get(i));
        }

        if (lines.size() > topKeep) {
            fitted.add("§8...");
            fitted.add(lines.get(lines.size() - 1));
        }

        return fitted;
    }
}
