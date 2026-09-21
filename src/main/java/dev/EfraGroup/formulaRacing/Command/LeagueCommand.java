package dev.EfraGroup.formulaRacing.Command;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import dev.EfraGroup.formulaRacing.Controllers.LeagueManager;
import dev.EfraGroup.formulaRacing.Event.Events;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.League.League;
import dev.EfraGroup.formulaRacing.League.LeagueCalendarEntry;
import dev.EfraGroup.formulaRacing.League.LeagueCategory;
import dev.EfraGroup.formulaRacing.League.LeagueStanding;
import dev.EfraGroup.formulaRacing.League.LeagueTeamStanding;
import dev.EfraGroup.formulaRacing.League.TeamConfig;
import dev.EfraGroup.formulaRacing.League.TeamMode;
import dev.EfraGroup.formulaRacing.Pontuation.PointsConfig;
import dev.EfraGroup.formulaRacing.League.scoring.ScoringRegistry;
import dev.EfraGroup.formulaRacing.Utils.SenderUtils;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.Location;

@CommandAlias("league")
public class LeagueCommand extends BaseCommand {

    private final FormulaRacing plugin;
    private final LeagueManager leagueManager;

    public LeagueCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.leagueManager = plugin.getLeagueManager();
    }

    @Default
    public void onDefault(CommandSender sender) {
        League league = this.selectedLeague(sender).orElse(null);
        if (league == null) {
            plugin.sendMessage(sender, "league_none_selected");
            return;
        }
        showInfo(sender, league);
    }

    @Subcommand("create")
    @CommandPermission("formularacing.event.admin")
    @Description("Cria uma liga")
    public void onCreate(CommandSender sender, String name) {
        try {
            League league = leagueManager.createLeague(this.ownerUuid(sender), name);
            if (league == null) {
                plugin.sendMessage(sender, "league_create_error");
                return;
            }
            Player owner = SenderUtils.player(sender);
            if (owner != null) {
                leagueManager.selectLeague(owner.getUniqueId(), league);
            }
            plugin.sendMessage(sender, "league_created", "{league}", league.getName());
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_create_error");
        }
    }

    @Subcommand("list")
    public void onList(CommandSender sender) {
        plugin.sendMessage(sender, "league_list_header");
        for (League league : leagueManager.getAllLeagues()) {
            plugin.sendMessage(
                sender,
                "league_list_row",
                "{league}",
                league.getName(),
                "{drivers}",
                String.valueOf(league.getDrivers().size())
            );
        }
    }

    @Subcommand("select")
    @CommandCompletion("@leagues")
    public void onSelect(CommandSender sender, String leagueName) {
        League league = leagueManager.getLeagueByName(leagueName).orElse(null);
        if (league == null) {
            plugin.sendMessage(sender, "league_not_found", "{league}", leagueName);
            return;
        }
        Player selector = SenderUtils.player(sender);
        if (selector == null) {
            sender.sendMessage("§cApenas jogadores podem selecionar uma liga.");
            return;
        }
        leagueManager.selectLeague(selector.getUniqueId(), league);
        plugin.sendMessage(sender, "league_selected", "{league}", league.getName());
        showInfo(sender, league);
    }

    @Subcommand("info")
    @CommandCompletion("@leagues")
    public void onInfo(CommandSender sender, @Optional String leagueName) {
        League league = leagueName == null
            ? this.selectedLeague(sender).orElse(null)
            : leagueManager.getLeagueByName(leagueName).orElse(null);
        if (league == null) {
            plugin.sendMessage(sender, "league_none_selected");
            return;
        }
        showInfo(sender, league);
    }

    @Subcommand("addteam")
    @CommandPermission("formularacing.event.admin")
    public void onAddTeam(CommandSender sender, String teamName) {
        League league = this.selectedLeague(sender).orElse(null);
        if (league == null) {
            plugin.sendMessage(sender, "league_none_selected");
            return;
        }
        try {
            if (leagueManager.addTeam(league, teamName) == null) {
                plugin.sendMessage(sender, "league_team_add_error", "{team}", teamName);
                return;
            }
            plugin.sendMessage(
                sender,
                "league_team_added",
                "{team}",
                teamName,
                "{league}",
                league.getName()
            );
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_team_add_error", "{team}", teamName);
        }
    }

    @Subcommand("adddriver")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@players @nothing")
    public void onAddDriver(CommandSender sender, String playerName, @Optional String teamName) {
        League league = this.selectedLeague(sender).orElse(null);
        if (league == null) {
            plugin.sendMessage(sender, "league_none_selected");
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target == null || target.getUniqueId() == null) {
            plugin.sendMessage(sender, "player_not_found");
            return;
        }
        try {
            if (leagueManager.addDriver(league, target.getUniqueId(), teamName) == null) {
                plugin.sendMessage(sender, "league_driver_add_error", "{player}", playerName);
                return;
            }
            plugin.sendMessage(
                sender,
                "league_driver_added",
                "{player}",
                playerName,
                "{league}",
                league.getName()
            );
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_driver_add_error", "{player}", playerName);
        }
    }

    @Subcommand("linkevent")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@event")
    public void onLinkEvent(CommandSender sender, String eventName, @Optional String roundNumberText) {
        League league = this.selectedLeague(sender).orElse(null);
        if (league == null) {
            plugin.sendMessage(sender, "league_none_selected");
            return;
        }
        Events event = plugin.getRaceEventManager().getEventByName(eventName).orElse(null);
        if (event == null) {
            plugin.sendMessage(sender, "event_not_found");
            return;
        }
        int roundNumber = 1;
        if (roundNumberText != null) {
            try {
                roundNumber = Integer.parseInt(roundNumberText);
            } catch (NumberFormatException e) {
                plugin.sendMessage(sender, "invalid_number");
                return;
            }
        }
        if (!leagueManager.linkEvent(league, event, roundNumber)) {
            plugin.sendMessage(sender, "league_link_error");
            return;
        }
        plugin.sendMessage(
            sender,
            "league_event_linked",
            "{league}",
            league.getName(),
            "{event}",
            event.getDisplayName()
        );
    }

    @Subcommand("standings")
    @CommandCompletion("drivers|teams @leagues")
    public void onStandings(CommandSender sender, @Optional String type, @Optional String leagueName) {
        League league = leagueName == null
            ? this.selectedLeague(sender).orElse(null)
            : leagueManager.getLeagueByName(leagueName).orElse(null);
        if (league == null) {
            plugin.sendMessage(sender, "league_none_selected");
            return;
        }

        if ("teams".equalsIgnoreCase(type)) {
            plugin.sendMessage(sender, "league_standings_teams_header", "{league}", league.getName());
            List<LeagueTeamStanding> standings = leagueManager.getTeamStandings(league);
            for (int i = 0; i < standings.size(); i++) {
                LeagueTeamStanding row = standings.get(i);
                plugin.sendMessage(
                    sender,
                    "league_standings_team_row",
                    "{position}",
                    String.valueOf(i + 1),
                    "{team}",
                    row.getTeamName(),
                    "{points}",
                    String.valueOf(row.getPoints())
                );
            }
            return;
        }

        plugin.sendMessage(sender, "league_standings_drivers_header", "{league}", league.getName());
        List<LeagueStanding> standings = leagueManager.getDriverStandings(league);
        for (int i = 0; i < standings.size(); i++) {
            LeagueStanding row = standings.get(i);
            String name = Bukkit.getOfflinePlayer(row.getPlayerUUID()).getName();
            if (name == null) {
                name = row.getPlayerUUID().toString();
            }
            plugin.sendMessage(
                sender,
                "league_standings_driver_row",
                "{position}",
                String.valueOf(i + 1),
                "{player}",
                name,
                "{points}",
                String.valueOf(row.getPoints())
            );
        }
    }

    @Subcommand("delete")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues")
    public void onDelete(CommandSender sender, String leagueName) {
        League league = leagueManager.getLeagueByName(leagueName).orElse(null);
        if (league == null) {
            plugin.sendMessage(sender, "league_not_found", "{league}", leagueName);
            return;
        }
        plugin.getLeagueHologramService().removeHolograms(league);
        leagueManager.getAllLeagues().remove(league);
        plugin.sendMessage(sender, "league_deleted", "{league}", league.getName());
    }

    @Subcommand("addevent")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @event")
    public void onAddEvent(CommandSender sender, String leagueName, String eventName) {
        League league = leagueManager.getLeagueByName(leagueName).orElse(null);
        if (league == null) {
            plugin.sendMessage(sender, "league_not_found", "{league}", leagueName);
            return;
        }
        Events event = plugin.getRaceEventManager().getEventByName(eventName).orElse(null);
        if (event == null) {
            plugin.sendMessage(sender, "event_not_found");
            return;
        }
        try {
            leagueManager.linkEvent(league, event, 1);
            league.getCalendar().putIfAbsent(event.getId(), new LeagueCalendarEntry(event.getId()));
            plugin.sendMessage(sender, "league_event_linked",
                "{league}", league.getName(), "{event}", event.getDisplayName());
        } catch (Exception e) {
            plugin.sendMessage(sender, "league_link_error");
        }
    }

    @Subcommand("removeevent")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @event")
    public void onRemoveEvent(CommandSender sender, String leagueName, String eventName) {
        League league = leagueManager.getLeagueByName(leagueName).orElse(null);
        if (league == null) {
            plugin.sendMessage(sender, "league_not_found", "{league}", leagueName);
            return;
        }
        Events event = plugin.getRaceEventManager().getEventByName(eventName).orElse(null);
        if (event == null) {
            plugin.sendMessage(sender, "event_not_found");
            return;
        }
        league.getCalendar().remove(event.getId());
        plugin.sendMessage(sender, "league_event_unlinked",
            "{league}", league.getName(), "{event}", event.getDisplayName());
    }

    @Subcommand("seteventcategory")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @event @nothing")
    public void onSetEventCategory(CommandSender sender, String leagueName, String eventName, String categoryName) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        Events event = plugin.getRaceEventManager().getEventByName(eventName).orElse(null);
        if (event == null) {
            plugin.sendMessage(sender, "event_not_found");
            return;
        }
        try {
            leagueManager.setEventMeta(league, event.getId(),
                categoryName.isBlank() ? null : categoryName, null);
            plugin.sendMessage(sender, "league_event_category_set",
                "{event}", event.getDisplayName(), "{category}", categoryName);
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_link_error");
        }
    }

    @Subcommand("seteventheat")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @event @nothing")
    public void onSetEventHeat(CommandSender sender, String leagueName, String eventName, Integer heatId) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        Events event = plugin.getRaceEventManager().getEventByName(eventName).orElse(null);
        if (event == null) {
            plugin.sendMessage(sender, "event_not_found");
            return;
        }
        try {
            leagueManager.setEventMeta(league, event.getId(), null, heatId);
            plugin.sendMessage(sender, "league_event_heat_set",
                "{event}", event.getDisplayName(), "{heat}", String.valueOf(heatId));
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_link_error");
        }
    }

    @Subcommand("scoring")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @nothing @nothing")
    public void onScoring(CommandSender sender, String leagueName, String systemId, @Optional String categoryName) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        if (!ScoringRegistry.exists(systemId)) {
            plugin.sendMessage(sender, "league_scoring_invalid", "{system}", systemId);
            return;
        }
        try {
            if (categoryName != null && !categoryName.isBlank()) {
                LeagueCategory cat = league.getCategory(categoryName);
                if (cat == null) {
                    plugin.sendMessage(sender, "league_category_not_found", "{category}", categoryName);
                    return;
                }
                cat.setScoringSystem(systemId.toUpperCase());
                leagueManager.saveLeagueConfig(league);
                leagueManager.recalculate(league);
            } else {
                league.setScoringSystem(systemId.toUpperCase());
                leagueManager.saveLeagueConfig(league);
                leagueManager.recalculate(league);
            }
            plugin.sendMessage(sender, "league_scoring_set", "{system}", systemId);
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_link_error");
        }
    }

    @Subcommand("teammode")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @nothing")
    public void onTeamMode(CommandSender sender, String leagueName, String mode) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        try {
            league.setTeamMode(TeamMode.valueOf(mode.toUpperCase()));
            leagueManager.saveLeagueConfig(league);
            plugin.sendMessage(sender, "league_teammode_set", "{mode}", mode);
        } catch (IllegalArgumentException e) {
            plugin.sendMessage(sender, "league_teammode_invalid", "{mode}", mode);
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_link_error");
        }
    }

    @Subcommand("teamconfig")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @nothing @nothing @nothing")
    public void onTeamConfig(CommandSender sender, String leagueName, Integer maxMains,
                             Integer maxReserves, Integer countedScorers) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        TeamConfig cfg = league.getTeamConfig();
        if (maxMains != null) cfg.setMaxMains(maxMains);
        if (maxReserves != null) cfg.setMaxReserves(maxReserves);
        if (countedScorers != null) cfg.setCountedScorers(countedScorers);
        try {
            leagueManager.saveLeagueConfig(league);
            plugin.sendMessage(sender, "league_teamconfig_set");
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_link_error");
        }
    }

    @Subcommand("customscale")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @nothing @nothing")
    public void onCustomScale(CommandSender sender, String leagueName, Integer position, Integer points) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        if (league.getCustomScale() == null) {
            league.setCustomScale(new PointsConfig("custom"));
        }
        league.getCustomScale().getRacePoints().put(position, points);
        try {
            leagueManager.saveLeagueConfig(league);
            leagueManager.recalculate(league);
            plugin.sendMessage(sender, "league_customscale_set",
                "{position}", String.valueOf(position), "{points}", String.valueOf(points));
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_link_error");
        }
    }

    @Subcommand("mulligans")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @nothing @nothing")
    public void onMulligans(CommandSender sender, String leagueName, Integer count,
                            @Optional String categoryName) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        try {
            if (categoryName != null && !categoryName.isBlank()) {
                LeagueCategory cat = league.getCategory(categoryName);
                if (cat == null) {
                    plugin.sendMessage(sender, "league_category_not_found", "{category}", categoryName);
                    return;
                }
                cat.setMulliganCount(count);
            } else {
                league.setMulliganCount(count);
            }
            leagueManager.saveLeagueConfig(league);
            leagueManager.recalculate(league);
            plugin.sendMessage(sender, "league_mulligans_set", "{count}", String.valueOf(count));
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_link_error");
        }
    }

    @Subcommand("category")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @nothing")
    public void onCategory(CommandSender sender, String leagueName, String action, String categoryName) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        try {
            if ("add".equalsIgnoreCase(action)) {
                leagueManager.addCategory(league, categoryName);
                plugin.sendMessage(sender, "league_category_added", "{category}", categoryName);
            } else if ("remove".equalsIgnoreCase(action)) {
                leagueManager.removeCategory(league, categoryName);
                plugin.sendMessage(sender, "league_category_removed", "{category}", categoryName);
            } else {
                plugin.sendMessage(sender, "league_category_usage");
            }
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_link_error");
        }
    }

    @Subcommand("calendar")
    @CommandCompletion("@leagues")
    public void onCalendar(CommandSender sender, String leagueName) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        plugin.sendMessage(sender, "league_calendar_header", "{league}", league.getName());
        for (LeagueCalendarEntry entry : league.getCalendar().values()) {
            Events event = plugin.getRaceEventManager().getEventById(entry.getEventId()).orElse(null);
            String name = event != null ? event.getDisplayName() : String.valueOf(entry.getEventId());
            plugin.sendMessage(sender, "league_calendar_row",
                "{event}", name,
                "{category}", entry.hasCategory() ? entry.getCategoryName() : "-",
                "{heat}", entry.hasPinnedHeat() ? String.valueOf(entry.getPinnedHeatId()) : "-");
        }
    }

    @Subcommand("recalculate")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues")
    public void onRecalculate(CommandSender sender, String leagueName) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        leagueManager.recalculate(league);
        plugin.sendMessage(sender, "league_recalculated", "{league}", league.getName());
    }

    @Subcommand("holo")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues drivers|teams create|remove")
    public void onHolo(CommandSender sender, String leagueName, String scope, String action) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        if ("create".equalsIgnoreCase(action)) {
            Player player = SenderUtils.player(sender);
            if (player == null) {
                sender.sendMessage("§cCriar holograma exige um jogador (usa a sua posição).");
                return;
            }
            Location loc = player.getLocation();
            if ("teams".equalsIgnoreCase(scope)) {
                plugin.getLeagueHologramService().createTeamHologram(league, loc);
            } else {
                plugin.getLeagueHologramService().createDriverHologram(league, loc);
            }
            plugin.sendMessage(sender, "league_holo_created",
                "{scope}", scope, "{league}", league.getName());
        } else if ("remove".equalsIgnoreCase(action)) {
            plugin.getLeagueHologramService().removeHolograms(league);
            plugin.sendMessage(sender, "league_holo_removed", "{league}", league.getName());
        } else {
            plugin.getLeagueHologramService().updateHolograms(league);
            plugin.sendMessage(sender, "league_holo_updated", "{league}", league.getName());
        }
    }

    @Subcommand("breakdown")
    @CommandCompletion("@leagues")
    public void onBreakdown(CommandSender sender, String leagueName) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        plugin.sendMessage(sender, "league_breakdown_header", "{league}", league.getName());
        List<LeagueStanding> standings = leagueManager.getDriverStandings(league);
        for (LeagueStanding row : standings) {
            String name = Bukkit.getOfflinePlayer(row.getPlayerUUID()).getName();
            if (name == null) name = row.getPlayerUUID().toString();
            plugin.sendMessage(sender, "league_breakdown_row",
                "{player}", name, "{points}", String.valueOf(row.getPoints()));
        }
    }

    @Subcommand("givepoints")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @players @nothing")
    public void onGivePoints(CommandSender sender, String leagueName, String targetName, Integer amount) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getUniqueId() == null) {
            plugin.sendMessage(sender, "player_not_found");
            return;
        }
        leagueManager.adjustPoints(league, target.getUniqueId(), amount);
        plugin.sendMessage(sender, "league_points_given",
            "{player}", targetName, "{amount}", String.valueOf(amount), "{league}", league.getName());
    }

    @Subcommand("takepoints")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @players @nothing")
    public void onTakePoints(CommandSender sender, String leagueName, String targetName, Integer amount) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (target.getUniqueId() == null) {
            plugin.sendMessage(sender, "player_not_found");
            return;
        }
        leagueManager.adjustPoints(league, target.getUniqueId(), -Math.abs(amount));
        plugin.sendMessage(sender, "league_points_taken",
            "{player}", targetName, "{amount}", String.valueOf(amount), "{league}", league.getName());
    }

    @Subcommand("transferpoints")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @players @players @nothing")
    public void onTransferPoints(CommandSender sender, String leagueName, String fromName, String toName,
                                 Integer amount) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        OfflinePlayer from = Bukkit.getOfflinePlayer(fromName);
        OfflinePlayer to = Bukkit.getOfflinePlayer(toName);
        if (from.getUniqueId() == null || to.getUniqueId() == null) {
            plugin.sendMessage(sender, "player_not_found");
            return;
        }
        leagueManager.transferPoints(league, from.getUniqueId(), to.getUniqueId(), Math.abs(amount));
        plugin.sendMessage(sender, "league_points_transferred",
            "{from}", fromName, "{to}", toName, "{amount}", String.valueOf(amount),
            "{league}", league.getName());
    }

    /** Player's selected league, or empty for the console. */
    private java.util.Optional<League> selectedLeague(CommandSender sender) {
        Player player = SenderUtils.player(sender);
        return player != null
            ? leagueManager.getSelectedLeague(player.getUniqueId())
            : java.util.Optional.empty();
    }

    /** Owner UUID for a league created from the console (no player available). */
    private UUID ownerUuid(CommandSender sender) {
        Player player = SenderUtils.player(sender);
        return player != null ? player.getUniqueId() : new UUID(0L, 0L);
    }

    private League requireLeague(CommandSender sender, String leagueName) {
        League league = leagueManager.getLeagueByName(leagueName).orElse(null);
        if (league == null) {
            plugin.sendMessage(sender, "league_not_found", "{league}", leagueName);
        }
        return league;
    }

    private void showInfo(CommandSender sender, League league) {
        plugin.sendMessage(sender, "league_info_header", "{league}", league.getName());
        plugin.sendMessage(
            sender,
            "league_info_status",
            "{status}",
            league.getStatus().name()
        );
        plugin.sendMessage(
            sender,
            "league_info_teams",
            "{teams}",
            String.valueOf(league.getTeams().size())
        );
        plugin.sendMessage(
            sender,
            "league_info_drivers",
            "{drivers}",
            String.valueOf(league.getDrivers().size())
        );
    }
}
