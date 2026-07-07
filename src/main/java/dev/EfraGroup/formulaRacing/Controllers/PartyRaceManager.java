package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Event.EventState;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Heat.HeatState;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Round.RoundState;
import dev.EfraGroup.formulaRacing.Round.Rounds;
import dev.EfraGroup.formulaRacing.Utils.TitleHelper;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class PartyRaceManager {

    private final FormulaRacing plugin;
    private final RaceEventManager eventManager;
    private final DatabaseManager database;
    private final Map<UUID, PartyRaceSession> activePartyRaces = new HashMap<>();

    public PartyRaceManager(FormulaRacing plugin, RaceEventManager eventManager, DatabaseManager database) {
        this.plugin = plugin;
        this.eventManager = eventManager;
        this.database = database;
    }

    public boolean createPartyRace(Player creator, String trackName, int laps, int pits) {
        try {
            if (!database.hasParty(creator.getUniqueId())) {
                creator.sendMessage("§cYou are not in a party.");
                return false;
            }

            UUID owner = database.getOwner(creator.getUniqueId());
            if (!owner.equals(creator.getUniqueId())) {
                creator.sendMessage("§cOnly the leader can start a party race.");
                return false;
            }

            if (activePartyRaces.containsKey(owner)) {
                creator.sendMessage("§cYour party already has an active race.");
                return false;
            }

            DatabaseManager.TrackData trackData = database.getTrackData(trackName);
            if (trackData == null) {
                plugin.sendMessage(creator, "track_not_found", "{track}", trackName);
                return false;
            }

            String finalTrackName = trackData.getTrackName();
            String trackNameWS = finalTrackName.replaceAll("\\s+", "").toLowerCase();

            if (!database.isCircuit(trackNameWS) && (laps > 1 || pits > 0)) {
                plugin.sendMessage(creator, "quickrace_ptp_adjustment");
                laps = 1;
                pits = 0;
            }

            int finalLaps = Math.max(1, laps);
            int finalPits = Math.min(Math.max(0, pits), finalLaps - 1);
            String eventName = "PartyRace_" + owner.toString() + "_" + System.currentTimeMillis();

            String membersRaw = database.getMembers(owner);
            List<UUID> memberList = new ArrayList<>();
            if (membersRaw != null && !membersRaw.isEmpty()) {
                for (String s : membersRaw.split(",")) {
                    if (!s.isEmpty()) {
                        try { memberList.add(UUID.fromString(s)); } catch (IllegalArgumentException ignored) {}
                    }
                }
            }
            memberList.add(owner);

            List<Player> onlineMembers = new ArrayList<>();
            for (UUID u : memberList) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && p.isOnline()) onlineMembers.add(p);
            }

            if (onlineMembers.size() < 2) {
                creator.sendMessage("§cNeed at least 2 online members for a party race.");
                return false;
            }

            CompletableFuture<Object> future = eventManager.createQuickRace(
                    creator.getUniqueId(), eventName, finalTrackName, finalLaps, finalPits
            );

            future.thenAccept(obj -> {
                if (obj == null || !(obj instanceof Events event)) {
                    creator.sendMessage("§cError creating the party race.");
                    return;
                }

                Rounds round = event.getEventSchedule().getRounds().values().stream()
                        .findFirst().map(r -> (Rounds) r).orElse(null);
                if (round == null) {
                    creator.sendMessage("§cError setting up the race (round).");
                    return;
                }

                Heats heat = round.getHeats().values().stream()
                        .findFirst().map(h -> (Heats) h).orElse(null);
                if (heat == null) {
                    creator.sendMessage("§cError setting up the race (heat).");
                    return;
                }

                heat.setMaxDrivers(onlineMembers.size());

                for (Player member : onlineMembers) {
                    database.setPlayerSelectedEvent(member.getUniqueId(), event);
                    int position = heat.getDriverCount() + 1;
                    heat.addDriver(member.getUniqueId(), position);
                }

                heat.loadHeat();
                round.setRoundState(RoundState.RUNNING);
                event.setState(EventState.RUNNING);

                for (Player member : onlineMembers) {
                    Driver driver = heat.getDriver(member.getUniqueId());
                    if (driver != null) {
                        heat.getGridManager().teleportDriver(driver);
                    }
                }

                PartyRaceSession session = new PartyRaceSession(owner, event, round, heat, onlineMembers);
                activePartyRaces.put(owner, session);

                String trackColor = "§e";
                for (Player member : onlineMembers) {
                    member.sendMessage("");
                    member.sendMessage("§6§l⚐ PARTY RACE");
                    member.sendMessage("§7Pista: " + trackColor + finalTrackName);
                    member.sendMessage("§7Laps: §f" + finalLaps + " §7| Pit Stops: §f" + finalPits);
                    member.sendMessage("");
                    TitleHelper.sendThemedTitle(member,
                            "§6§lPARTY RACE",
                            "§e" + finalTrackName,
                            10, 40, 10);
                    member.playSound(member.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                }

                heat.startCountdown(10);

                plugin.getDebugManager().logRaceSystem(
                        "Party Race created by " + creator.getName() +
                        " for " + onlineMembers.size() + " members on " + finalTrackName
                );
            });

            return true;

        } catch (SQLException e) {
            creator.sendMessage("§cError creating party race.");
            plugin.getDebugManager().logRaceSystem("[PartyRace] Error: " + e.getMessage());
            return false;
        }
    }

    public PartyRaceSession getActivePartyRace(UUID owner) {
        return activePartyRaces.get(owner);
    }

    public PartyRaceSession getActivePartyRaceByPlayer(UUID playerUuid) {
        for (PartyRaceSession session : activePartyRaces.values()) {
            if (session.hasPlayer(playerUuid)) return session;
        }
        return null;
    }

    public void removePartyRace(UUID owner) {
        activePartyRaces.remove(owner);
    }

    public boolean isInPartyRace(UUID playerUuid) {
        return getActivePartyRaceByPlayer(playerUuid) != null;
    }

    public void cleanup() {
        activePartyRaces.clear();
    }

    public static class PartyRaceSession {
        private final UUID owner;
        private final Events event;
        private final Rounds round;
        private final Heats heat;
        private final List<Player> members;

        public PartyRaceSession(UUID owner, Events event, Rounds round, Heats heat, List<Player> members) {
            this.owner = owner;
            this.event = event;
            this.round = round;
            this.heat = heat;
            this.members = new ArrayList<>(members);
        }

        public UUID getOwner() { return owner; }
        public Events getEvent() { return event; }
        public Rounds getRound() { return round; }
        public Heats getHeat() { return heat; }
        public List<Player> getMembers() { return Collections.unmodifiableList(members); }

        public boolean hasPlayer(UUID uuid) {
            for (Player p : members) {
                if (p.getUniqueId().equals(uuid)) return true;
            }
            return false;
        }

        public boolean isRaceActive() {
            return heat != null && heat.getHeatState() == HeatState.RACING;
        }
    }
}
