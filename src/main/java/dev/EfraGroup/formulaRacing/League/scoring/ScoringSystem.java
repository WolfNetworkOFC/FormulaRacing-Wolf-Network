package dev.EfraGroup.formulaRacing.League.scoring;

public interface ScoringSystem {

    String id();

    int pointsForPosition(int position, int driverCount);
}
