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
import dev.EfraGroup.formulaRacing.League.LeagueDriver;
import dev.EfraGroup.formulaRacing.League.LeagueStanding;
import dev.EfraGroup.formulaRacing.League.LeagueTeam;
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
    public void onStandings(CommandSender sender, @Optional String type,
                            @Optional String leagueName, @Optional String toggle) {
        if (leagueName != null && (leagueName.equalsIgnoreCase("true") || leagueName.equalsIgnoreCase("false"))) {
            toggle = leagueName;
            leagueName = null;
        }
        League league = leagueName == null
            ? this.selectedLeague(sender).orElse(null)
            : leagueManager.getLeagueByName(leagueName).orElse(null);
        if (league == null) {
            plugin.sendMessage(sender, "league_none_selected");
            return;
        }

        if (toggle != null && (toggle.equalsIgnoreCase("true") || toggle.equalsIgnoreCase("false"))
            && type != null && (type.equalsIgnoreCase("drivers") || type.equalsIgnoreCase("teams"))
            && sender.hasPermission("formularacing.event.admin")) {
            boolean enabled = Boolean.parseBoolean(toggle);
            try {
                leagueManager.setStandingsEnabled(
                    league,
                    type.equalsIgnoreCase("drivers") ? enabled : league.isDriverStandingsEnabled(),
                    type.equalsIgnoreCase("teams") ? enabled : league.isTeamStandingsEnabled()
                );
                plugin.sendMessage(sender, "league_recalculated", "{league}", league.getName());
            } catch (SQLException e) {
                plugin.sendMessage(sender, "league_link_error");
            }
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
            String name = row.getPlayerName();
            if (name == null || name.isBlank()) {
                name = Bukkit.getOfflinePlayer(row.getPlayerUUID()).getName();
            }
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
        leagueManager.deleteLeague(league);
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
            if (!leagueManager.linkEvent(league, event, 1)) {
                plugin.sendMessage(sender, "league_link_error");
                return;
            }
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
        leagueManager.unlinkEvent(league, event.getId());
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
            String name = row.getPlayerName();
            if (name == null || name.isBlank()) name = Bukkit.getOfflinePlayer(row.getPlayerUUID()).getName();
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

    private String driverName(UUID uuid) {
        if (uuid == null) {
            return "?";
        }
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return (name == null || name.isBlank()) ? uuid.toString() : name;
    }

    @Subcommand("team list")
    @CommandCompletion("@leagues")
    public void onTeamList(CommandSender sender, @Optional String leagueName) {
        League league = leagueName == null
            ? this.selectedLeague(sender).orElse(null)
            : leagueManager.getLeagueByName(leagueName).orElse(null);
        if (league == null) {
            plugin.sendMessage(sender, "league_none_selected");
            return;
        }
        for (LeagueTeam team : league.getTeams().values()) {
            StringBuilder members = new StringBuilder();
            for (UUID main : team.getMainDrivers()) {
                members.append(" [MAIN] ").append(this.driverName(main));
            }
            for (UUID reserve : team.getReserveDrivers()) {
                members.append(" [RES] ").append(this.driverName(reserve));
            }
            for (UUID priority : team.getPriorityDrivers()) {
                members.append(" [P").append(team.getPriority(priority)).append("] ").append(this.driverName(priority));
            }
            sender.sendMessage("§6" + team.getName()
                + " §7(id: " + team.getId()
                + (team.getOwner() != null ? ", owner: " + this.driverName(team.getOwner()) : "")
                + ")" + members);
        }
    }

    @Subcommand("team invite")
    @CommandCompletion("@leagues @nothing @players")
    public void onTeamInvite(CommandSender sender, String teamName, String playerName) {
        League league = this.selectedLeague(sender).orElse(null);
        if (league == null) {
            plugin.sendMessage(sender, "league_none_selected");
            return;
        }
        Integer teamId = leagueManager.findTeamId(league, teamName);
        if (teamId == null) {
            sender.sendMessage("§cTime não encontrado: " + teamName);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null) {
            plugin.sendMessage(sender, "player_not_found");
            return;
        }
        LeagueTeam team = league.getTeams().get(teamId);
        Player inviter = SenderUtils.player(sender);
        if (inviter != null && team.getOwner() != null && !team.isOwner(inviter.getUniqueId())
            && !sender.hasPermission("formularacing.event.admin")) {
            sender.sendMessage("§cApenas o dono do time pode convidar jogadores.");
            return;
        }
        leagueManager.inviteDriver(league, teamId, target.getUniqueId(), inviter == null ? null : inviter.getUniqueId());
        sender.sendMessage("§aConvite enviado para §e" + playerName + " §a(o time §e" + team.getName() + "§a).");
    }

    @Subcommand("team accept")
    public void onTeamAccept(CommandSender sender) {
        Player player = SenderUtils.player(sender);
        if (player == null) {
            sender.sendMessage("§cApenas jogadores podem aceitar convites.");
            return;
        }
        LeagueManager.PendingInvite invite = leagueManager.peekInvite(player.getUniqueId());
        if (invite == null) {
            sender.sendMessage("§cVocê não tem convites pendentes.");
            return;
        }
        League league = leagueManager.getLeagueById(invite.leagueId()).orElse(null);
        if (league == null) {
            leagueManager.pollInvite(player.getUniqueId());
            sender.sendMessage("§cA liga desse convite não existe mais.");
            return;
        }
        try {
            if (!leagueManager.acceptInvite(player.getUniqueId())) {
                sender.sendMessage("§cNão foi possível aceitar o convite (time cheio?).");
                return;
            }
            sender.sendMessage("§aVocê entrou no time §e" + league.getTeams().get(invite.teamId()).getName() + "§a.");
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_driver_add_error", "{player}", player.getName());
        }
    }

    @Subcommand("team decline")
    public void onTeamDecline(CommandSender sender) {
        Player player = SenderUtils.player(sender);
        if (player == null) {
            sender.sendMessage("§cApenas jogadores podem recusar convites.");
            return;
        }
        if (leagueManager.pollInvite(player.getUniqueId()) == null) {
            sender.sendMessage("§cVocê não tem convites pendentes.");
            return;
        }
        sender.sendMessage("§aConvite recusado.");
    }

    @Subcommand("team kick")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @players")
    public void onTeamKick(CommandSender sender, String leagueName, String playerName) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null) {
            plugin.sendMessage(sender, "player_not_found");
            return;
        }
        try {
            if (!leagueManager.removeDriver(league, target.getUniqueId())) {
                sender.sendMessage("§cJogador não encontrado nesta liga.");
                return;
            }
            sender.sendMessage("§e" + playerName + " §eremovido da liga §e" + league.getName() + "§r.");
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_driver_add_error", "{player}", playerName);
        }
    }

    @Subcommand("team promote")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @players")
    public void onTeamPromote(CommandSender sender, String leagueName, String playerName) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null) {
            plugin.sendMessage(sender, "player_not_found");
            return;
        }
        try {
            if (!leagueManager.promoteDriver(league, target.getUniqueId())) {
                sender.sendMessage("§cNão foi possível promover (não é reserva ou time cheio).");
                return;
            }
            sender.sendMessage("§e" + playerName + " §apromovido a main.");
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_driver_add_error", "{player}", playerName);
        }
    }

    @Subcommand("team demote")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @players")
    public void onTeamDemote(CommandSender sender, String leagueName, String playerName) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null) {
            plugin.sendMessage(sender, "player_not_found");
            return;
        }
        try {
            if (!leagueManager.demoteDriver(league, target.getUniqueId())) {
                sender.sendMessage("§cNão foi possível rebaixar (não é main ou time cheio).");
                return;
            }
            sender.sendMessage("§e" + playerName + " §erebaixado a reserva.");
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_driver_add_error", "{player}", playerName);
        }
    }

    @Subcommand("team setowner")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @nothing @players")
    public void onTeamSetOwner(CommandSender sender, String leagueName, String teamName, String playerName) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        Integer teamId = leagueManager.findTeamId(league, teamName);
        if (teamId == null) {
            sender.sendMessage("§cTime não encontrado: " + teamName);
            return;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null) {
            plugin.sendMessage(sender, "player_not_found");
            return;
        }
        try {
            leagueManager.setTeamOwner(league, teamId, target.getUniqueId());
            sender.sendMessage("§aDono do time §e" + teamName + " §aagora é §e" + playerName + "§a.");
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_link_error");
        }
    }

    @Subcommand("team leave")
    public void onTeamLeave(CommandSender sender) {
        Player player = SenderUtils.player(sender);
        if (player == null) {
            sender.sendMessage("§cApenas jogadores podem sair do time.");
            return;
        }
        League league = this.selectedLeague(sender).orElse(null);
        if (league == null) {
            plugin.sendMessage(sender, "league_none_selected");
            return;
        }
        LeagueDriver driver = league.getDrivers().get(player.getUniqueId());
        if (driver == null || driver.getTeamId() == null) {
            sender.sendMessage("§cVocê não está em nenhum time.");
            return;
        }
        LeagueTeam team = league.getTeams().get(driver.getTeamId());
        if (team != null && team.isOwner(player.getUniqueId()) && team.getMembers().size() > 1) {
            sender.sendMessage("§cTransfira a propriedade do time antes de sair.");
            return;
        }
        try {
            leagueManager.removeDriver(league, player.getUniqueId());
            sender.sendMessage("§cVocê saiu do time.");
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_driver_add_error", "{player}", player.getName());
        }
    }

    @Subcommand("setmain")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @players @nothing")
    public void onSetMain(CommandSender sender, String leagueName, String playerName) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null) {
            plugin.sendMessage(sender, "player_not_found");
            return;
        }
        try {
            if (!leagueManager.promoteDriver(league, target.getUniqueId())) {
                sender.sendMessage("§cNão foi possível definir como main (limite do time atingido?).");
                return;
            }
            sender.sendMessage("§e" + playerName + " §aagora é main.");
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_driver_add_error", "{player}", playerName);
        }
    }

    @Subcommand("setreserve")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @players @nothing")
    public void onSetReserve(CommandSender sender, String leagueName, String playerName) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null) {
            plugin.sendMessage(sender, "player_not_found");
            return;
        }
        try {
            if (!leagueManager.demoteDriver(league, target.getUniqueId())) {
                sender.sendMessage("§cNão foi possível definir como reserva (limite do time atingido?).");
                return;
            }
            sender.sendMessage("§e" + playerName + " §aagora é reserva.");
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_driver_add_error", "{player}", playerName);
        }
    }

    @Subcommand("setpriority")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues @players @nothing @nothing")
    public void onSetPriority(CommandSender sender, String leagueName, String playerName, Integer priority) {
        League league = requireLeague(sender, leagueName);
        if (league == null) return;
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (target.getUniqueId() == null) {
            plugin.sendMessage(sender, "player_not_found");
            return;
        }
        try {
            if (!leagueManager.setDriverPriority(league, target.getUniqueId(), priority)) {
                sender.sendMessage("§cJogador não está em um time.");
                return;
            }
            sender.sendMessage("§e" + playerName + " §aagora tem prioridade §e" + priority + "§a.");
        } catch (SQLException e) {
            plugin.sendMessage(sender, "league_driver_add_error", "{player}", playerName);
        }
    }

    @Subcommand("export")
    @CommandPermission("formularacing.event.admin")
    @CommandCompletion("@leagues")
    public void onExport(CommandSender sender, @Optional String leagueName) {
        League league = leagueName == null
            ? this.selectedLeague(sender).orElse(null)
            : leagueManager.getLeagueByName(leagueName).orElse(null);
        if (league == null) {
            plugin.sendMessage(sender, "league_none_selected");
            return;
        }
        java.io.File file = leagueManager.exportCsv(league);
        if (file == null) {
            sender.sendMessage("§cErro ao exportar CSV.");
            return;
        }
        sender.sendMessage("§aCSV exportado para §e" + file.getName() + "§a.");
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
