package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public class LanguageGui implements Listener {

    private final DatabaseManager db;
    private final FormulaRacing plugin;
    private final Map<UUID, Long> clickCooldown = new HashMap<>();

    // Mapa de idiomas disponíveis com suas informações
    private static final Map<String, LanguageInfo> LANGUAGES = new LinkedHashMap<>();

    static {
        // Usando blocos coloridos para representar idiomas
        // Funciona perfeitamente em servidores offline mode (Minecraft pirata)

        // Estados Unidos - Livro Azul (🇺🇸 cores azul e branco)
        LANGUAGES.put("en_US", new LanguageInfo("en_US", "English (United States)", Material.BLUE_BANNER));

        // Brasil - Livro Verde e Amarelo (🇧🇷 cores da bandeira)
        LANGUAGES.put("pt_BR", new LanguageInfo("pt_BR", "Português (Brasil)", Material.LIME_BANNER));

        // Portugal - Livro Verde e Vermelho (🇵🇹 cores da bandeira)
        LANGUAGES.put("pt_PT", new LanguageInfo("pt_PT", "Português (Portugal)", Material.RED_BANNER));
    }

    public LanguageGui(DatabaseManager db, FormulaRacing plugin) {
        this.db = db;
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Abre o menu de seleção de idioma */
    public void open(Player player) {
        String currentLang = db.getPlayerLanguage(player.getUniqueId());

        // Obtém o título traduzido
        String title = plugin.getTranslation("lang_menu_title", currentLang);

        Inventory inv = Bukkit.createInventory(null, 27, title);

        // Lista de idiomas disponíveis no servidor
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists() || !langDir.isDirectory()) {
            player.sendMessage(ChatColor.RED + "Erro: Pasta de idiomas não encontrada!");
            return;
        }

        File[] langFiles = langDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (langFiles == null || langFiles.length == 0) {
            player.sendMessage(ChatColor.RED + "Nenhum idioma disponível!");
            return;
        }

        // Adiciona os idiomas disponíveis ao inventário
        for (File langFile : langFiles) {
            String langCode = langFile.getName().replace(".yml", "");

            // Usa um livro como ícone padrão para idiomas não mapeados
            LanguageInfo langInfo = LANGUAGES.getOrDefault(langCode,
                new LanguageInfo(langCode, langCode, Material.BOOK));

            // Verifica se é o idioma atual do jogador
            boolean isCurrent = langCode.equals(currentLang);

            ItemStack item = createLanguageItem(langInfo, isCurrent, currentLang);
            inv.addItem(item);
        }

        // Adiciona item de informação no slot 22
        ItemStack infoItem = createInfoItem(currentLang);
        inv.setItem(22, infoItem);

        player.openInventory(inv);
    }

    /** Cria um item de idioma com ícone colorido */
    private ItemStack createLanguageItem(LanguageInfo langInfo, boolean isCurrent, String currentLang) {
        ItemStack item = new ItemStack(langInfo.icon());
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String displayName = "" + ChatColor.AQUA + ChatColor.BOLD + langInfo.displayName();
            if (isCurrent) {
                displayName += ChatColor.GREEN + " ✓";
            }
            meta.setDisplayName(displayName);

            List<String> lore = new ArrayList<>();

            if (isCurrent) {
                String currentText = plugin.getTranslation("lang_menu_current", currentLang);
                lore.add(ChatColor.GREEN + "▪ " + currentText);
            } else {
                String clickText = plugin.getTranslation("lang_menu_click", currentLang);
                lore.add(ChatColor.YELLOW + "▪ " + clickText);
            }

            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Language: " + ChatColor.GRAY + langInfo.code());
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }


    /** Cria o item de informação */
    private ItemStack createInfoItem(String currentLang) {
        ItemStack item = new ItemStack(Material.COMPASS);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            String title = plugin.getTranslation("lang_menu_info_title", currentLang);
            meta.setDisplayName(ChatColor.YELLOW + title);

            List<String> lore = new ArrayList<>();
            String line1 = plugin.getTranslation("lang_menu_info_line1", currentLang);
            String line2 = plugin.getTranslation("lang_menu_info_line2", currentLang);
            lore.add(ChatColor.GRAY + line1);
            lore.add(ChatColor.GRAY + line2);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    /** Verifica se o inventário é o menu de idiomas */
    private boolean isLanguageInventory(Inventory inv, String title) {
        if (inv == null || title == null) return false;

        // Verifica contra todos os possíveis títulos traduzidos
        for (String langCode : LANGUAGES.keySet()) {
            File langFile = new File(plugin.getDataFolder(), "lang/" + langCode + ".yml");
            if (langFile.exists()) {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(langFile);
                String translatedTitle = config.getString("lang_menu_title", "Select Language");
                translatedTitle = ChatColor.translateAlternateColorCodes('&', translatedTitle);

                if (ChatColor.stripColor(title).equalsIgnoreCase(ChatColor.stripColor(translatedTitle))) {
                    return true;
                }
            }
        }

        return false;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        Inventory inv = event.getView().getTopInventory();

        if (!isLanguageInventory(inv, title)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (!clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) return;

        // Ignora cliques no item de informação (compass)
        if (clicked.getType() == Material.COMPASS) return;

        Player player = (Player) event.getWhoClicked();

        // 🕒 Evita spam de cliques
        long now = System.currentTimeMillis();
        long last = clickCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 500) {
            String langCode = db.getPlayerLanguage(player.getUniqueId());
            player.sendMessage(plugin.getTranslation("wait_before_click", langCode));
            return;
        }
        clickCooldown.put(player.getUniqueId(), now);

        // Extrai o código do idioma do lore
        List<String> lore = clicked.getItemMeta().getLore();
        if (lore == null || lore.isEmpty()) return;

        String lastLine = ChatColor.stripColor(lore.get(lore.size() - 1));
        if (!lastLine.startsWith("Language: ")) return;

        String selectedLang = lastLine.replace("Language: ", "").trim();

        // Verifica se o arquivo de idioma existe
        File langFile = new File(plugin.getDataFolder(), "lang/" + selectedLang + ".yml");
        if (!langFile.exists()) {
            player.sendMessage(ChatColor.RED + "Erro ao selecionar idioma!");
            return;
        }

        // Salva no banco de dados
        db.setPlayerLanguage(player.getUniqueId(), selectedLang);
        player.closeInventory();

        // Envia mensagem de confirmação no novo idioma
        YamlConfiguration langConfig = YamlConfiguration.loadConfiguration(langFile);
        String confirmMsg = langConfig.getString("lang_set", "§aSeu idioma foi alterado para:");
        confirmMsg = ChatColor.translateAlternateColorCodes('&', confirmMsg);

        LanguageInfo langInfo = LANGUAGES.getOrDefault(selectedLang,
            new LanguageInfo(selectedLang, selectedLang, Material.BOOK));

        player.sendMessage(confirmMsg + " " + ChatColor.WHITE + langInfo.displayName());
    }

    /** Estrutura para armazenar informações de idiomas */
    private record LanguageInfo(String code, String displayName, Material icon) {}
}

