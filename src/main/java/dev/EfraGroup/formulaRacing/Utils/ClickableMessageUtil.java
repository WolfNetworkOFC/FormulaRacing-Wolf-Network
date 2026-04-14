package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import net.kyori.adventure.text.format.NamedTextColor;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent.Action;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

 public class ClickableMessageUtil {

     public static void broadcastEventCreated(String eventName, String trackName, int laps, int pits) {
         TextComponent line1 = createTextComponent("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557", NamedTextColor.GREEN, false, false);
         TextComponent line2 = createTextComponent("\u2551 ", NamedTextColor.GREEN, false, false);
         TextComponent eventTitle = createClickableTextComponent("\ud83c\udfc1 Novo Evento Criado!", NamedTextColor.GOLD, true, false, "/race join " + eventName, "\u00a7aClique para entrar no evento!\n\u00a77Comando: \u00a7f/race join " + eventName, true);
         line2.addExtra(eventTitle);
         TextComponent line3 = createTextComponent("\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2563", NamedTextColor.GREEN, false, false);
         TextComponent line4 = createTextComponent("\u2551 ", NamedTextColor.GREEN, false, false);
         line4.addExtra(createTextComponent("Evento: ", NamedTextColor.WHITE, false, false));
         line4.addExtra(createTextComponent(eventName, NamedTextColor.YELLOW, true, false));
         TextComponent line5 = createTextComponent("\u2551 ", NamedTextColor.GREEN, false, false);
         line5.addExtra(createTextComponent("Pista: ", NamedTextColor.WHITE, false, false));
         line5.addExtra(createTextComponent(trackName, NamedTextColor.YELLOW, false, false));
         TextComponent line6 = createTextComponent("\u2551 ", NamedTextColor.GREEN, false, false);
         line6.addExtra(createTextComponent(String.format("Voltas: %d | Pits: %d", laps, pits), NamedTextColor.GRAY, false, false));
         TextComponent line7 = createTextComponent("\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2563", NamedTextColor.GREEN, false, false);
         TextComponent line8 = createTextComponent("\u2551 ", NamedTextColor.GREEN, false, false);
         line8.addExtra(createClickableTextComponent("[ \u25ba CLIQUE PARA ENTRAR ]", NamedTextColor.AQUA, true, true, "/race join " + eventName, "\u00a7aClique para entrar no evento!\n\u00a77Comando: \u00a7f/race join " + eventName, true));
         TextComponent line9 = createTextComponent("\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d", NamedTextColor.GREEN, false, false);
         for (Player player : Bukkit.getOnlinePlayers()) {
             player.spigot().sendMessage(line1);
             player.spigot().sendMessage(line2);
             player.spigot().sendMessage(line3);
             player.spigot().sendMessage(line4);
             player.spigot().sendMessage(line5);
             player.spigot().sendMessage(line6);
             player.spigot().sendMessage(line7);
             player.spigot().sendMessage(line8);
             player.spigot().sendMessage(line9);
         }
     }

     public static void sendClickableLine(Player player, String prefix, String clickable, String suffix, String command, String hover, boolean suggest) {
         TextComponent message = new TextComponent("");
         if (prefix != null && !prefix.isEmpty()) {
             for (BaseComponent bc : TextComponent.fromLegacyText(prefix)) {
                 message.addExtra(bc);
             }
         }
         TextComponent clickPart = createClickableTextComponent(clickable, NamedTextColor.AQUA, false, true, command, hover, suggest);
         message.addExtra(clickPart);
         if (suffix != null && !suffix.isEmpty()) {
             for (BaseComponent bc : TextComponent.fromLegacyText(suffix)) {
                 message.addExtra(bc);
             }
         }
         player.spigot().sendMessage(message);
     }

     public static void sendEventSignBroadcast(Player player, String text, String hoverText, String eventName) {
         player.sendMessage("");
         TextComponent line = new TextComponent(TextComponent.fromLegacyText(text));
         line.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/event sign " + eventName));
         if (hoverText != null && !hoverText.isEmpty()) {
             line.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text(hoverText)));
         }
         player.spigot().sendMessage(line);
         player.sendMessage("");
     }

     public static void sendQuickRaceJoinBroadcast(Player player, String text, String hoverText) {
         player.sendMessage("");
         TextComponent line = new TextComponent(TextComponent.fromLegacyText(text));
         line.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/race join"));
         if (hoverText != null && !hoverText.isEmpty()) {
             line.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text(hoverText)));
         }
         player.spigot().sendMessage(line);
         player.sendMessage("");
     }

     public static void sendClickableMessage(Player player, String message, String command, String hoverText) {
         TextComponent component = createClickableTextComponent(message, NamedTextColor.AQUA, true, true, command, hoverText, true);
         player.spigot().sendMessage(component);
     }

     public static void broadcastEventStartingSoon(String eventName, int secondsRemaining) {
         TextComponent message = new TextComponent("");
         message.addExtra(createTextComponent("\u23f0 Evento ", NamedTextColor.YELLOW, false, false));
         message.addExtra(createTextComponent(eventName, NamedTextColor.GOLD, true, false));
         message.addExtra(createTextComponent(" inicia em " + secondsRemaining + " segundos! ", NamedTextColor.YELLOW, false, false));
         message.addExtra(createClickableTextComponent("[Entrar Agora]", NamedTextColor.GREEN, true, true, "/race join " + eventName, "\u00a7a\u00daltima chance para entrar!", true));
         for (Player player : Bukkit.getOnlinePlayers()) {
             player.spigot().sendMessage(message);
         }
     }

     public static void sendQuickRaceInvite(Player player, String trackName, int laps, int pits, int currentDrivers, int maxDrivers) {
         String clickText = FormulaRacing.getInstance().getTranslationUtil().getTranslated(player, "quickrace_click_to_join", "{track}", trackName, "{laps}", String.valueOf(laps), "{pits}", String.valueOf(pits), "{current}", String.valueOf(currentDrivers), "{max}", String.valueOf(maxDrivers));
         String hoverText = FormulaRacing.getInstance().getTranslationUtil().getTranslated(player, "quickrace_click_to_join_hover", "{track}", trackName, "{laps}", String.valueOf(laps), "{pits}", String.valueOf(pits), "{current}", String.valueOf(currentDrivers), "{max}", String.valueOf(maxDrivers));
         sendQuickRaceJoinBroadcast(player, clickText, hoverText);
     }

     public static void broadcastEventStarted(String eventName) {
         TextComponent message = new TextComponent("");
         message.addExtra(createTextComponent("\ud83c\udfc1 Evento ", NamedTextColor.GREEN, false, false));
         message.addExtra(createTextComponent(eventName, NamedTextColor.GOLD, true, false));
         message.addExtra(createTextComponent(" iniciou! Use ", NamedTextColor.GREEN, false, false));
         message.addExtra(createClickableTextComponent("/spectate join " + eventName, NamedTextColor.AQUA, false, true, "/spectate join " + eventName, "\u00a7aClique para assistir como espectador!", false));
         message.addExtra(createTextComponent(" para assistir!", NamedTextColor.GREEN, false, false));
         for (Player player : Bukkit.getOnlinePlayers()) {
             player.spigot().sendMessage(message);
         }
     }

     public static void sendClickableUrl(Player player, String message, String url, String hoverText) {
         TextComponent component = createClickableUrlTextComponent(message, NamedTextColor.AQUA, true, true, url, hoverText);
         player.spigot().sendMessage(component);
     }

     public static TextComponent getRefreshButton(String command, String hoverText) {
         TextComponent tc = new TextComponent("[\u21bb]");
         tc.setColor(ChatColor.AQUA);
         tc.setBold(true);
         tc.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND, command));
         tc.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text(hoverText)));
         return tc;
     }

     public static TextComponent getButton(String text, ChatColor color, String command, String hoverText, Action action) {
         TextComponent tc = new TextComponent("[" + text + "]");
         tc.setColor(color);
         tc.setBold(true);
         tc.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(actionToBungee(action), command));
         tc.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text(hoverText)));
         return tc;
     }

     public static TextComponent getEditButton(String currentValue, String suggestCommand, String hoverText) {
         TextComponent tc = new TextComponent(currentValue);
         tc.setColor(ChatColor.YELLOW);
         tc.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND, suggestCommand));
         tc.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text(hoverText)));
         return tc;
     }

     public static TextComponent getToggleButton(String statusText, ChatColor color, String runCommand, String hoverText) {
         TextComponent tc = new TextComponent("[" + statusText + "]");
         tc.setColor(color);
         tc.setBold(true);
         tc.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, runCommand));
         tc.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text(hoverText)));
         return tc;
     }

     private static TextComponent createTextComponent(String text, NamedTextColor color, boolean bold, boolean underlined) {
         TextComponent tc = new TextComponent(text);
         tc.setColor(namedTextColorToBungee(color));
         if (bold) tc.setBold(true);
         if (underlined) tc.setUnderlined(true);
         return tc;
     }

     private static TextComponent createClickableTextComponent(String text, NamedTextColor color, boolean bold, boolean underlined, String command, String hoverText, boolean suggest) {
         TextComponent tc = new TextComponent(text);
         tc.setColor(namedTextColorToBungee(color));
         if (bold) tc.setBold(true);
         if (underlined) tc.setUnderlined(true);
         tc.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(suggest ? net.md_5.bungee.api.chat.ClickEvent.Action.SUGGEST_COMMAND : net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, command));
         if (hoverText != null && !hoverText.isEmpty()) {
             tc.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text(hoverText)));
         }
         return tc;
     }

     private static TextComponent createClickableUrlTextComponent(String text, NamedTextColor color, boolean bold, boolean underlined, String url, String hoverText) {
         TextComponent tc = new TextComponent(text);
         tc.setColor(namedTextColorToBungee(color));
         if (bold) tc.setBold(true);
         if (underlined) tc.setUnderlined(true);
         tc.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, url));
         if (hoverText != null && !hoverText.isEmpty()) {
             tc.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT, new net.md_5.bungee.api.chat.hover.content.Text(hoverText)));
         }
         return tc;
     }

      private static ChatColor namedTextColorToBungee(NamedTextColor color) {
          if (color == null) return ChatColor.WHITE;
          if (color == NamedTextColor.BLACK) return ChatColor.BLACK;
          if (color == NamedTextColor.DARK_BLUE) return ChatColor.DARK_BLUE;
          if (color == NamedTextColor.DARK_GREEN) return ChatColor.DARK_GREEN;
          if (color == NamedTextColor.DARK_AQUA) return ChatColor.DARK_AQUA;
          if (color == NamedTextColor.DARK_RED) return ChatColor.DARK_RED;
          if (color == NamedTextColor.DARK_PURPLE) return ChatColor.DARK_PURPLE;
          if (color == NamedTextColor.GOLD) return ChatColor.GOLD;
          if (color == NamedTextColor.GRAY) return ChatColor.GRAY;
          if (color == NamedTextColor.DARK_GRAY) return ChatColor.DARK_GRAY;
          if (color == NamedTextColor.BLUE) return ChatColor.BLUE;
          if (color == NamedTextColor.GREEN) return ChatColor.GREEN;
          if (color == NamedTextColor.AQUA) return ChatColor.AQUA;
          if (color == NamedTextColor.RED) return ChatColor.RED;
          if (color == NamedTextColor.LIGHT_PURPLE) return ChatColor.LIGHT_PURPLE;
          if (color == NamedTextColor.YELLOW) return ChatColor.YELLOW;
          if (color == NamedTextColor.WHITE) return ChatColor.WHITE;
          return ChatColor.WHITE;
      }

      private static net.md_5.bungee.api.chat.ClickEvent.Action actionToBungee(Action action) {
          return action;
      }
 }
