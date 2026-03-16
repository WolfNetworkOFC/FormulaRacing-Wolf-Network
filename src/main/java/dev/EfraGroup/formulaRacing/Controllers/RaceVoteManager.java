//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Controllers;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent.Action;
import net.md_5.bungee.api.chat.hover.content.Content;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class RaceVoteManager {
    private final FormulaRacing plugin;
    private final DatabaseManager database;
    private final QuickRaceManager quickRaceManager;
    private RaceProposal currentProposal;
    private static final int MIN_VOTES_REQUIRED = 3;
    private static final int PROPOSAL_TIMEOUT_SECONDS = 120;

    public RaceVoteManager(FormulaRacing plugin, DatabaseManager database, QuickRaceManager quickRaceManager) {
        this.plugin = plugin;
        this.database = database;
        this.quickRaceManager = quickRaceManager;
    }

    public boolean propose(Player proposer, String trackName, int laps, int pits) {
        if (this.currentProposal != null && !this.currentProposal.hasExpired()) {
            this.plugin.sendMessage(proposer, "vote_proposal_active_error", new String[0]);
            this.currentProposal.showStatus(proposer);
            return false;
        } else if (this.quickRaceManager.isQuickRaceActive()) {
            this.plugin.sendMessage(proposer, "vote_quickrace_active_error", new String[0]);
            this.plugin.sendMessage(proposer, "vote_wait_finish", new String[0]);
            return false;
        } else {
            DatabaseManager.TrackData trackData = this.database.getTrackData(trackName);
            if (trackData == null) {
                this.plugin.sendMessage(proposer, "vote_track_not_found", new String[]{"{track}", trackName});
                return false;
            } else {
                String finalTrackName = trackData.getTrackName();
                laps = Math.max(1, Math.min(100, laps));
                pits = Math.max(0, Math.min(laps - 1, pits));
                this.currentProposal = new RaceProposal(proposer, finalTrackName, laps, pits);
                this.currentProposal.start();
                DebugManager var10000 = this.plugin.getDebugManager();
                String var10001 = proposer.getName();
                var10000.logRaceSystem("Proposta de corrida criada por " + var10001 + " para " + trackName);
                return true;
            }
        }
    }

    public boolean vote(Player player) {
        if (this.currentProposal != null && !this.currentProposal.hasExpired()) {
            return this.currentProposal.addVote(player);
        } else {
            this.plugin.sendMessage(player, "vote_none_active_error", new String[0]);
            this.plugin.sendMessage(player, "vote_none_active_hint", new String[0]);
            return false;
        }
    }

    public boolean unvote(Player player) {
        if (this.currentProposal != null && !this.currentProposal.hasExpired()) {
            return this.currentProposal.removeVote(player);
        } else {
            this.plugin.sendMessage(player, "vote_none_active_error", new String[0]);
            return false;
        }
    }

    public boolean cancelProposal(Player canceller) {
        if (this.currentProposal != null && !this.currentProposal.hasExpired()) {
            if (!this.currentProposal.isProposer(canceller) && !canceller.hasPermission("formularacing.voterace.cancel")) {
                this.plugin.sendMessage(canceller, "vote_cancel_error_perm", new String[0]);
                return false;
            } else {
                this.currentProposal.cancel();
                this.currentProposal = null;

                for(Player p : Bukkit.getOnlinePlayers()) {
                    this.plugin.sendMessage(p, "vote_cancelled", new String[]{"{player}", canceller.getName()});
                }

                return true;
            }
        } else {
            this.plugin.sendMessage(canceller, "vote_cancel_error_none", new String[0]);
            return false;
        }
    }

    public void showProposalStatus(Player player) {
        if (this.currentProposal != null && !this.currentProposal.hasExpired()) {
            this.currentProposal.showStatus(player);
        } else {
            this.plugin.sendMessage(player, "vote_cancel_error_none", new String[0]);
            player.sendMessage("");
            this.plugin.sendMessage(player, "vote_help_propose", new String[0]);
            player.sendMessage("");
            String var10001 = String.valueOf(ChatColor.GRAY);
            player.sendMessage(var10001 + "Exemplo: " + String.valueOf(ChatColor.WHITE) + "/race propose Monaco 5 1");
        }
    }

    public boolean isProposalActive() {
        return this.currentProposal != null && !this.currentProposal.hasExpired();
    }

    public Optional<RaceProposal> getCurrentProposal() {
        return Optional.ofNullable(this.currentProposal);
    }

    public class RaceProposal {
        private final UUID proposerUUID;
        private final String proposerName;
        private final String trackName;
        private final int laps;
        private final int pits;
        private final Set<UUID> voters;
        private final long createdAt;
        private BukkitTask timeoutTask;
        private BukkitTask actionBarTask;
        private boolean expired;

        private void startActionBarTask() {
            this.actionBarTask = Bukkit.getScheduler().runTaskTimer(RaceVoteManager.this.plugin, () -> {
                String msg = this.buildActionBarMessage();

                for(Player p : Bukkit.getOnlinePlayers()) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
                }

            }, 0L, 20L);
        }

        private void stopActionBarTask() {
            if (this.actionBarTask != null && !this.actionBarTask.isCancelled()) {
                this.actionBarTask.cancel();

                for(Player p : Bukkit.getOnlinePlayers()) {
                    p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(""));
                }
            }

        }

        private String buildActionBarMessage() {
            long elapsedMs = System.currentTimeMillis() - this.createdAt;
            long remaining = Math.max(0L, 120L - elapsedMs / 1000L);
            int current = this.voters.size();
            double progress = Math.min((double)1.0F, (double)current / (double)3.0F);
            String bar = this.buildProgressBar(progress, 10);
            String var10000 = String.valueOf(ChatColor.AQUA);
            return var10000 + "QuickRace: " + String.valueOf(ChatColor.WHITE) + this.trackName + " " + bar + String.valueOf(ChatColor.AQUA) + " (" + current + "/3) " + String.valueOf(ChatColor.GRAY) + remaining + "s";
        }

        private String buildProgressBar(double progress, int length) {
            int filled = (int)Math.round(progress * (double)length);
            StringBuilder sb = new StringBuilder("§7[");

            for(int i = 0; i < length; ++i) {
                if (i < filled) {
                    sb.append("§b|");
                } else {
                    sb.append("§8|");
                }
            }

            sb.append("§7]");
            return sb.toString();
        }

        public RaceProposal(Player proposer, String trackName, int laps, int pits) {
            this.proposerUUID = proposer.getUniqueId();
            this.proposerName = proposer.getName();
            this.trackName = trackName;
            this.laps = laps;
            this.pits = pits;
            this.voters = ConcurrentHashMap.newKeySet();
            this.createdAt = System.currentTimeMillis();
            this.expired = false;
            this.voters.add(this.proposerUUID);
        }

        public void start() {
            this.broadcastProposalCreated();
            this.startActionBarTask();
            this.timeoutTask = Bukkit.getScheduler().runTaskLater(RaceVoteManager.this.plugin, () -> {
                if (!this.expired) {
                    this.expire();
                }

            }, 2400L);
        }

        public boolean addVote(Player player) {
            if (this.expired) {
                RaceVoteManager.this.plugin.sendMessage(player, "vote_expired", new String[0]);
                return false;
            } else {
                UUID playerUUID = player.getUniqueId();
                if (this.voters.contains(playerUUID)) {
                    RaceVoteManager.this.plugin.sendMessage(player, "vote_already_voted", new String[0]);
                    this.showStatus(player);
                    return false;
                } else {
                    this.voters.add(playerUUID);
                    RaceVoteManager.this.plugin.sendMessage(player, "vote_registered", new String[0]);

                    for(Player p : Bukkit.getOnlinePlayers()) {
                        if (!p.equals(player)) {
                            String var10001 = String.valueOf(ChatColor.GRAY);
                            p.sendMessage(var10001 + "► " + String.valueOf(ChatColor.WHITE) + player.getName() + String.valueOf(ChatColor.GREEN) + " votou na proposta! " + String.valueOf(ChatColor.GRAY) + "(" + this.voters.size() + "/3)");
                            if (!this.voters.contains(p.getUniqueId())) {
                                TextComponent quickVoteButton = new TextComponent("[Votar também]");
                                quickVoteButton.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
                                quickVoteButton.setClickEvent(new ClickEvent(Action.RUN_COMMAND, "/race vote"));
                                quickVoteButton.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new Content[]{new Text("Clique para votar!")}));
                                p.spigot().sendMessage(quickVoteButton);
                            }
                        }
                    }

                    if (this.voters.size() >= 3) {
                        this.approve();
                    }

                    return true;
                }
            }
        }

        public boolean removeVote(Player player) {
            UUID playerUUID = player.getUniqueId();
            if (playerUUID.equals(this.proposerUUID)) {
                RaceVoteManager.this.plugin.sendMessage(player, "vote_cant_remove_self", new String[0]);
                return false;
            } else if (!this.voters.contains(playerUUID)) {
                RaceVoteManager.this.plugin.sendMessage(player, "vote_not_voted", new String[0]);
                return false;
            } else {
                this.voters.remove(playerUUID);
                RaceVoteManager.this.plugin.sendMessage(player, "vote_removed", new String[0]);

                for(Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.equals(player)) {
                        String var10001 = String.valueOf(ChatColor.GRAY);
                        p.sendMessage(var10001 + "◄ " + String.valueOf(ChatColor.WHITE) + player.getName() + String.valueOf(ChatColor.RED) + " removeu seu voto " + String.valueOf(ChatColor.GRAY) + "(" + this.voters.size() + "/3)");
                    }
                }

                return true;
            }
        }

        private void approve() {
            this.stopActionBarTask();
            if (!this.expired) {
                this.expired = true;
                if (this.timeoutTask != null) {
                    this.timeoutTask.cancel();
                }

                for(Player p : Bukkit.getOnlinePlayers()) {
                    p.sendMessage("");
                    String var10001 = String.valueOf(ChatColor.GOLD);
                    p.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
                    String approvedMsg = RaceVoteManager.this.plugin.getTranslation("vote_approved", RaceVoteManager.this.plugin.getDatabaseManager().getPlayerLanguage(p.getUniqueId()), new String[0]);
                    var10001 = String.valueOf(ChatColor.GREEN);
                    p.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + approvedMsg);
                    var10001 = String.valueOf(ChatColor.GOLD);
                    p.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
                    var10001 = String.valueOf(ChatColor.GRAY);
                    p.sendMessage(var10001 + "Pista: " + String.valueOf(ChatColor.WHITE) + String.valueOf(ChatColor.BOLD) + this.trackName);
                    var10001 = String.valueOf(ChatColor.GRAY);
                    p.sendMessage(var10001 + "Voltas: " + String.valueOf(ChatColor.WHITE) + this.laps + String.valueOf(ChatColor.GRAY) + " | Pits: " + String.valueOf(ChatColor.WHITE) + this.pits);
                    var10001 = String.valueOf(ChatColor.GRAY);
                    p.sendMessage(var10001 + "Votos: " + String.valueOf(ChatColor.WHITE) + this.voters.size() + " jogadores");
                    p.sendMessage("");
                    RaceVoteManager.this.plugin.sendMessage(p, "vote_creating", new String[0]);
                    var10001 = String.valueOf(ChatColor.GOLD);
                    p.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
                    p.sendMessage("");
                }

                Bukkit.getScheduler().runTaskLater(RaceVoteManager.this.plugin, () -> {
                    Player creator = Bukkit.getPlayer(this.proposerUUID);
                    if (creator == null) {
                        creator = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
                    }

                    if (creator != null) {
                        boolean created = RaceVoteManager.this.quickRaceManager.createQuickRace(creator, this.trackName, this.laps, this.pits);
                        if (created) {
                            List<Player> voterPlayers = this.voters.stream().map(Bukkit::getPlayer).filter(Objects::nonNull).toList();
                            RaceVoteManager.this.quickRaceManager.sendJoinMessage(voterPlayers);
                        } else {
                            for(Player p : Bukkit.getOnlinePlayers()) {
                                p.sendMessage(ChatColor.RED + "✗ Erro ao criar Quick Race!");
                            }
                        }
                    }

                    RaceVoteManager.this.currentProposal = null;
                }, 40L);
            }
        }

        private void expire() {
            this.stopActionBarTask();
            if (!this.expired) {
                this.expired = true;

                for(Player p : Bukkit.getOnlinePlayers()) {
                    RaceVoteManager.this.plugin.sendMessage(p, "vote_expired", new String[0]);
                    String var10001 = String.valueOf(ChatColor.GRAY);
                    p.sendMessage(var10001 + "Pista: " + String.valueOf(ChatColor.WHITE) + this.trackName + String.valueOf(ChatColor.GRAY) + " (faltaram " + (3 - this.voters.size()) + " votos)");
                }

                RaceVoteManager.this.currentProposal = null;
            }
        }

        public void cancel() {
            this.stopActionBarTask();
            if (!this.expired) {
                this.expired = true;
                if (this.timeoutTask != null) {
                    this.timeoutTask.cancel();
                }

            }
        }

        public void showStatus(Player player) {
            player.sendMessage("");
            String var10001 = String.valueOf(ChatColor.GOLD);
            player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
            var10001 = String.valueOf(ChatColor.YELLOW);
            player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "    \ud83d\udccb PROPOSTA DE CORRIDA");
            var10001 = String.valueOf(ChatColor.GOLD);
            player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
            var10001 = String.valueOf(ChatColor.GRAY);
            player.sendMessage(var10001 + "Pista: " + String.valueOf(ChatColor.WHITE) + this.trackName);
            var10001 = String.valueOf(ChatColor.GRAY);
            player.sendMessage(var10001 + "Voltas: " + String.valueOf(ChatColor.WHITE) + this.laps + String.valueOf(ChatColor.GRAY) + " | Pits: " + String.valueOf(ChatColor.WHITE) + this.pits);
            var10001 = String.valueOf(ChatColor.GRAY);
            player.sendMessage(var10001 + "Proposto por: " + String.valueOf(ChatColor.WHITE) + this.proposerName);
            player.sendMessage("");
            int progress = this.voters.size() * 10 / 3;
            StringBuilder bar = new StringBuilder(String.valueOf(ChatColor.GRAY) + "[");

            for(int i = 0; i < 10; ++i) {
                if (i < progress) {
                    bar.append(ChatColor.GREEN).append("▰");
                } else {
                    bar.append(ChatColor.DARK_GRAY).append("▱");
                }
            }

            bar.append(ChatColor.GRAY).append("]");
            var10001 = String.valueOf(ChatColor.AQUA);
            player.sendMessage(var10001 + "Votos: " + String.valueOf(ChatColor.WHITE) + this.voters.size() + "/3 " + String.valueOf(bar));
            long elapsed = (System.currentTimeMillis() - this.createdAt) / 1000L;
            long remaining = 120L - elapsed;
            var10001 = String.valueOf(ChatColor.GRAY);
            player.sendMessage(var10001 + "Tempo restante: " + String.valueOf(ChatColor.WHITE) + remaining + "s");
            player.sendMessage("");
            boolean hasVoted = this.voters.contains(player.getUniqueId());
            if (hasVoted) {
                RaceVoteManager.this.plugin.sendMessage(player, "vote_already_voted", new String[0]);
                if (!player.getUniqueId().equals(this.proposerUUID)) {
                    TextComponent unvoteButton = new TextComponent("[Remover Voto]");
                    unvoteButton.setColor(net.md_5.bungee.api.ChatColor.RED);
                    unvoteButton.setBold(true);
                    unvoteButton.setClickEvent(new ClickEvent(Action.RUN_COMMAND, "/race unvote"));
                    unvoteButton.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new Content[]{new Text("Clique para remover seu voto")}));
                    player.spigot().sendMessage(unvoteButton);
                }
            } else {
                TextComponent voteButton = new TextComponent("► VOTAR AGORA");
                voteButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                voteButton.setBold(true);
                voteButton.setClickEvent(new ClickEvent(Action.RUN_COMMAND, "/race vote"));
                voteButton.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new Content[]{new Text("Clique para votar nesta proposta!")}));
                player.spigot().sendMessage(voteButton);
            }

            var10001 = String.valueOf(ChatColor.GOLD);
            player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
            player.sendMessage("");
        }

        private void broadcastProposalCreated() {
            for(Player p : Bukkit.getOnlinePlayers()) {
                p.sendMessage("");
                String var10001 = String.valueOf(ChatColor.GOLD);
                p.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
                var10001 = String.valueOf(ChatColor.GREEN);
                p.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "   \ud83d\udccb NOVA PROPOSTA DE CORRIDA!");
                var10001 = String.valueOf(ChatColor.GOLD);
                p.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
                var10001 = String.valueOf(ChatColor.WHITE);
                p.sendMessage(var10001 + this.proposerName + String.valueOf(ChatColor.GRAY) + " propôs uma corrida:");
                p.sendMessage("");
                var10001 = String.valueOf(ChatColor.GRAY);
                p.sendMessage(var10001 + "\ud83c\udfc1 Pista: " + String.valueOf(ChatColor.WHITE) + String.valueOf(ChatColor.BOLD) + this.trackName);
                var10001 = String.valueOf(ChatColor.GRAY);
                p.sendMessage(var10001 + "\ud83d\udd04 Voltas: " + String.valueOf(ChatColor.WHITE) + this.laps + String.valueOf(ChatColor.GRAY) + " | Pits: " + String.valueOf(ChatColor.WHITE) + this.pits);
                var10001 = String.valueOf(ChatColor.GRAY);
                p.sendMessage(var10001 + "\ud83d\udcca Votos necessários: " + String.valueOf(ChatColor.WHITE) + "3");
                var10001 = String.valueOf(ChatColor.GRAY);
                p.sendMessage(var10001 + "⏱ Tempo: " + String.valueOf(ChatColor.WHITE) + "120s");
                p.sendMessage("");
                TextComponent voteButton = new TextComponent("► CLIQUE AQUI PARA VOTAR");
                voteButton.setColor(net.md_5.bungee.api.ChatColor.GREEN);
                voteButton.setBold(true);
                voteButton.setClickEvent(new ClickEvent(Action.RUN_COMMAND, "/race vote"));
                voteButton.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new Content[]{new Text("Clique para votar nesta proposta!")}));
                p.spigot().sendMessage(voteButton);
                TextComponent statusButton = new TextComponent("[Ver detalhes]");
                statusButton.setColor(net.md_5.bungee.api.ChatColor.YELLOW);
                statusButton.setClickEvent(new ClickEvent(Action.RUN_COMMAND, "/race proposal"));
                statusButton.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new Content[]{new Text("Ver status completo da proposta")}));
                p.spigot().sendMessage(statusButton);
                var10001 = String.valueOf(ChatColor.GOLD);
                p.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
                p.sendMessage("");
            }

        }

        public boolean hasExpired() {
            return this.expired;
        }

        public String getTrackName() {
            return this.trackName;
        }

        public int getLaps() {
            return this.laps;
        }

        public int getPits() {
            return this.pits;
        }

        public int getVoteCount() {
            return this.voters.size();
        }

        public String getProposerName() {
            return this.proposerName;
        }

        public boolean isProposer(Player player) {
            return player.getUniqueId().equals(this.proposerUUID);
        }

        public boolean hasVoted(UUID playerUUID) {
            return this.voters.contains(playerUUID);
        }
    }
}
