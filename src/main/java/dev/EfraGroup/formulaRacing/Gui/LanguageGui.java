//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Gui.Framework.BaseGui;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiButton;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public class LanguageGui extends BaseGui {
    private final DatabaseManager db;
    private final Player player;
    private static final Map<UUID, Long> clickCooldown = new HashMap();
    private static Object headAPI;
    private static Method getItemHeadMethod;
    private static boolean headApiInitialized = false;
    private static final Map<String, LanguageInfo> LANGUAGES = new LinkedHashMap();

    private static void initHeadDatabase() {
        if (!headApiInitialized) {
            Plugin hdPlugin = Bukkit.getPluginManager().getPlugin("HeadDatabase");
            if (hdPlugin != null) {
                try {
                    Class<?> apiClass = Class.forName("me.arcaniax.hdb.api.HeadDatabaseAPI");
                    headAPI = apiClass.getDeclaredConstructor().newInstance();
                    getItemHeadMethod = apiClass.getMethod("getItemHead", String.class);
                    FormulaRacing.getInstance().getDebugManager().logGuiSystem("HeadDatabase API integrada com sucesso!");
                } catch (Exception e) {
                    FormulaRacing.getInstance().getDebugManager().logGuiSystem("Falha ao carregar HeadDatabase API via reflexão: " + e.getMessage());
                    headAPI = null;
                }
            } else {
                headAPI = null;
            }

            headApiInitialized = true;
        }
    }

    public LanguageGui(FormulaRacing plugin, Player player) {
        super(plugin.getTranslation("lang_menu_title", plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId()), new String[0]), 3);
        this.db = plugin.getDatabaseManager();
        this.player = player;
        this.setupContent();
    }

    private void setupContent() {
        String currentLang = this.db.getPlayerLanguage(this.player.getUniqueId());
        File langDir = new File(this.plugin.getDataFolder(), "lang");
        if (langDir.exists() && langDir.isDirectory()) {
            File[] langFiles = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (langFiles != null && langFiles.length != 0) {
                for(File langFile : langFiles) {
                    String langCode = langFile.getName().replace(".yml", "");
                    LanguageInfo langInfo = (LanguageInfo)LANGUAGES.getOrDefault(langCode, new LanguageInfo(langCode, langCode, "0"));
                    boolean isCurrent = langCode.equals(currentLang);
                    ItemStack item = this.createLanguageItem(langInfo, isCurrent, currentLang);
                    GuiButton button = new GuiButton(item, (event) -> {
                        long now = System.currentTimeMillis();
                        long last = (Long)clickCooldown.getOrDefault(this.player.getUniqueId(), 0L);
                        if (now - last < 500L) {
                            this.player.sendMessage(this.plugin.getTranslation("wait_before_click", currentLang, new String[0]));
                        } else {
                            clickCooldown.put(this.player.getUniqueId(), now);
                            this.db.setPlayerLanguage(this.player.getUniqueId(), langCode);
                            this.plugin.getTranslationUtil().updatePlayerLanguage(this.player.getUniqueId(), langCode);
                            this.player.closeInventory();
                            File lFile = new File(this.plugin.getDataFolder(), "lang/" + langCode + ".yml");
                            YamlConfiguration langConfig = YamlConfiguration.loadConfiguration(lFile);
                            String confirmMsg = langConfig.getString("lang_set", "§aSeu idioma foi alterado para:");
                            confirmMsg = ChatColor.translateAlternateColorCodes('&', confirmMsg);
                            this.player.sendMessage(confirmMsg + " " + String.valueOf(ChatColor.WHITE) + langInfo.displayName());
                            this.plugin.getHotbarController().giveHotbarItems(this.player);
                        }
                    });
                    this.addItem(button);
                }

                ItemStack infoItem = this.createInfoItem(currentLang);
                GuiButton infoButton = new GuiButton(infoItem, (e) -> {
                });
                this.setItem(infoButton, 22);
            } else {
                this.player.sendMessage(String.valueOf(ChatColor.RED) + "Nenhum idioma disponível!");
            }
        } else {
            this.player.sendMessage(String.valueOf(ChatColor.RED) + "Erro: Pasta de idiomas não encontrada!");
        }
    }

    private ItemStack createLanguageItem(LanguageInfo langInfo, boolean isCurrent, String currentLang) {
        ItemStack item;
        if (headAPI != null && getItemHeadMethod != null && !langInfo.headId().equals("0")) {
            try {
                item = (ItemStack)getItemHeadMethod.invoke(headAPI, langInfo.headId());
                if (item == null) {
                    item = new ItemStack(Material.BOOK);
                }
            } catch (Exception var9) {
                item = new ItemStack(Material.BOOK);
            }
        } else {
            item = new ItemStack(Material.BOOK);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String var10000 = String.valueOf(ChatColor.AQUA);
            String displayName = var10000 + String.valueOf(ChatColor.BOLD) + langInfo.displayName();
            if (isCurrent) {
                displayName = displayName + String.valueOf(ChatColor.GREEN) + " ✓";
            }

            meta.setDisplayName(displayName);
            List<String> lore = new ArrayList();
            if (isCurrent) {
                String currentText = this.plugin.getTranslation("lang_menu_current", currentLang, new String[0]);
                String var10001 = String.valueOf(ChatColor.GREEN);
                lore.add(var10001 + "▪ " + currentText);
            } else {
                String clickText = this.plugin.getTranslation("lang_menu_click", currentLang, new String[0]);
                String var11 = String.valueOf(ChatColor.YELLOW);
                lore.add(var11 + "▪ " + clickText);
            }

            lore.add("");
            String var12 = String.valueOf(ChatColor.DARK_GRAY);
            lore.add(var12 + "Language: " + String.valueOf(ChatColor.GRAY) + langInfo.code());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createInfoItem(String currentLang) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String title = this.plugin.getTranslation("lang_menu_info_title", currentLang, new String[0]);
            String var10001 = String.valueOf(ChatColor.YELLOW);
            meta.setDisplayName(var10001 + title);
            List<String> lore = new ArrayList();
            String line1 = this.plugin.getTranslation("lang_menu_info_line1", currentLang, new String[0]);
            String line2 = this.plugin.getTranslation("lang_menu_info_line2", currentLang, new String[0]);
            var10001 = String.valueOf(ChatColor.GRAY);
            lore.add(var10001 + line1);
            var10001 = String.valueOf(ChatColor.GRAY);
            lore.add(var10001 + line2);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    static {
        LANGUAGES.put("en_US", new LanguageInfo("en_US", "English (United States)", "27589"));
        LANGUAGES.put("pt_BR", new LanguageInfo("pt_BR", "Português (Brasil)", "27585"));
        LANGUAGES.put("pt_PT", new LanguageInfo("pt_PT", "Português (Portugal)", "22022"));
        initHeadDatabase();
    }

    private static record LanguageInfo(String code, String displayName, String headId) {
    }
}
