package dev.EfraGroup.formulaRacing.TabCompleter;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PartyTabCompleter implements TabCompleter {

    private final DatabaseManager partyDAO;

    public PartyTabCompleter(DatabaseManager partyDAO) {
        this.partyDAO = partyDAO;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {

        if (!(sender instanceof Player player)) return List.of();

        try {
            // /party <subcommand>
            if (args.length == 1) {
                return filter(args[0],
                        "create",
                        "invite",
                        "remove",
                        "leave",
                        "disband",
                        "info"
                );
            }

            // /party add <player>
            if (args.length == 2 && args[0].equalsIgnoreCase("invite")) {
                return getOnlinePlayersNotInParty(player, args[1]);
            }

            // /party remove <player>
            if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
                return getPartyMembers(player, args[1]);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }

        return List.of();
    }

    // =====================
    // HELPERS
    // =====================

    private List<String> getOnlinePlayersNotInParty(Player sender, String input) throws SQLException {
        List<String> list = new ArrayList<>();

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(sender)) continue;
            if (!partyDAO.hasParty(p.getUniqueId())) {
                if (p.getName().toLowerCase().startsWith(input.toLowerCase())) {
                    list.add(p.getName());
                }
            }
        }
        return list;
    }

    private List<String> getPartyMembers(Player sender, String input) throws SQLException {
        List<String> list = new ArrayList<>();

        if (!partyDAO.hasParty(sender.getUniqueId())) return list;

        UUID owner = partyDAO.getOwner(sender.getUniqueId());
        if (!owner.equals(sender.getUniqueId())) return list;

        String membersRaw = partyDAO.getMembers(owner);
        if (membersRaw == null) return list;

        for (String uuidStr : membersRaw.split(",")) {
            UUID uuid = UUID.fromString(uuidStr);
            Player p = Bukkit.getPlayer(uuid);

            if (p != null && !p.equals(sender)) {
                if (p.getName().toLowerCase().startsWith(input.toLowerCase())) {
                    list.add(p.getName());
                }
            }
        }
        return list;
    }

    private List<String> filter(String input, String... options) {
        List<String> list = new ArrayList<>();
        for (String opt : options) {
            if (opt.startsWith(input.toLowerCase())) {
                list.add(opt);
            }
        }
        return list;
    }
}
