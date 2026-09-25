package dev.EfraGroup.formulaRacing.League;

public class LeagueCalendarEntry {

    private final int eventId;
    private int roundNumber = 1;
    private String categoryName;
    private Integer pinnedHeatId;
    private boolean pointsApplied;

    public LeagueCalendarEntry(int eventId) {
        this.eventId = eventId;
    }

    public LeagueCalendarEntry(int eventId, String categoryName, Integer pinnedHeatId) {
        this(eventId);
        this.categoryName = categoryName;
        this.pinnedHeatId = pinnedHeatId;
    }

    public int getRoundNumber() {
        return this.roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public int getEventId() {
        return eventId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getPinnedHeatId() {
        return pinnedHeatId;
    }

    public void setPinnedHeatId(Integer pinnedHeatId) {
        this.pinnedHeatId = pinnedHeatId;
    }

    public boolean isPointsApplied() {
        return this.pointsApplied;
    }

    public void setPointsApplied(boolean pointsApplied) {
        this.pointsApplied = pointsApplied;
    }

    public boolean hasPinnedHeat() {
        return pinnedHeatId != null;
    }

    public boolean hasCategory() {
        return categoryName != null && !categoryName.isBlank();
    }
}
