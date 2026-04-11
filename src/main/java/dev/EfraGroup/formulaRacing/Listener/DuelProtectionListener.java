//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent.Action;
import net.md_5.bungee.api.chat.hover.content.Content;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

public class DuelProtectionListener implements Listener {
    private final FormulaRacing plugin;
    private final DatabaseManager databaseManager;
    private static final List<String> BLOCKED_COMMANDS = Arrays.asList("/spawn", "/home", "/tp", "/teleport", "/tpa", "/tpaccept", "/back", "/warp", "/hub", "/lobby", "/suicide", "/kill", "/fly", "/gm", "/gamemode", "/speed", "/lonely");

    public DuelProtectionListener(FormulaRacing plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
    }

    @EventHandler(
            priority = EventPriority.HIGHEST
    )
    public void onCommandDuringDuel(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        boolean isStaff = player.hasPermission("formularacing.admin");
        UUID uuid = player.getUniqueId();
        String command = event.getMessage().toLowerCase().split(" ")[0];
        if (!command.equals("/duel") && !command.equals("/race") && !command.equals("/heat") && !command.equals("/event") && !command.equals("/fr") && !command.equals("/formularacing")) {
            boolean inDuel = this.plugin.getTimeTrialDuels() != null && this.plugin.getTimeTrialDuels().isPlayerActivelyInDuel(uuid);
            boolean inQuickRace = this.plugin.getQuickRaceManager() != null && this.plugin.getQuickRaceManager().isPlayerActivelyRacing(uuid);
            boolean inHeat = false;
            if (this.plugin.getRaceEventManager() != null) {
                Optional<Heats> activeHeat = this.plugin.getRaceEventManager().getPlayerActiveHeat(uuid);
                if (activeHeat.isPresent()) {
                    inHeat = ((Heats)activeHeat.get()).isPlayerActivelyRacing(uuid);
                }
            }

            if (inDuel || inQuickRace || inHeat) {
                for(String blocked : BLOCKED_COMMANDS) {
                    if (command.equals(blocked)) {
                        if (isStaff && !command.equals("/lonely")) {
                            return;
                        }

                        event.setCancelled(true);
                        String langCode = this.databaseManager.getPlayerLanguage(uuid);
                        String type = inDuel ? "duel" : "race";
                        String leaveCmd = inDuel ? "/duel leave" : "/race leave";
                        player.sendMessage(" ");
                        String var10001 = String.valueOf(ChatColor.RED);
                        player.sendMessage(var10001 + "⚠ " + this.plugin.getDirectTranslation(type + "_command_blocked", langCode));
                        String clickText = this.plugin.getTranslation("protection_click_to_leave", langCode, new String[0]);
                        String suffixText = this.plugin.getTranslation("protection_leave_suffix", langCode, new String[0]);
                        String hoverText = this.plugin.getTranslation("protection_hover_leave", langCode, new String[]{"{command}", leaveCmd});
                        TextComponent msg = new TextComponent(clickText);
                        msg.addExtra(suffixText);
                        msg.setClickEvent(new ClickEvent(Action.RUN_COMMAND, leaveCmd));
                        msg.setHoverEvent(new HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new Content[]{new Text(hoverText)}));
                        player.spigot().sendMessage(msg);
                        player.sendMessage(" ");
                        return;
                    }
                }

            }
        }
    }
}
