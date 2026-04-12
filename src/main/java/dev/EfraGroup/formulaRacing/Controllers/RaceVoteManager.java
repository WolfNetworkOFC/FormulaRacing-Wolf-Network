package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

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
            proposer.sendMessage("§cJá existe uma votação ativa!");
            return false;
        }
        if (quickRaceManager.isQuickRaceActive()) {
            proposer.sendMessage("§cNão é possível iniciar votação com corrida em andamento.");
            return false;
        }

        DatabaseManager.TrackData trackData = database.getTrackData(trackName);
        if (trackData == null) {
            proposer.sendMessage("§cPista não encontrada!");
            return false;
        }

        this.currentProposal = new RaceProposal(proposer, trackData.getTrackName(), laps, pits);
        this.currentProposal.start();
        return true;
    }

    public void vote(Player player) {
        if (isProposalActive()) currentProposal.addVote(player);
        else player.sendMessage("§cNão há votação ativa.");
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
        private final Set<UUID> voters = ConcurrentHashMap.newKeySet();
        private boolean expired = false;
        private BukkitTask timeoutTask;

        public RaceProposal(Player proposer, String trackName, int laps, int pits) {
            this.proposerUUID = proposer.getUniqueId();
            this.proposerName = proposer.getName();
            this.trackName = trackName;
            this.laps = laps;
            this.pits = pits;
            this.voters.add(proposerUUID);
        }

        public void start() {
            broadcastProposalCreated();
            this.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> { if (!expired) expire(); }, 2400L);
        }

        private void broadcastProposalCreated() {
            // Linhas normais
            Bukkit.broadcastMessage(" ");
            Bukkit.broadcastMessage("§6§l════════════ NOVA PROPOSTA ════════════");
            Bukkit.broadcastMessage("§f§l" + proposerName + " §7iniciou uma votação para:");
            Bukkit.broadcastMessage("§c§l" + trackName + " §7| §f" + laps + " Voltas §7| §f" + pits + " Pits");
            Bukkit.broadcastMessage(" ");

            // Botão Clicável no seu padrão
            TextComponent clickButton = new TextComponent("[ §a§lCLIQUE PARA VOTAR §r]");
            clickButton.setColor(ChatColor.GREEN);
            clickButton.setBold(true);
            clickButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/voterace"));
            clickButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§aClique para registrar seu voto!")));

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.spigot().sendMessage(clickButton);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f);
            }

            Bukkit.broadcastMessage(" ");
            Bukkit.broadcastMessage("§6§l═══════════════════════════════════════");
        }

        public void addVote(Player player) {
            if (voters.contains(player.getUniqueId())) {
                player.sendMessage("§eVocê já votou!");
                return;
            }
            voters.add(player.getUniqueId());

            // Mensagem de voto secundário (clicável também)
            TextComponent voteMsg = new TextComponent("§7► §f§l" + player.getName() + " §atambém quer §f" + trackName + " §6[" + voters.size() + "/3]");
            voteMsg.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/voterace"));
            voteMsg.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§aClique para votar também!")));

            for (Player p : Bukkit.getOnlinePlayers()) {
                p.spigot().sendMessage(voteMsg);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);

            if (voters.size() >= 3) approve();
        }

        private void approve() {
            this.expired = true;
            if (timeoutTask != null) timeoutTask.cancel();

            Bukkit.broadcastMessage("§6§l════════════ CORRIDA APROVADA ════════════");
            Bukkit.broadcastMessage("§7Pista: §f§l" + trackName);
            Bukkit.broadcastMessage("§7Config: §f" + laps + " Voltas | " + pits + " Pits");
            Bukkit.broadcastMessage("§6§l═══════════════════════════════════════");

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player creator = Bukkit.getPlayer(proposerUUID);
                if (creator != null) quickRaceManager.createQuickRace(creator, trackName, laps, pits);
                currentProposal = null;
            }, 40L);
        }

        private void expire() {
            this.expired = true;
            Bukkit.broadcastMessage("§c§lVotação expirada para: " + trackName);
            currentProposal = null;
        }

        public boolean hasExpired() { return expired; }
    }
}