//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Event;

import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Round.RoundType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class EventResults {
    public static List<Driver> generateHeatResults(Heats heat) {
        List<Driver> newList = new ArrayList(heat.getDrivers().values());
        if (heat.getRound() != null && heat.getRound().getType() == RoundType.QUALIFICATION) {
            newList.sort((d1, d2) -> {
                if (d1.getFastestLap() == null) {
                    return 1;
                } else {
                    return d2.getFastestLap() == null ? -1 : Long.compare(d1.getFastestLap().getLapTime(), d2.getFastestLap().getLapTime());
                }
            });
        } else {
            newList.sort(Comparator.comparingInt(Driver::getPosition));
        }

        return newList;
    }

    public static List<Driver> generateRoundResults(List<Heats> heats) {
        List<Driver> results = new ArrayList();

        for(Heats heat : heats) {
            results.addAll(generateHeatResults(heat));
        }

        if (!results.isEmpty()) {
            Heats firstHeat = (Heats)heats.get(0);
            if (firstHeat.getRound() != null && firstHeat.getRound().getType() == RoundType.QUALIFICATION) {
                results.sort((d1, d2) -> {
                    if (d1.getFastestLap() == null && d2.getFastestLap() == null) {
                        return 0;
                    } else if (d1.getFastestLap() == null) {
                        return 1;
                    } else {
                        return d2.getFastestLap() == null ? -1 : Long.compare(d1.getFastestLap().getLapTime(), d2.getFastestLap().getLapTime());
                    }
                });
            } else {
                results.sort((d1, d2) -> {
                    if (d1.isFinished() && !d2.isFinished()) {
                        return -1;
                    } else {
                        return !d1.isFinished() && d2.isFinished() ? 1 : Integer.compare(d1.getPosition(), d2.getPosition());
                    }
                });
            }
        }

        return results;
    }
}
