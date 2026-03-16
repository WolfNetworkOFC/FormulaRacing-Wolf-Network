//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Command;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Controllers.QuickRaceManager;
import dev.EfraGroup.formulaRacing.Controllers.RaceVoteManager;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Subcommand;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

@CommandAlias("race")
@Description("Comandos de Quick Race")
public class RaceCommand extends BaseCommand {
    private final FormulaRacing plugin;
    private final QuickRaceManager quickRaceManager;

    public RaceCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.quickRaceManager = plugin.getQuickRaceManager();
    }

    private RaceVoteManager getVoteManager() {
        return this.plugin.getRaceVoteManager();
    }

    @Default
    @CatchUnknown
    public void onDefault(Player player) {
        if (this.quickRaceManager.isQuickRaceActive()) {
            this.onInfo(player);
        } else {
            this.sendHelp(player);
        }

    }

    @Subcommand("create|new")
    @CommandPermission("formularacing.race.create")
    @Description("Cria uma quick race")
    @CommandCompletion("@tracks 3|5|10 0|1|2")
    public void onCreate(Player player, String trackName, @Default("3") int laps, @Default("0") int pits) {
        if (laps < 1) {
            this.plugin.sendMessage(player, "race_create_laps_invalid", new String[0]);
        } else if (laps > 100) {
            this.plugin.sendMessage(player, "race_create_laps_max", new String[0]);
        } else if (pits < 0) {
            this.plugin.sendMessage(player, "race_create_pits_invalid", new String[0]);
        } else if (pits >= laps) {
            this.plugin.sendMessage(player, "race_create_pits_max", new String[0]);
        } else {
            boolean created = this.quickRaceManager.createQuickRace(player, trackName, laps, pits);
            if (created) {
                player.sendMessage("");
                this.plugin.sendMessage(player, "race_created_success", new String[0]);
                this.plugin.sendMessage(player, "race_created_track", new String[]{"{track}", trackName});
                this.plugin.sendMessage(player, "race_created_config", new String[]{"{laps}", String.valueOf(laps), "{pits}", String.valueOf(pits)});
                this.plugin.sendMessage(player, "race_created_waiting", new String[0]);
                player.sendMessage("");
                this.plugin.sendMessage(player, "race_created_hint", new String[0]);
                player.sendMessage("");
                List<Player> onlinePlayers = new ArrayList(Bukkit.getOnlinePlayers());
                this.quickRaceManager.sendJoinMessage(onlinePlayers);
            }

        }
    }

    @Subcommand("propose|suggest")
    @Description("Propõe uma corrida")
    @CommandCompletion("@tracks 3|5|10 0|1|2")
    public void onPropose(Player player, String trackName, @Default("3") int laps, @Default("0") int pits) {
        if (this.getVoteManager() == null) {
            this.plugin.sendMessage(player, "race_propose_disabled", new String[0]);
        } else if (laps >= 1 && laps <= 100) {
            if (pits >= 0 && pits < laps) {
                boolean proposed = this.getVoteManager().propose(player, trackName, laps, pits);
                if (proposed) {
                    this.plugin.sendMessage(player, "race_propose_success", new String[0]);
                    this.plugin.sendMessage(player, "race_propose_hint", new String[0]);
                }

            } else {
                this.plugin.sendMessage(player, "race_create_pits_invalid", new String[0]);
            }
        } else {
            this.plugin.sendMessage(player, "race_create_laps_invalid", new String[0]);
        }
    }

    @Subcommand("vote|v")
    @Description("Vota na proposta ativa")
    public void onVote(Player player) {
        if (this.getVoteManager() == null) {
            this.plugin.sendMessage(player, "race_propose_disabled", new String[0]);
        } else {
            this.getVoteManager().vote(player);
        }
    }

    @Subcommand("unvote|novote")
    @Description("Remove seu voto")
    public void onUnvote(Player player) {
        if (this.getVoteManager() == null) {
            this.plugin.sendMessage(player, "race_propose_disabled", new String[0]);
        } else {
            this.getVoteManager().unvote(player);
        }
    }

    @Subcommand("proposal|prop|suggestion")
    @Description("Ver proposta ativa")
    public void onProposal(Player player) {
        if (this.getVoteManager() == null) {
            this.plugin.sendMessage(player, "race_propose_disabled", new String[0]);
        } else {
            this.getVoteManager().showProposalStatus(player);
        }
    }

    @Subcommand("join")
    @Description("Entra na quick race ativa")
    public void onJoin(Player player) {
        boolean joined = this.quickRaceManager.addPlayer(player);
        if (joined) {
            DebugManager var10000 = this.plugin.getDebugManager();
            String var10001 = player.getName();
            var10000.logRaceSystem(var10001 + " entrou na quick race (" + String.valueOf(this.quickRaceManager.getCurrentHeat().map(Heats::getDriverCount).orElse(0)) + " pilotos)");
        }

    }

    @Subcommand("leave|quit|exit")
    @Description("Sai da corrida ou evento atual")
    public void onLeave(Player player) {
        boolean left = this.plugin.getRaceEventManager().leaveEvent(player);
        if (!left) {
            this.plugin.sendMessage(player, "race_leave_error", new String[0]);
        }

    }

    @Subcommand("start|begin")
    @CommandPermission("formularacing.race.start")
    @Description("Inicia a quick race")
    public void onStart(Player player) {
        this.quickRaceManager.startQuickRace(player);
    }

    @Subcommand("end|stop|finish")
    @CommandPermission("formularacing.race.end")
    @Description("Finaliza a quick race")
    public void onEnd(Player player) {
        this.quickRaceManager.endQuickRace(player);
    }

    @Subcommand("info|status")
    @Description("Informações da quick race atual")
    public void onInfo(Player player) {
        Optional<Events> eventOpt = this.quickRaceManager.getCurrentQuickRace();
        Optional<Heats> heatOpt = this.quickRaceManager.getCurrentHeat();
        if (!eventOpt.isEmpty() && !heatOpt.isEmpty()) {
            Events event = (Events)eventOpt.get();
            Heats heat = (Heats)heatOpt.get();
            player.sendMessage("");
            String var10001 = String.valueOf(ChatColor.GOLD);
            player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
            var10001 = String.valueOf(ChatColor.GREEN);
            player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + this.plugin.getTranslation("race_info_header", this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]));
            var10001 = String.valueOf(ChatColor.GOLD);
            player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
            this.plugin.sendMessage(player, "race_created_track", new String[]{"{track}", event.getTrackNameWS()});
            this.plugin.sendMessage(player, "race_created_config", new String[]{"{laps}", String.valueOf(heat.getTotalLaps()), "{pits}", String.valueOf(heat.getTotalPits())});
            var10001 = String.valueOf(ChatColor.GRAY);
            player.sendMessage(var10001 + "Pilotos: " + String.valueOf(ChatColor.WHITE) + heat.getDriverCount() + "/" + heat.getMaxDrivers());
            var10001 = String.valueOf(ChatColor.GRAY);
            player.sendMessage(var10001 + "Estado: " + String.valueOf(this.getStateColor(heat)) + heat.getHeatState().name());
            if (heat.getDriverCount() > 0) {
                player.sendMessage("");
                this.plugin.sendMessage(player, "race_info_pilots", new String[0]);
                heat.getDrivers().values().stream().limit(10L).forEach((driver) -> {
                    Player p = this.plugin.getServer().getPlayer(driver.getUuid());
                    String name = p != null ? p.getName() : "Desconhecido";
                    String status = driver.isFinished() ? this.plugin.getTranslation("race_info_status_finished", this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]) : (driver.isDnf() ? this.plugin.getTranslation("race_info_status_dnf", this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]) : this.plugin.getTranslation("race_info_status_racing", this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]));
                    String var100011 = String.valueOf(ChatColor.GRAY);
                    player.sendMessage(var100011 + "  • " + String.valueOf(ChatColor.WHITE) + name + " " + status);
                });
                if (heat.getDriverCount() > 10) {
                    this.plugin.sendMessage(player, "race_info_more_pilots", new String[]{"{count}", String.valueOf(heat.getDriverCount() - 10)});
                }
            }

            player.sendMessage("");
            if (heat.getHeatState().name().equals("LOADED")) {
                if (player.hasPermission("formularacing.race.start")) {
                    var10001 = String.valueOf(ChatColor.YELLOW);
                    player.sendMessage(var10001 + "► Use " + String.valueOf(ChatColor.WHITE) + "/race start" + String.valueOf(ChatColor.YELLOW) + " para iniciar");
                }

                var10001 = String.valueOf(ChatColor.YELLOW);
                player.sendMessage(var10001 + "► Use " + String.valueOf(ChatColor.WHITE) + "/race join" + String.valueOf(ChatColor.YELLOW) + " para entrar");
            }

            var10001 = String.valueOf(ChatColor.GOLD);
            player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
            player.sendMessage("");
        } else {
            this.plugin.sendMessage(player, "race_info_none", new String[0]);
            this.plugin.sendMessage(player, "race_info_hint", new String[0]);
        }
    }

    private ChatColor getStateColor(Heats heat) {
        ChatColor var10000;
        switch (heat.getHeatState().name()) {
            case "LOADED" -> var10000 = ChatColor.YELLOW;
            case "RACING" -> var10000 = ChatColor.GREEN;
            case "FINISHED" -> var10000 = ChatColor.GRAY;
            default -> var10000 = ChatColor.WHITE;
        }

        return var10000;
    }

    private void sendHelp(Player player) {
        player.sendMessage("");
        String var10001 = String.valueOf(ChatColor.GOLD);
        player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
        var10001 = String.valueOf(ChatColor.GOLD);
        player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + this.plugin.getTranslation("race_help_header", this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]));
        var10001 = String.valueOf(ChatColor.GOLD);
        player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
        var10001 = String.valueOf(ChatColor.YELLOW);
        player.sendMessage(var10001 + "/race propose <pista> [voltas] [pits]" + String.valueOf(ChatColor.GRAY) + " - Propor corrida");
        var10001 = String.valueOf(ChatColor.YELLOW);
        player.sendMessage(var10001 + "/race vote" + String.valueOf(ChatColor.GRAY) + " - Votar em proposta");
        var10001 = String.valueOf(ChatColor.YELLOW);
        player.sendMessage(var10001 + "/race proposal" + String.valueOf(ChatColor.GRAY) + " - Ver proposta ativa");
        var10001 = String.valueOf(ChatColor.YELLOW);
        player.sendMessage(var10001 + "/race join" + String.valueOf(ChatColor.GRAY) + " - Entrar na corrida");
        var10001 = String.valueOf(ChatColor.YELLOW);
        player.sendMessage(var10001 + "/race leave" + String.valueOf(ChatColor.GRAY) + " - Sair da corrida");
        var10001 = String.valueOf(ChatColor.YELLOW);
        player.sendMessage(var10001 + "/race info" + String.valueOf(ChatColor.GRAY) + " - Ver informações");
        if (player.hasPermission("formularacing.race.start")) {
            player.sendMessage("");
            var10001 = String.valueOf(ChatColor.GOLD);
            player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + this.plugin.getTranslation("race_help_admin", this.plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]));
            var10001 = String.valueOf(ChatColor.YELLOW);
            player.sendMessage(var10001 + "/race create <pista> [voltas] [pits]" + String.valueOf(ChatColor.GRAY) + " - Criar direto");
            var10001 = String.valueOf(ChatColor.YELLOW);
            player.sendMessage(var10001 + "/race start" + String.valueOf(ChatColor.GRAY) + " - Iniciar a corrida");
            var10001 = String.valueOf(ChatColor.YELLOW);
            player.sendMessage(var10001 + "/race end" + String.valueOf(ChatColor.GRAY) + " - Finalizar a corrida");
        }

        player.sendMessage("");
        var10001 = String.valueOf(ChatColor.GRAY);
        player.sendMessage(var10001 + "Exemplo: " + String.valueOf(ChatColor.WHITE) + "/race propose Monaco 5 1");
        var10001 = String.valueOf(ChatColor.GOLD);
        player.sendMessage(var10001 + String.valueOf(ChatColor.BOLD) + "═══════════════════════════════");
    }
}
