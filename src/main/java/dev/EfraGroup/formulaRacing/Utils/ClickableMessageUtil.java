 /*
  * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
  *
  * Could not load the following classes:
  *  net.md_5.bungee.api.ChatColor
  *  net.md_5.bungee.api.chat.BaseComponent
  *  net.md_5.bungee.api.chat.ClickEvent
  *  net.md_5.bungee.api.chat.ClickEvent$Action
  *  net.md_5.bungee.api.chat.HoverEvent
  *  net.md_5.bungee.api.chat.HoverEvent$Action
  *  net.md_5.bungee.api.chat.TextComponent
  *  net.md_5.bungee.api.chat.hover.content.Content
  *  net.md_5.bungee.api.chat.hover.content.Text
  *  org.bukkit.Bukkit
  *  org.bukkit.entity.Player
  */
 package dev.EfraGroup.formulaRacing.Utils;

 import dev.EfraGroup.formulaRacing.FormulaRacing;
 import dev.EfraGroup.formulaRacing.Utils.TranslationUtil;
 import net.md_5.bungee.api.ChatColor;
 import net.md_5.bungee.api.chat.BaseComponent;
 import net.md_5.bungee.api.chat.ClickEvent;
 import net.md_5.bungee.api.chat.HoverEvent;
 import net.md_5.bungee.api.chat.TextComponent;
 import net.md_5.bungee.api.chat.hover.content.Content;
 import net.md_5.bungee.api.chat.hover.content.Text;
 import org.bukkit.Bukkit;
 import org.bukkit.entity.Player;

 public class ClickableMessageUtil {
     public static void broadcastEventCreated(String eventName, String trackName, int laps, int pits) {
         TextComponent line1 = new TextComponent("\u2554\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2557");
         line1.setColor(ChatColor.GREEN);
         TextComponent line2 = new TextComponent("\u2551 ");
         line2.setColor(ChatColor.GREEN);
         TextComponent eventTitle = new TextComponent("\ud83c\udfc1 Novo Evento Criado!");
         eventTitle.setColor(ChatColor.GOLD);
         eventTitle.setBold(Boolean.valueOf(true));
         line2.addExtra((BaseComponent)eventTitle);
         TextComponent line3 = new TextComponent("\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2563");
         line3.setColor(ChatColor.GREEN);
         TextComponent line4 = new TextComponent("\u2551 ");
         line4.setColor(ChatColor.GREEN);
         TextComponent eventLabel = new TextComponent("Evento: ");
         eventLabel.setColor(ChatColor.WHITE);
         TextComponent eventNameText = new TextComponent(eventName);
         eventNameText.setColor(ChatColor.YELLOW);
         eventNameText.setBold(Boolean.valueOf(true));
         line4.addExtra((BaseComponent)eventLabel);
         line4.addExtra((BaseComponent)eventNameText);
         TextComponent line5 = new TextComponent("\u2551 ");
         line5.setColor(ChatColor.GREEN);
         TextComponent trackLabel = new TextComponent("Pista: ");
         trackLabel.setColor(ChatColor.WHITE);
         TextComponent trackNameText = new TextComponent(trackName);
         trackNameText.setColor(ChatColor.YELLOW);
         line5.addExtra((BaseComponent)trackLabel);
         line5.addExtra((BaseComponent)trackNameText);
         TextComponent line6 = new TextComponent("\u2551 ");
         line6.setColor(ChatColor.GREEN);
         TextComponent detailsText = new TextComponent(String.format("Voltas: %d | Pits: %d", laps, pits));
         detailsText.setColor(ChatColor.GRAY);
         line6.addExtra((BaseComponent)detailsText);
         TextComponent line7 = new TextComponent("\u2560\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2563");
         line7.setColor(ChatColor.GREEN);
         TextComponent line8 = new TextComponent("\u2551 ");
         line8.setColor(ChatColor.GREEN);
         TextComponent clickButton = new TextComponent("[ \u25ba CLIQUE PARA ENTRAR ]");
         clickButton.setColor(ChatColor.AQUA);
         clickButton.setBold(Boolean.valueOf(true));
         clickButton.setUnderlined(Boolean.valueOf(true));
         clickButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/race join " + eventName));
         clickButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Content[]{new Text("\u00a7aClique para entrar no evento!\n\u00a77Comando: \u00a7f/race join " + eventName)}));
         line8.addExtra((BaseComponent)clickButton);
         TextComponent line9 = new TextComponent("\u255a\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u255d");
         line9.setColor(ChatColor.GREEN);
         for (Player player : Bukkit.getOnlinePlayers()) {
             player.spigot().sendMessage((BaseComponent)line1);
             player.spigot().sendMessage((BaseComponent)line2);
             player.spigot().sendMessage((BaseComponent)line3);
             player.spigot().sendMessage((BaseComponent)line4);
             player.spigot().sendMessage((BaseComponent)line5);
             player.spigot().sendMessage((BaseComponent)line6);
             player.spigot().sendMessage((BaseComponent)line7);
             player.spigot().sendMessage((BaseComponent)line8);
             player.spigot().sendMessage((BaseComponent)line9);
         }
     }

     public static void sendClickableLine(Player player, String prefix, String clickable, String suffix, String command, String hover, boolean suggest) {
         TextComponent message = new TextComponent("");
         if (prefix != null && !prefix.isEmpty()) {
             for (BaseComponent bc : TextComponent.fromLegacyText((String)prefix)) {
                 message.addExtra(bc);
             }
         }
         TextComponent clickPart = new TextComponent(clickable);
         clickPart.setColor(ChatColor.AQUA);
         clickPart.setUnderlined(Boolean.valueOf(true));
         clickPart.setClickEvent(new ClickEvent(suggest ? ClickEvent.Action.SUGGEST_COMMAND : ClickEvent.Action.RUN_COMMAND, command));
         if (hover != null && !hover.isEmpty()) {
             clickPart.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Content[]{new Text(hover)}));
         }
         message.addExtra((BaseComponent)clickPart);
         if (suffix != null && !suffix.isEmpty()) {
             for (BaseComponent bc : TextComponent.fromLegacyText((String)suffix)) {
                 message.addExtra(bc);
             }
         }
         player.spigot().sendMessage((BaseComponent)message);
     }

     public static void sendEventSignBroadcast(Player player, String text, String hoverText, String eventName) {
         player.sendMessage("");
         TextComponent line = new TextComponent(TextComponent.fromLegacyText((String)text));
         line.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/event sign " + eventName));
         if (hoverText != null && !hoverText.isEmpty()) {
             line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Content[]{new Text(hoverText)}));
         }
         player.spigot().sendMessage((BaseComponent)line);
         player.sendMessage("");
     }

     public static void sendQuickRaceJoinBroadcast(Player player, String text, String hoverText) {
         player.sendMessage("");
         TextComponent line = new TextComponent(TextComponent.fromLegacyText((String)text));
         line.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/race join"));
         if (hoverText != null && !hoverText.isEmpty()) {
             line.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Content[]{new Text(hoverText)}));
         }
         player.spigot().sendMessage((BaseComponent)line);
         player.sendMessage("");
     }

     public static void sendClickableMessage(Player player, String message, String command, String hoverText) {
         TextComponent component = new TextComponent(message);
         component.setColor(ChatColor.AQUA);
         component.setBold(Boolean.valueOf(true));
         component.setUnderlined(Boolean.valueOf(true));
         component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
         component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Content[]{new Text(hoverText)}));
         player.spigot().sendMessage((BaseComponent)component);
     }

     public static void broadcastEventStartingSoon(String eventName, int secondsRemaining) {
         TextComponent message = new TextComponent("\u23f0 Evento ");
         message.setColor(ChatColor.YELLOW);
         TextComponent eventNameComponent = new TextComponent(eventName);
         eventNameComponent.setColor(ChatColor.GOLD);
         eventNameComponent.setBold(Boolean.valueOf(true));
         message.addExtra((BaseComponent)eventNameComponent);
         TextComponent timing = new TextComponent(" inicia em " + secondsRemaining + " segundos! ");
         timing.setColor(ChatColor.YELLOW);
         message.addExtra((BaseComponent)timing);
         TextComponent clickText = new TextComponent("[Entrar Agora]");
         clickText.setColor(ChatColor.GREEN);
         clickText.setBold(Boolean.valueOf(true));
         clickText.setUnderlined(Boolean.valueOf(true));
         clickText.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/race join " + eventName));
         clickText.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Content[]{new Text("\u00a7a\u00daltima chance para entrar!")}));
         message.addExtra((BaseComponent)clickText);
         for (Player player : Bukkit.getOnlinePlayers()) {
             player.spigot().sendMessage((BaseComponent)message);
         }
     }

     public static void sendQuickRaceInvite(Player player, String trackName, int laps, int pits, int currentDrivers, int maxDrivers) {
          TranslationUtil t = FormulaRacing.getInstance().getTranslationUtil();
         String clickText = t.getTranslated(player, "quickrace_click_to_join", "{track}", trackName, "{laps}", String.valueOf(laps), "{pits}", String.valueOf(pits), "{current}", String.valueOf(currentDrivers), "{max}", String.valueOf(maxDrivers));
         String hoverText = t.getTranslated(player, "quickrace_click_to_join_hover", "{track}", trackName, "{laps}", String.valueOf(laps), "{pits}", String.valueOf(pits), "{current}", String.valueOf(currentDrivers), "{max}", String.valueOf(maxDrivers));
         sendQuickRaceJoinBroadcast(player, clickText, hoverText);
     }

     public static void broadcastEventStarted(String eventName) {
         TextComponent message = new TextComponent("\ud83c\udfc1 Evento ");
         message.setColor(ChatColor.GREEN);
         TextComponent eventNameComponent = new TextComponent(eventName);
         eventNameComponent.setColor(ChatColor.GOLD);
         eventNameComponent.setBold(Boolean.valueOf(true));
         message.addExtra((BaseComponent)eventNameComponent);
         TextComponent started = new TextComponent(" iniciou! Use ");
         started.setColor(ChatColor.GREEN);
         message.addExtra((BaseComponent)started);
         TextComponent spectateCmd = new TextComponent("/spectate join " + eventName);
         spectateCmd.setColor(ChatColor.AQUA);
         spectateCmd.setUnderlined(Boolean.valueOf(true));
         spectateCmd.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/spectate join " + eventName));
         spectateCmd.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Content[]{new Text("\u00a7aClique para assistir como espectador!")}));
         message.addExtra((BaseComponent)spectateCmd);
         TextComponent toWatch = new TextComponent(" para assistir!");
         toWatch.setColor(ChatColor.GREEN);
         message.addExtra((BaseComponent)toWatch);
         for (Player player : Bukkit.getOnlinePlayers()) {
             player.spigot().sendMessage((BaseComponent)message);
         }
     }

     public static void sendClickableUrl(Player player, String message, String url, String hoverText) {
         TextComponent component = new TextComponent(message);
         component.setColor(ChatColor.AQUA);
         component.setBold(Boolean.valueOf(true));
         component.setUnderlined(Boolean.valueOf(true));
         component.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
         component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Content[]{new Text(hoverText)}));
         player.spigot().sendMessage((BaseComponent)component);
     }

     public static TextComponent getRefreshButton(String command, String hoverText) {
         TextComponent btn = new TextComponent("[\u21bb]");
         btn.setColor(ChatColor.AQUA);
         btn.setBold(Boolean.valueOf(true));
         btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
         btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Content[]{new Text(hoverText)}));
         return btn;
     }

     public static TextComponent getButton(String text, ChatColor color, String command, String hoverText, ClickEvent.Action action) {
         TextComponent btn = new TextComponent("[" + text + "]");
         btn.setColor(color);
         btn.setClickEvent(new ClickEvent(action, command));
         if (hoverText != null) {
             btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Content[]{new Text(hoverText)}));
         }
         return btn;
     }

     public static TextComponent getEditButton(String currentValue, String suggestCommand, String hoverText) {
         TextComponent btn = new TextComponent(currentValue);
         btn.setColor(ChatColor.YELLOW);
         btn.setUnderlined(Boolean.valueOf(true));
         btn.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, suggestCommand));
         btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Content[]{new Text(hoverText)}));
         return btn;
     }

     public static TextComponent getToggleButton(String statusText, ChatColor color, String runCommand, String hoverText) {
         TextComponent btn = new TextComponent("[" + statusText + "]");
         btn.setColor(color);
         btn.setBold(Boolean.valueOf(true));
         btn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, runCommand));
         btn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Content[]{new Text(hoverText)}));
         return btn;
     }
 }
