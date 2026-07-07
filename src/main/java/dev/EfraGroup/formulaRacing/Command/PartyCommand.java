package dev.EfraGroup.formulaRacing.Command;

import dev.EfraGroup.formulaRacing.Command.Help.CommandHelpService;
import dev.EfraGroup.formulaRacing.Controllers.PartyRaceManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Flags;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent.Action;
import net.md_5.bungee.api.chat.hover.content.Content;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@CommandAlias("party")
@Description("Party commands")
public class PartyCommand extends BaseCommand {
    private final FormulaRacing plugin;
    private final DatabaseManager dm;
    private final Map<UUID, UUID> pendingInvites = new HashMap();

    public PartyCommand(FormulaRacing plugin) {
        this.plugin = plugin;
        this.dm = plugin.getDatabaseManager();
    }

    @Default
    @CatchUnknown
    public void onDefault(Player player) {
        this.sendHelp(player);
    }

    @Subcommand("help|ajuda|?")
    @Description("Shows the party command help")
    public void onHelp(Player player) {
        this.sendHelp(player);
    }

    @Subcommand("create")
    @Description("Creates a new party")
    public void onCreate(Player player) {
        try {
            if (this.dm.hasParty(player.getUniqueId())) {
                player.sendMessage("§cYou are already in a party.");
                return;
            }

            this.dm.createParty(player.getUniqueId());
            player.sendMessage("§aParty created successfully!");
        } catch (SQLException var3) {
            player.sendMessage("§cError creating party.");
        }

    }

    @Subcommand("invite")
    @Description("Invites a player to the party")
    @CommandCompletion("@players")
    public void onInvite(Player player, String targetName) {
        try {
            if (!this.dm.hasParty(player.getUniqueId())) {
                player.sendMessage("§cYou do not have a party.");
                return;
            }

            UUID owner = this.dm.getOwner(player.getUniqueId());
            if (!owner.equals(player.getUniqueId())) {
                player.sendMessage("§cOnly the leader can invite players.");
                return;
            }

            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null || !target.isOnline()) {
                player.sendMessage("§cPlayer not found.");
                return;
            }

            if (this.dm.hasParty(target.getUniqueId())) {
                player.sendMessage("§cThat player is already in a party.");
                return;
            }

            String var10002 = String.valueOf(ChatColor.YELLOW);
            TextComponent base = new TextComponent(var10002 + player.getName() + String.valueOf(ChatColor.GREEN) + " invited you to a party!\n" + String.valueOf(ChatColor.DARK_GRAY) + "---=        ");
            TextComponent accept = new TextComponent("ACCEPT");
            accept.setColor(ChatColor.GREEN);
            accept.setBold(true);
            accept.setClickEvent(new ClickEvent(Action.RUN_COMMAND, "/party accept " + player.getName()));
            accept.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new Content[]{new Text(String.valueOf(ChatColor.GRAY) + "Click to accept invite")}));
            TextComponent space = new TextComponent("  ");
            TextComponent deny = new TextComponent("DENY");
            deny.setColor(ChatColor.GREEN);
            deny.setBold(true);
            deny.setClickEvent(new ClickEvent(Action.RUN_COMMAND, "/party deny " + player.getName()));
            deny.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new Content[]{new Text(String.valueOf(ChatColor.GRAY) + "Click to deny invite")}));
            TextComponent end = new TextComponent("        =---");
            end.setColor(ChatColor.DARK_GRAY);
            base.addExtra(accept);
            base.addExtra(space);
            base.addExtra(deny);
            base.addExtra(end);
            target.spigot().sendMessage(base);
            this.pendingInvites.put(target.getUniqueId(), player.getUniqueId());
            player.sendMessage("§aInvite sent to §e" + target.getName() + "§a.");
        } catch (SQLException var10) {
            player.sendMessage("§cError sending invite.");
        }

    }

    @Subcommand("accept")
    @Description("Accepts a party invite")
    @CommandCompletion("@players")
    public void onAccept(Player player, String ownerName) {
        try {
            UUID invited = player.getUniqueId();
            if (!this.pendingInvites.containsKey(invited)) {
                player.sendMessage("§cYou have no pending invites.");
                return;
            }

            UUID owner = (UUID)this.pendingInvites.get(invited);
            Player ownerPlayer = Bukkit.getPlayer(owner);
            if (ownerPlayer == null || !ownerPlayer.isOnline()) {
                player.sendMessage("§cThe party leader is no longer online.");
                this.pendingInvites.remove(invited);
                return;
            }

            if (!ownerPlayer.getName().equalsIgnoreCase(ownerName)) {
                player.sendMessage("§cThis invite is not from that player.");
                return;
            }

            if (!this.dm.hasParty(owner)) {
                player.sendMessage("§cThis party no longer exists.");
                this.pendingInvites.remove(invited);
                return;
            }

            if (this.dm.hasParty(invited)) {
                player.sendMessage("§cVocê já está em uma party.");
                this.pendingInvites.remove(invited);
                return;
            }

            this.dm.addMember(owner, invited);
            this.pendingInvites.remove(invited);
            player.sendMessage("§aYou joined the party of §e" + ownerPlayer.getName() + "§a.");
            ownerPlayer.sendMessage("§a" + player.getName() + " joined your party.");
        } catch (SQLException var6) {
            player.sendMessage("§cError accepting invite.");
        }

    }

    @Subcommand("deny")
    @Description("Denies a party invite")
    @CommandCompletion("@players")
    public void onDeny(Player player, String ownerName) {
        UUID invited = player.getUniqueId();
        if (!this.pendingInvites.containsKey(invited)) {
            player.sendMessage("§cVocê não tem convites pendentes.");
        } else {
            UUID owner = (UUID)this.pendingInvites.get(invited);
            Player ownerPlayer = Bukkit.getPlayer(owner);
            if (ownerPlayer != null && ownerPlayer.getName().equalsIgnoreCase(ownerName)) {
                this.pendingInvites.remove(invited);
                player.sendMessage("§cYou denied the invite from §e" + ownerPlayer.getName() + ".");
                if (ownerPlayer.isOnline()) {
                    ownerPlayer.sendMessage("§c" + player.getName() + " denied the party invite.");
                }

            } else {
                player.sendMessage("§cThis invite is not from that player.");
            }
        }
    }

    @Subcommand("remove")
    @Description("Removes a player from the party")
    @CommandCompletion("@players")
    public void onRemove(Player player, String targetName) {
        try {
            if (!this.dm.hasParty(player.getUniqueId())) {
                player.sendMessage("§cVocê não tem uma party.");
                return;
            }

            UUID owner = this.dm.getOwner(player.getUniqueId());
            if (!owner.equals(player.getUniqueId())) {
                player.sendMessage("§cOnly the leader can remove players.");
                return;
            }

            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                player.sendMessage("§cPlayer not found.");
                return;
            }

            if (target.getUniqueId().equals(owner)) {
                player.sendMessage("§cYou cannot remove the leader.");
                return;
            }

            this.dm.removeMember(owner, target.getUniqueId());
            player.sendMessage("§cJogador §e" + target.getName() + " §cremoved from party.");
            target.sendMessage("§cYou were removed from the party.");
        } catch (SQLException var5) {
            player.sendMessage("§cError removing player.");
        }

    }

    @Subcommand("leave")
    @Description("Leaves the current party")
    public void onLeave(Player player) {
        try {
            if (!this.dm.hasParty(player.getUniqueId())) {
                player.sendMessage("§cYou are not in a party.");
                return;
            }

            UUID owner = this.dm.getOwner(player.getUniqueId());
            if (owner.equals(player.getUniqueId())) {
                this.dm.disbandParty(owner);
                player.sendMessage("§cYou disbanded the party.");
            } else {
                this.dm.removeMember(owner, player.getUniqueId());
                player.sendMessage("§cYou left the party.");
            }
        } catch (SQLException var3) {
            player.sendMessage("§cError leaving party.");
        }

    }

    @Subcommand("disband")
    @Description("Disbands the current party")
    public void onDisband(Player player) {
        try {
            if (!this.dm.hasParty(player.getUniqueId())) {
                player.sendMessage("§cVocê não tem uma party.");
                return;
            }

            UUID owner = this.dm.getOwner(player.getUniqueId());
            if (!owner.equals(player.getUniqueId())) {
                player.sendMessage("§cOnly the leader can disband the party.");
                return;
            }

            this.dm.disbandParty(owner);
            player.sendMessage("§cParty disbanded.");
        } catch (SQLException var3) {
            player.sendMessage("§cError disbanding party.");
        }

    }

    @Subcommand("info")
    @Description("Shows party information")
    public void onInfo(Player player) {
        try {
            if (!this.dm.hasParty(player.getUniqueId())) {
                player.sendMessage("§cYou are not in a party.");
                return;
            }

            UUID owner = this.dm.getOwner(player.getUniqueId());
            String membersRaw = this.dm.getMembers(owner);
            player.sendMessage("§6§lParty Info");
            Player ownerP = Bukkit.getPlayer(owner);
            String var10001 = ownerP != null ? ownerP.getName() : "Offline (" + String.valueOf(owner) + ")";
            player.sendMessage("§eLeader: §f" + var10001);
            player.sendMessage("§eMembers:");
            Arrays.stream(membersRaw.split(",")).forEach((uuidStr) -> {
                if (!uuidStr.isEmpty()) {
                    UUID uuid = UUID.fromString(uuidStr);
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        player.sendMessage(" §7- §f" + p.getName());
                    } else {
                        player.sendMessage(" §7- §8Offline (" + String.valueOf(uuid) + ")");
                    }

                }
            });
        } catch (SQLException var5) {
            player.sendMessage("§cError fetching party information.");
        }

    }

    @Subcommand("race")
    @Description("Starts a private race for the party")
    @Syntax("<track> [laps] [pits]")
    @CommandCompletion("@tracks")
    public void onRace(Player player, String trackName, @Flags("default:3") int laps, @Flags("default:0") int pits) {
        PartyRaceManager prm = plugin.getPartyRaceManager();
        if (prm == null) {
            player.sendMessage("§cParty races are not available.");
            return;
        }
        prm.createPartyRace(player, trackName, laps, pits);
    }

    @Subcommand("promote")
    @Description("Transfers party leadership to another member")
    @CommandCompletion("@partyMembers")
    public void onPromote(Player player, @Flags("other") Player target) {
        try {
            if (!dm.hasParty(player.getUniqueId())) {
                player.sendMessage("§cVocê não tem uma party.");
                return;
            }

            UUID owner = dm.getOwner(player.getUniqueId());
            if (!owner.equals(player.getUniqueId())) {
                player.sendMessage("§cOnly the leader can promote another member.");
                return;
            }

            if (target.getUniqueId().equals(owner)) {
                player.sendMessage("§cYou are already the party leader.");
                return;
            }

            String membersRaw = dm.getMembers(owner);
            boolean isMember = Arrays.stream(membersRaw.split(","))
                    .anyMatch(s -> !s.isEmpty() && UUID.fromString(s).equals(target.getUniqueId()));
            if (!isMember) {
                player.sendMessage("§cThat player is not in your party.");
                return;
            }

            dm.removeMember(owner, target.getUniqueId());
            dm.createParty(target.getUniqueId());
            String remainingMembers = Arrays.stream(membersRaw.split(","))
                    .filter(s -> !s.isEmpty())
                    .filter(s -> !UUID.fromString(s).equals(target.getUniqueId()))
                    .collect(Collectors.joining(","));
            if (!remainingMembers.isEmpty()) {
                for (String s : remainingMembers.split(",")) {
                    if (!s.isEmpty()) dm.addMember(target.getUniqueId(), UUID.fromString(s));
                }
            }

            player.sendMessage("§e" + target.getName() + " §ais the new party leader.");
            target.sendMessage("§aYou are now the party leader.");
        } catch (SQLException e) {
            player.sendMessage("§cError promoting player.");
        }
    }

    private void sendHelp(Player player) {
        CommandHelpService.sendHelp(player, this, "/party");
    }
}







