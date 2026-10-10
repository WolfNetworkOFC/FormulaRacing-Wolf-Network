package dev.EfraGroup.formulaRacing.League;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LeagueTeam {

    private final int id;
    private final int leagueId;
    private final String name;
    private String colorHex;

    private UUID owner;
    private Set<UUID> mainDrivers = new HashSet<>();
    private Set<UUID> reserveDrivers = new HashSet<>();
    private List<UUID> priorityDrivers = new ArrayList<>();

    private Integer maxMains;
    private Integer maxReserves;
    private Integer countedScorers;

    public LeagueTeam(int id, int leagueId, String name) {
        this.id = id;
        this.leagueId = leagueId;
        this.name = name;
    }

    public int getId() {
        return id;
    }

    public int getLeagueId() {
        return leagueId;
    }

    public String getName() {
        return name;
    }

    public String getColorHex() {
        return colorHex;
    }

    public void setColorHex(String colorHex) {
        this.colorHex = colorHex;
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public boolean isOwner(UUID uuid) {
        return owner != null && owner.equals(uuid);
    }

    public Set<UUID> getMainDrivers() {
        return mainDrivers;
    }

    public Set<UUID> getReserveDrivers() {
        return reserveDrivers;
    }

    public List<UUID> getPriorityDrivers() {
        return priorityDrivers;
    }

    public Set<UUID> getMembers() {
        Set<UUID> members = new LinkedHashSet<>();
        members.addAll(mainDrivers);
        members.addAll(reserveDrivers);
        members.addAll(priorityDrivers);
        return members;
    }

    public boolean isMember(UUID uuid) {
        return mainDrivers.contains(uuid)
            || reserveDrivers.contains(uuid)
            || priorityDrivers.contains(uuid);
    }

    public boolean isMain(UUID uuid) {
        return mainDrivers.contains(uuid);
    }

    public boolean isReserve(UUID uuid) {
        return reserveDrivers.contains(uuid);
    }

    public int getPriority(UUID uuid) {
        return priorityDrivers.indexOf(uuid) + 1;
    }

    public Integer getMaxMains() {
        return maxMains;
    }

    public void setMaxMains(Integer maxMains) {
        this.maxMains = maxMains;
    }

    public Integer getMaxReserves() {
        return maxReserves;
    }

    public void setMaxReserves(Integer maxReserves) {
        this.maxReserves = maxReserves;
    }

    public Integer getCountedScorers() {
        return countedScorers;
    }

    public void setCountedScorers(Integer countedScorers) {
        this.countedScorers = countedScorers;
    }

    public int effectiveMaxMains(League league) {
        int value = this.maxMains != null ? this.maxMains : league.getTeamConfig().getMaxMains();
        return value;
    }

    public int effectiveMaxReserves(League league) {
        int value = this.maxReserves != null ? this.maxReserves : league.getTeamConfig().getMaxReserves();
        return value;
    }

    public int effectiveCountedScorers(League league) {
        int value = this.countedScorers != null
            ? this.countedScorers
            : league.getTeamConfig().getCountedScorers();
        return value;
    }

    public boolean addMainDriver(UUID uuid) {
        if (isMember(uuid)) return false;
        priorityDrivers.remove(uuid);
        reserveDrivers.remove(uuid);
        return mainDrivers.add(uuid);
    }

    public boolean addReserveDriver(UUID uuid) {
        if (isMember(uuid)) return false;
        priorityDrivers.remove(uuid);
        mainDrivers.remove(uuid);
        return reserveDrivers.add(uuid);
    }

    public boolean addPriorityDriver(UUID uuid, int priority) {
        if (isMember(uuid)) {
            priorityDrivers.remove(uuid);
        } else {
            mainDrivers.remove(uuid);
            reserveDrivers.remove(uuid);
        }
        int index = Math.max(0, Math.min(priority - 1, priorityDrivers.size()));
        priorityDrivers.add(index, uuid);
        return true;
    }

    public boolean removeMember(UUID uuid) {
        boolean removed = mainDrivers.remove(uuid) || reserveDrivers.remove(uuid);
        removed |= priorityDrivers.remove(uuid);
        return removed;
    }

    public boolean promoteToMain(UUID uuid) {
        if (!reserveDrivers.contains(uuid)) return false;
        reserveDrivers.remove(uuid);
        mainDrivers.add(uuid);
        return true;
    }

    public boolean demoteToReserve(UUID uuid) {
        if (!mainDrivers.contains(uuid)) return false;
        mainDrivers.remove(uuid);
        reserveDrivers.add(uuid);
        return true;
    }
}
