package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Utils.FRTask;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RaceVoteManager {
    private final FormulaRacing plugin;
    private final DatabaseManager database;
    private final QuickRaceManager quickRaceManager;
    private RaceProposal currentProposal;

    public RaceVoteManager(FormulaRacing plugin, DatabaseManager database, QuickRaceManager quickRaceManager) {
        this.plugin = plugin;
        this.database = database;
        this.quickRaceManager = quickRaceManager;
    }

    public boolean propose(Player proposer, String trackName, int laps, int pits) {
        if (isProposalActive()) {
            proposer.sendMessage("§cThere is already an active vote!");
            return false;
        }
        if (quickRaceManager.isQuickRaceActive()) {
            proposer.sendMessage("§cCannot start a vote while a race is in progress.");
            return false;
        }

        DatabaseManager.TrackData trackData = database.getTrackData(trackName);
        if (trackData == null) {
            proposer.sendMessage("§cTrack not found!");
            return false;
        }

        String trackNameWS = trackData.getTrackName().replaceAll("\\s+", "").toLowerCase();
        int gridCount = plugin.getTrackIntegrationManager().getGridPositionCount(trackNameWS);
        if (gridCount <= 0) {
            proposer.sendMessage("§c✗ Track §e" + trackData.getTrackName() + " §chas no grid defined!");
            return false;
        }

        this.currentProposal = new RaceProposal(proposer, trackData.getTrackName(), laps, pits);
        this.currentProposal.start();
        return true;
    }

    public void vote(Player player) {
        if (isProposalActive()) currentProposal.addVote(player);
        else player.sendMessage("§cThere is no active vote.");
    }

    public boolean isProposalActive() {
        return currentProposal != null && !currentProposal.hasExpired();
    }

    public class RaceProposal {
        private final UUID proposerUUID;
        private final String proposerName;
        private final String trackName;
        private final int laps;
        private final int pits;
        private final int requiredVotes; // New variable to store the vote target
        private final Set<UUID> voters = ConcurrentHashMap.newKeySet();
        private boolean expired = false;
        private FRTask timeoutTask;

        public RaceProposal(Player proposer, String trackName, int laps, int pits) {
            this.proposerUUID = proposer.getUniqueId();
            this.proposerName = proposer.getName();
            this.trackName = trackName;
            this.laps = laps;
            this.pits = pits;

            // Calculates 30% of online players at the time the proposal is created.
            // Math.ceil rounds up, and Math.max ensures at least 1 vote is needed.
            int onlinePlayers = Bukkit.getOnlinePlayers().size();
            this.requiredVotes = Math.max(1, (int) Math.ceil(onlinePlayers * 0.30));

            this.voters.add(proposerUUID);
        }

        public void start() {
            broadcastProposalCreated();
            // Auto-approve if the creator is the only one on the server
            if (voters.size() >= requiredVotes) {
                approve();
            } else {
                this.timeoutTask = SchedulerHelper.runTaskLater(plugin, () -> { if (!expired) expire(); }, 2400L);
            }
        }

        private void broadcastProposalCreated() {
            Bukkit.broadcastMessage(" ");
            Bukkit.broadcastMessage("§6§l════════════ NEW PROPOSAL ════════════");
            Bukkit.broadcastMessage("§f§l" + proposerName + " §7started a vote for:");
            Bukkit.broadcastMessage("§c§l" + trackName + " §7| §f" + laps + " Laps §7| §f" + pits + " Pits");
            Bukkit.broadcastMessage("§7Target votes: §a" + voters.size() + "/" + requiredVotes); // Shows target in initial announcement
            Bukkit.broadcastMessage(" ");

            TextComponent clickButton = new TextComponent("[ §a§lCLICK TO VOTE §r]");
            clickButton.setColor(ChatColor.GREEN);
            clickButton.setBold(true);
            clickButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/voterace"));
            clickButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§aClick to cast your vote!")));

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.spigot().sendMessage(clickButton);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
            }

            Bukkit.broadcastMessage(" ");
            Bukkit.broadcastMessage("§6§l═══════════════════════════════════════");
        }

        public void addVote(Player player) {
            if (voters.contains(player.getUniqueId())) {
                player.sendMessage("§eYou have already voted!");
                return;
            }
            voters.add(player.getUniqueId());

            // Now the chat updates showing the dynamically calculated vote target
            TextComponent voteMsg = new TextComponent("§7► §f§l" + player.getName() + " §aalso wants §f" + trackName + " §6[" + voters.size() + "/" + requiredVotes + "]");
            voteMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/voterace"));
            voteMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§aClick to vote too!")));

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.spigot().sendMessage(voteMsg);
            }

            // Replaces fixed "3" with the variable
            if (voters.size() >= requiredVotes) approve();
        }

        private void approve() {
            this.expired = true;
            if (timeoutTask != null) timeoutTask.cancel();

            Bukkit.broadcastMessage("§6§l════════════ RACE APPROVED ════════════");
            Bukkit.broadcastMessage("§7Pista: §f§l" + trackName);
            Bukkit.broadcastMessage("§7Config: §f" + laps + " Laps | " + pits + " Pits");
            Bukkit.broadcastMessage("§6§l═══════════════════════════════════════");

            SchedulerHelper.runTaskLater(plugin, () -> {
                Player creator = Bukkit.getPlayer(proposerUUID);
                if (creator != null) quickRaceManager.createQuickRace(creator, trackName, laps, pits);
                currentProposal = null;
            }, 40L);
        }

        private void expire() {
            this.expired = true;
            Bukkit.broadcastMessage("§c§lVote expired for: " + trackName);
            currentProposal = null;
        }

        public boolean hasExpired() { return expired; }
    }
}
