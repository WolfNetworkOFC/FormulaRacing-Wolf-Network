package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static net.md_5.bungee.api.ChatMessageType.ACTION_BAR;

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
            proposer.sendMessage("§cNão é possível iniciar uma votação durante uma corrida.");
            return false;
        }

        DatabaseManager.TrackData trackData = database.getTrackData(trackName);
        if (trackData == null) {
            proposer.sendMessage("§cPista '" + trackName + "' não encontrada!");
            return false;
        }

        this.currentProposal = new RaceProposal(proposer, trackData.getTrackName(), laps, pits);
        this.currentProposal.start();
        return true;
    }

    public void vote(Player player) {
        if (isProposalActive()) {
            currentProposal.addVote(player);
        } else {
            player.sendMessage("§cNão há nenhuma votação ativa.");
        }
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
        private final long createdAt;
        private BukkitTask timeoutTask;
        private BukkitTask actionBarTask;
        private boolean expired = false;

        public RaceProposal(Player proposer, String trackName, int laps, int pits) {
            this.proposerUUID = proposer.getUniqueId();
            this.proposerName = proposer.getName();
            this.trackName = trackName;
            this.laps = laps;
            this.pits = pits;
            this.createdAt = System.currentTimeMillis();
            this.voters.add(proposerUUID);
        }

        public void start() {
            broadcastProposalCreated();
            this.timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> { if (!expired) expire(); }, 2400L);
        }

        private String buildBar() {
            int filled = voters.size() * 3;
            return "§b|".repeat(Math.min(filled, 10)) + "§8|".repeat(Math.max(0, 10 - filled));
        }
        private void broadcastProposalCreated() {
            // Anúncio principal (Primeira pessoa)
            Bukkit.broadcastMessage(" ");
            Bukkit.broadcastMessage("§6§l════════════ NOVA PROPOSTA ════════════");
            Bukkit.broadcastMessage("§f§l" + proposerName + " §7iniciou uma votação para:");
            Bukkit.broadcastMessage("§c§l" + trackName + " §7| §f" + laps + " Voltas §7| §f" + pits + " Pits");
            Bukkit.broadcastMessage(" ");

            // Criando o componente com o evento de clique
            Component clickMe = LegacyComponentSerializer.legacySection().deserialize("§a§l[CLIQUE AQUI PARA VOTAR]")
                    .clickEvent(ClickEvent.runCommand("/voterace"));

Bukkit.broadcastMessage(String.valueOf(clickMe));

            Bukkit.broadcastMessage("§6§l═══════════════════════════════════════");
            Bukkit.broadcastMessage(" ");

            Bukkit.getOnlinePlayers().forEach(p -> p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 1f));
        }

        public void addVote(Player player) {
            if (voters.contains(player.getUniqueId())) {
                player.sendMessage("§eVocê já votou!");
                return;
            }

            voters.add(player.getUniqueId());

            // Mensagem curta para os próximos votos
            String msg = "§7► §f§l" + player.getName() + " §atambém quer §f" + trackName + " §6[" + voters.size() + "/3]";
            // Onde você cria o componente clicável
            Component clickMe = LegacyComponentSerializer.legacySection().deserialize("§a§l[CLIQUE AQUI PARA VOTAR]")
                    .clickEvent(ClickEvent.runCommand("/voterace"));

// USE Bukkit.broadcast(clickMe) em vez de broadcastMessage
            Bukkit.broadcastMessage(String.valueOf(clickMe));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);

            if (voters.size() >= 3) approve();
        }

        private void approve() {
            this.expired = true;
            stopTasks();
            Bukkit.broadcastMessage("§6§l════════════ CORRIDA APROVADA ════════════");
            Bukkit.broadcastMessage("§7Pista: §f§l" + trackName);
            Bukkit.broadcastMessage("§7Config: §f" + laps + " Voltas | " + pits + " Pits");
            Bukkit.broadcastMessage("§6§l═══════════════════════════════════════");

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player creator = Bukkit.getPlayer(proposerUUID);
                if (creator != null && quickRaceManager.createQuickRace(creator, trackName, laps, pits)) {
                    // Criado com sucesso
                }
                currentProposal = null;
            }, 40L);
        }

        private void expire() {
            this.expired = true;
            stopTasks();
            Bukkit.broadcastMessage("§c§lVotação expirada para: " + trackName);
            currentProposal = null;
        }

        private void stopTasks() {
            if (timeoutTask != null) timeoutTask.cancel();
            if (actionBarTask != null) actionBarTask.cancel();
        }

        public boolean hasExpired() { return expired; }
    }
}