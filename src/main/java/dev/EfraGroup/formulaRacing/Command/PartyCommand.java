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
@Description("Comandos de party")
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
    @Description("Mostra a ajuda do comando party")
    public void onHelp(Player player) {
        this.sendHelp(player);
    }

    @Subcommand("create")
    @Description("Cria uma nova party")
    public void onCreate(Player player) {
        try {
            if (this.dm.hasParty(player.getUniqueId())) {
                player.sendMessage("§cVocê já está em uma party.");
                return;
            }

            this.dm.createParty(player.getUniqueId());
            player.sendMessage("§aParty criada com sucesso!");
        } catch (SQLException var3) {
            player.sendMessage("§cErro ao criar party.");
        }

    }

    @Subcommand("invite")
    @Description("Convida um jogador para a party")
    @CommandCompletion("@players")
    public void onInvite(Player player, String targetName) {
        try {
            if (!this.dm.hasParty(player.getUniqueId())) {
                player.sendMessage("§cVocê não tem uma party.");
                return;
            }

            UUID owner = this.dm.getOwner(player.getUniqueId());
            if (!owner.equals(player.getUniqueId())) {
                player.sendMessage("§cApenas o líder pode convidar jogadores.");
                return;
            }

            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null || !target.isOnline()) {
                player.sendMessage("§cJogador não encontrado.");
                return;
            }

            if (this.dm.hasParty(target.getUniqueId())) {
                player.sendMessage("§cEsse jogador já está em uma party.");
                return;
            }

            String var10002 = String.valueOf(ChatColor.YELLOW);
            TextComponent base = new TextComponent(var10002 + player.getName() + String.valueOf(ChatColor.GREEN) + " convidou você para uma party!\n" + String.valueOf(ChatColor.DARK_GRAY) + "---=        ");
            TextComponent accept = new TextComponent("ACEITAR");
            accept.setColor(ChatColor.GREEN);
            accept.setBold(true);
            accept.setClickEvent(new ClickEvent(Action.RUN_COMMAND, "/party accept " + player.getName()));
            accept.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new Content[]{new Text(String.valueOf(ChatColor.GRAY) + "Clique para aceitar o convite")}));
            TextComponent space = new TextComponent("  ");
            TextComponent deny = new TextComponent("NEGAR");
            deny.setColor(ChatColor.GREEN);
            deny.setBold(true);
            deny.setClickEvent(new ClickEvent(Action.RUN_COMMAND, "/party deny " + player.getName()));
            deny.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new Content[]{new Text(String.valueOf(ChatColor.GRAY) + "Clique para negar o convite")}));
            TextComponent end = new TextComponent("        =---");
            end.setColor(ChatColor.DARK_GRAY);
            base.addExtra(accept);
            base.addExtra(space);
            base.addExtra(deny);
            base.addExtra(end);
            target.spigot().sendMessage(base);
            this.pendingInvites.put(target.getUniqueId(), player.getUniqueId());
            player.sendMessage("§aConvite enviado para §e" + target.getName() + "§a.");
        } catch (SQLException var10) {
            player.sendMessage("§cErro ao enviar convite.");
        }

    }

    @Subcommand("accept")
    @Description("Aceita um convite de party")
    @CommandCompletion("@players")
    public void onAccept(Player player, String ownerName) {
        try {
            UUID invited = player.getUniqueId();
            if (!this.pendingInvites.containsKey(invited)) {
                player.sendMessage("§cVocê não tem convites pendentes.");
                return;
            }

            UUID owner = (UUID)this.pendingInvites.get(invited);
            Player ownerPlayer = Bukkit.getPlayer(owner);
            if (ownerPlayer == null || !ownerPlayer.isOnline()) {
                player.sendMessage("§cO líder da party não está mais online.");
                this.pendingInvites.remove(invited);
                return;
            }

            if (!ownerPlayer.getName().equalsIgnoreCase(ownerName)) {
                player.sendMessage("§cEsse convite não é desse jogador.");
                return;
            }

            if (!this.dm.hasParty(owner)) {
                player.sendMessage("§cEssa party não existe mais.");
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
            player.sendMessage("§aVocê entrou na party de §e" + ownerPlayer.getName() + "§a.");
            ownerPlayer.sendMessage("§a" + player.getName() + " entrou na sua party.");
        } catch (SQLException var6) {
            player.sendMessage("§cErro ao aceitar convite.");
        }

    }

    @Subcommand("deny")
    @Description("Recusa um convite de party")
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
                player.sendMessage("§cVocê recusou o convite de §e" + ownerPlayer.getName() + ".");
                if (ownerPlayer.isOnline()) {
                    ownerPlayer.sendMessage("§c" + player.getName() + " recusou o convite para a party.");
                }

            } else {
                player.sendMessage("§cEsse convite não é desse jogador.");
            }
        }
    }

    @Subcommand("remove")
    @Description("Remove um jogador da party")
    @CommandCompletion("@players")
    public void onRemove(Player player, String targetName) {
        try {
            if (!this.dm.hasParty(player.getUniqueId())) {
                player.sendMessage("§cVocê não tem uma party.");
                return;
            }

            UUID owner = this.dm.getOwner(player.getUniqueId());
            if (!owner.equals(player.getUniqueId())) {
                player.sendMessage("§cApenas o líder pode remover jogadores.");
                return;
            }

            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                player.sendMessage("§cJogador não encontrado.");
                return;
            }

            if (target.getUniqueId().equals(owner)) {
                player.sendMessage("§cVocê não pode remover o líder.");
                return;
            }

            this.dm.removeMember(owner, target.getUniqueId());
            player.sendMessage("§cJogador §e" + target.getName() + " §cremovido da party.");
            target.sendMessage("§cVocê foi removido da party.");
        } catch (SQLException var5) {
            player.sendMessage("§cErro ao remover jogador.");
        }

    }

    @Subcommand("leave")
    @Description("Sai da party atual")
    public void onLeave(Player player) {
        try {
            if (!this.dm.hasParty(player.getUniqueId())) {
                player.sendMessage("§cVocê não está em uma party.");
                return;
            }

            UUID owner = this.dm.getOwner(player.getUniqueId());
            if (owner.equals(player.getUniqueId())) {
                this.dm.disbandParty(owner);
                player.sendMessage("§cVocê dissolveu a party.");
            } else {
                this.dm.removeMember(owner, player.getUniqueId());
                player.sendMessage("§cVocê saiu da party.");
            }
        } catch (SQLException var3) {
            player.sendMessage("§cErro ao sair da party.");
        }

    }

    @Subcommand("disband")
    @Description("Dissolve a party atual")
    public void onDisband(Player player) {
        try {
            if (!this.dm.hasParty(player.getUniqueId())) {
                player.sendMessage("§cVocê não tem uma party.");
                return;
            }

            UUID owner = this.dm.getOwner(player.getUniqueId());
            if (!owner.equals(player.getUniqueId())) {
                player.sendMessage("§cApenas o líder pode dissolver a party.");
                return;
            }

            this.dm.disbandParty(owner);
            player.sendMessage("§cParty dissolvida.");
        } catch (SQLException var3) {
            player.sendMessage("§cErro ao dissolver party.");
        }

    }

    @Subcommand("info")
    @Description("Mostra informações da party")
    public void onInfo(Player player) {
        try {
            if (!this.dm.hasParty(player.getUniqueId())) {
                player.sendMessage("§cVocê não está em uma party.");
                return;
            }

            UUID owner = this.dm.getOwner(player.getUniqueId());
            String membersRaw = this.dm.getMembers(owner);
            player.sendMessage("§6§lParty Info");
            Player ownerP = Bukkit.getPlayer(owner);
            String var10001 = ownerP != null ? ownerP.getName() : "Offline (" + String.valueOf(owner) + ")";
            player.sendMessage("§eLíder: §f" + var10001);
            player.sendMessage("§eMembros:");
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
            player.sendMessage("§cErro ao buscar informações da party.");
        }

    }

    @Subcommand("race")
    @Description("Inicia uma corrida privada para a party")
    @Syntax("<track> [laps] [pits]")
    @CommandCompletion("@tracks")
    public void onRace(Player player, String trackName, @Flags("default:3") int laps, @Flags("default:0") int pits) {
        PartyRaceManager prm = plugin.getPartyRaceManager();
        if (prm == null) {
            player.sendMessage("§cParty races não estão disponíveis.");
            return;
        }
        prm.createPartyRace(player, trackName, laps, pits);
    }

    @Subcommand("promote")
    @Description("Transfere a liderança da party para outro membro")
    @CommandCompletion("@partyMembers")
    public void onPromote(Player player, @Flags("other") Player target) {
        try {
            if (!dm.hasParty(player.getUniqueId())) {
                player.sendMessage("§cVocê não tem uma party.");
                return;
            }

            UUID owner = dm.getOwner(player.getUniqueId());
            if (!owner.equals(player.getUniqueId())) {
                player.sendMessage("§cApenas o líder pode promover outro membro.");
                return;
            }

            if (target.getUniqueId().equals(owner)) {
                player.sendMessage("§cVocê já é o líder da party.");
                return;
            }

            String membersRaw = dm.getMembers(owner);
            boolean isMember = Arrays.stream(membersRaw.split(","))
                    .anyMatch(s -> !s.isEmpty() && UUID.fromString(s).equals(target.getUniqueId()));
            if (!isMember) {
                player.sendMessage("§cEsse jogador não está na sua party.");
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

            player.sendMessage("§e" + target.getName() + " §aé o novo líder da party.");
            target.sendMessage("§aVocê agora é o líder da party.");
        } catch (SQLException e) {
            player.sendMessage("§cErro ao promover jogador.");
        }
    }

    private void sendHelp(Player player) {
        CommandHelpService.sendHelp(player, this, "/party");
    }
}
