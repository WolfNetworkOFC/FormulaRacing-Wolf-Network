package dev.EfraGroup.formulaRacing.CommandHandler;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PartyCommandHandler implements CommandExecutor {

    private final Map<UUID, UUID> pendingInvites = new HashMap<>();

    private final DatabaseManager dm;

    public PartyCommandHandler(DatabaseManager dm) {
        this.dm = dm;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command cannot be executed from the console.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        try {
            switch (args[0].toLowerCase()) {

                case "create" -> handleCreate(player);
                case "invite" -> handleInvite(player, args);
                case "remove" -> handleRemove(player, args);
                case "leave" -> handleLeave(player);
                case "disband" -> handleDisband(player);
                case "info" -> handleInfo(player);
                case "accept" -> handleAccept(player, args);
                case "deny" -> handleDeny(player, args);

                default -> sendHelp(player);
            }
        } catch (SQLException e) {
            player.sendMessage("§cErro interno ao acessar o banco de dados.");
            e.printStackTrace();
        }

        return true;
    }

    // =====================
    // SUBCOMMANDS
    // =====================

    private void handleCreate(Player player) throws SQLException {
        if (dm.hasParty(player.getUniqueId())) {
            player.sendMessage("§cVocê já está em uma party.");
            return;
        }

        dm.createParty(player.getUniqueId());
        player.sendMessage("§aParty criada com sucesso!");
    }

    private void handleInvite(Player player, String[] args) throws SQLException {

        if (args.length != 2) {
            player.sendMessage("§cUso: /party invite <player>");
            return;
        }

        if (!dm.hasParty(player.getUniqueId())) {
            player.sendMessage("§cVocê não tem uma party.");
            return;
        }

        UUID owner = dm.getOwner(player.getUniqueId());
        if (!owner.equals(player.getUniqueId())) {
            player.sendMessage("§cApenas o líder pode convidar jogadores.");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null || !target.isOnline()) {
            player.sendMessage("§cJogador não encontrado.");
            return;
        }

        if (dm.hasParty(target.getUniqueId())) {
            player.sendMessage("§cEsse jogador já está em uma party.");
            return;
        }

        // =========================
        // TEXTO BASE
        // =========================
        TextComponent base = new TextComponent(
                ChatColor.YELLOW + player.getName()
                        + ChatColor.GREEN + " convidou você para uma party!\n"
                        + ChatColor.DARK_GRAY + "---=        "
        );

        // =========================
        // ACEITAR
        // =========================
        TextComponent accept = new TextComponent("ACEITAR");
        accept.setColor(ChatColor.GREEN);
        accept.setBold(true);
        accept.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/party accept " + player.getName()
        ));
        accept.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new Text(ChatColor.GRAY + "Clique para aceitar o convite")
        ));

        // =========================
        // ESPAÇO
        // =========================
        TextComponent space = new TextComponent("  ");

        // =========================
        // NEGAR
        // =========================
        TextComponent deny = new TextComponent("NEGAR");
        deny.setColor(ChatColor.GREEN);
        deny.setBold(true);
        deny.setClickEvent(new ClickEvent(
                ClickEvent.Action.RUN_COMMAND,
                "/party deny " + player.getName()
        ));
        deny.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new Text(ChatColor.GRAY + "Clique para negar o convite")
        ));

        // =========================
        // SUFIXO
        // =========================
        TextComponent end = new TextComponent("        =---");
        end.setColor(ChatColor.DARK_GRAY);

        // =========================
        // MONTAGEM FINAL
        // =========================
        base.addExtra(accept);
        base.addExtra(space);
        base.addExtra(deny);
        base.addExtra(end);

        // ENVIO CORRETO NO SPIGOT
        target.spigot().sendMessage(base);

        pendingInvites.put(target.getUniqueId(), player.getUniqueId());

        player.sendMessage("§aConvite enviado para §e" + target.getName() + "§a.");
    }





    private void handleAccept(Player player, String[] args) throws SQLException {
        if (args.length != 2) {
            player.sendMessage("§cUso: /party accept <player>");
            return;
        }

        UUID invited = player.getUniqueId();

        // Verifica se existe convite
        if (!pendingInvites.containsKey(invited)) {
            player.sendMessage("§cVocê não tem convites pendentes.");
            return;
        }

        UUID owner = pendingInvites.get(invited);

        Player ownerPlayer = Bukkit.getPlayer(owner);
        if (ownerPlayer == null || !ownerPlayer.isOnline()) {
            player.sendMessage("§cO líder da party não está mais online.");
            pendingInvites.remove(invited);
            return;
        }

        // Confere se o nome bate (segurança)
        if (!ownerPlayer.getName().equalsIgnoreCase(args[1])) {
            player.sendMessage("§cEsse convite não é desse jogador.");
            return;
        }

        // Verifica se o líder ainda tem party
        if (!dm.hasParty(owner)) {
            player.sendMessage("§cEssa party não existe mais.");
            pendingInvites.remove(invited);
            return;
        }

        // Verifica se o jogador já entrou em alguma party
        if (dm.hasParty(invited)) {
            player.sendMessage("§cVocê já está em uma party.");
            pendingInvites.remove(invited);
            return;
        }

        // ✅ ADICIONA NA PARTY
        dm.addMember(owner, invited);

        // Remove convite
        pendingInvites.remove(invited);

        // Mensagens
        player.sendMessage("§aVocê entrou na party de §e" + ownerPlayer.getName() + "§a.");
        ownerPlayer.sendMessage("§a" + player.getName() + " entrou na sua party.");
    }


    private void handleRemove(Player player, String[] args) throws SQLException {
        if (args.length != 2) {
            player.sendMessage("§cUso: /party remove <player>");
            return;
        }

        if (!dm.hasParty(player.getUniqueId())) {
            player.sendMessage("§cVocê não tem uma party.");
            return;
        }

        UUID owner = dm.getOwner(player.getUniqueId());
        if (!owner.equals(player.getUniqueId())) {
            player.sendMessage("§cApenas o líder pode remover jogadores.");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            player.sendMessage("§cJogador não encontrado.");
            return;
        }

        if (target.getUniqueId().equals(owner)) {
            player.sendMessage("§cVocê não pode remover o líder.");
            return;
        }

        dm.removeMember(owner, target.getUniqueId());

        player.sendMessage("§cJogador §e" + target.getName() + " §cremovido da party.");
        target.sendMessage("§cVocê foi removido da party.");
    }

    private void handleDeny(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage("§cUso: /party deny <player>");
            return;
        }

        UUID invited = player.getUniqueId();

        // Verifica se existe convite pendente
        if (!pendingInvites.containsKey(invited)) {
            player.sendMessage("§cVocê não tem convites pendentes.");
            return;
        }

        UUID owner = pendingInvites.get(invited);

        Player ownerPlayer = Bukkit.getPlayer(owner);

        // Confere se o nome bate
        if (ownerPlayer == null || !ownerPlayer.getName().equalsIgnoreCase(args[1])) {
            player.sendMessage("§cEsse convite não é desse jogador.");
            return;
        }

        // Remove convite
        pendingInvites.remove(invited);

        // Mensagens
        player.sendMessage("§cVocê recusou o convite de §e" + ownerPlayer.getName() + ".");
        ownerPlayer.sendMessage("§c" + player.getName() + " recusou o convite para a party.");
    }


    private void handleLeave(Player player) throws SQLException {
        if (!dm.hasParty(player.getUniqueId())) {
            player.sendMessage("§cVocê não está em uma party.");
            return;
        }

        UUID owner = dm.getOwner(player.getUniqueId());

        if (owner.equals(player.getUniqueId())) {
            dm.disbandParty(owner);
            player.sendMessage("§cVocê dissolveu a party.");
        } else {
            dm.removeMember(owner, player.getUniqueId());
            player.sendMessage("§cVocê saiu da party.");
        }
    }

    private void handleDisband(Player player) throws SQLException {
        if (!dm.hasParty(player.getUniqueId())) {
            player.sendMessage("§cVocê não tem uma party.");
            return;
        }

        UUID owner = dm.getOwner(player.getUniqueId());
        if (!owner.equals(player.getUniqueId())) {
            player.sendMessage("§cApenas o líder pode dissolver a party.");
            return;
        }

        dm.disbandParty(owner);
        player.sendMessage("§cParty dissolvida.");
    }

    private void handleInfo(Player player) throws SQLException {
        if (!dm.hasParty(player.getUniqueId())) {
            player.sendMessage("§cVocê não está em uma party.");
            return;
        }

        UUID owner = dm.getOwner(player.getUniqueId());
        String membersRaw = dm.getMembers(owner);

        player.sendMessage("§6§lParty Info");
        player.sendMessage("§eLíder: §f" + Bukkit.getPlayer(owner).getName());
        player.sendMessage("§eMembros:");

        Arrays.stream(membersRaw.split(",")).forEach(uuidStr -> {
            Player p = Bukkit.getPlayer(UUID.fromString(uuidStr));
            if (p != null) {
                player.sendMessage(" §7- §f" + p.getName());
            }
        });
    }
    private void sendHelp(Player player) {
        player.sendMessage("§6§lParty Commands:");
        player.sendMessage("§e/party create §7- Criar uma party");
        player.sendMessage("§e/party invite <player> §7- Convidar um jogador");
        player.sendMessage("§e/party accept <player> §7- Aceitar convite");
        player.sendMessage("§e/party deny <player> §7- Recusar convite");
        player.sendMessage("§e/party remove <player> §7- Remover jogador da party");
        player.sendMessage("§e/party leave §7- Sair da party");
        player.sendMessage("§e/party disband §7- Dissolver a party");
        player.sendMessage("§e/party info §7- Ver informações da party");
    }

}
