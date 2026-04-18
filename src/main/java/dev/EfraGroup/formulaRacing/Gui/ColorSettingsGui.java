package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Gui.Framework.BaseGui;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiButton;
import dev.EfraGroup.formulaRacing.Utils.Theme.FRThemeResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

public class ColorSettingsGui extends BaseGui {

    // ── Curated palette — all colours are within the FRTheme luminance range ──
    private static final Object[][] PALETTE = {
        { "#ff5757", "gui_color_name_crimson",  Material.RED_DYE          },
        { "#ff9b3a", "gui_color_name_orange",   Material.ORANGE_DYE       },
        { "#ffc93a", "gui_color_name_amber",    Material.YELLOW_DYE       },
        { "#7bf200", "gui_color_name_lime",     Material.LIME_DYE         },
        { "#00cc66", "gui_color_name_emerald",  Material.GREEN_DYE        },
        { "#00cc99", "gui_color_name_teal",     Material.CYAN_DYE         },
        { "#6cc3ff", "gui_color_name_sky",      Material.LIGHT_BLUE_DYE   },
        { "#3a8dff", "gui_color_name_azure",    Material.BLUE_DYE         },
        { "#a97bff", "gui_color_name_violet",   Material.PURPLE_DYE       },
        { "#ff5cc8", "gui_color_name_magenta",  Material.MAGENTA_DYE      },
        { "#ffa6c9", "gui_color_name_pink",     Material.PINK_DYE         },
        { "#f0f0f0", "gui_color_name_snow",     Material.WHITE_DYE        },
    };

    // ── Slot layout (6 rows = 54 slots) ──────────────────────────────────────
    // Row 0  ( 0-8 ): border + primary header at slot 4
    // Row 1  ( 9-20): primary colours, slots 9-20
    // Row 2  (21-26): primary colours, slots 21-26; border slots 27-35
    // Row 3  (27-35): accent header at slot 31, rest border
    // Row 4  (36-47): accent colours, slots 36-47
    // Row 5  (48-53): accent colours, slots 48-51; back arrow slot 49 (centre)
    //
    // In practice we place:
    //   primary  → slots 9..20 (12 slots for 12 colours)
    //   accent   → slots 36..47 (12 slots for 12 colours)
    //   header-primary  → slot 4   (paper)
    //   header-accent   → slot 31  (paper)
    //   back            → slot 49
    //   border fills everything else

    private static final int[] PRIMARY_SLOTS = {  9,10,11,12,13,14,15,16,17,18,19,20 };
    private static final int[] ACCENT_SLOTS  = { 36,37,38,39,40,41,42,43,44,45,46,47 };
    private static final int HEADER_PRIMARY_SLOT = 4;
    private static final int HEADER_ACCENT_SLOT  = 31;
    private static final int BACK_SLOT           = 49;

    public ColorSettingsGui(FormulaRacing plugin, Player player) {
        super(getTitle(plugin, player), 6);
        setupContent(plugin, player);
    }

    private static String getTitle(FormulaRacing plugin, Player player) {
        String lang = plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId());
        return plugin.getTranslation("gui_color_title", lang, new String[0]);
    }

    // ── Public refresh entry-point (called after in-place colour change) ──────
    public void refresh(FormulaRacing plugin, Player player) {
        getInventory().clear();
        buttons.clear();
        setupContent(plugin, player);
    }

    private void setupContent(FormulaRacing plugin, Player player) {
        String lang         = plugin.getDatabaseManager().getPlayerLanguage(player.getUniqueId());
        String currentPrimary = plugin.getDatabaseManager().getPlayerColor1(player.getUniqueId());
        String currentAccent  = plugin.getDatabaseManager().getPlayerColor2(player.getUniqueId());

        placeBorder();

        // ── Headers ────────────────────────────────────────────────────────
        placeHeader(HEADER_PRIMARY_SLOT,
                plugin.getTranslation("gui_color_header_primary", lang, new String[0]));
        placeHeader(HEADER_ACCENT_SLOT,
                plugin.getTranslation("gui_color_header_accent",  lang, new String[0]));

        // ── Palette: primary ───────────────────────────────────────────────
        for (int i = 0; i < PALETTE.length; i++) {
            String hex      = (String)   PALETTE[i][0];
            String nameKey  = (String)   PALETTE[i][1];
            Material mat    = (Material) PALETTE[i][2];
            String name     = plugin.getTranslation(nameKey, lang, new String[0]);
            boolean selected = hex.equalsIgnoreCase(currentPrimary);

            final String selectedHex = hex;
            setItem(buildColorButton(hex, name, mat, selected,
                    plugin.getTranslation(selected ? "gui_color_lore_current" : "gui_color_lore_select",
                            lang, new String[0]),
                    event -> {
                        Player p = (Player) event.getWhoClicked();
                        if (hex.equalsIgnoreCase(
                                plugin.getDatabaseManager().getPlayerColor1(p.getUniqueId()))) return;
                        plugin.getDatabaseManager().setPlayerColor1(p.getUniqueId(), selectedHex);
                        FRThemeResolver.invalidate(p.getUniqueId());
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.2f);
                        String msg = plugin.getTranslation("gui_color_changed_primary", lang,
                                new String[]{"{color}", selectedHex});
                        p.sendMessage(msg);
                        refresh(plugin, p);
                    }),
                    PRIMARY_SLOTS[i]);
        }

        // ── Palette: accent ────────────────────────────────────────────────
        for (int i = 0; i < PALETTE.length; i++) {
            String hex      = (String)   PALETTE[i][0];
            String nameKey  = (String)   PALETTE[i][1];
            Material mat    = (Material) PALETTE[i][2];
            String name     = plugin.getTranslation(nameKey, lang, new String[0]);
            boolean selected = hex.equalsIgnoreCase(currentAccent);

            final String selectedHex = hex;
            setItem(buildColorButton(hex, name, mat, selected,
                    plugin.getTranslation(selected ? "gui_color_lore_current" : "gui_color_lore_select",
                            lang, new String[0]),
                    event -> {
                        Player p = (Player) event.getWhoClicked();
                        if (hex.equalsIgnoreCase(
                                plugin.getDatabaseManager().getPlayerColor2(p.getUniqueId()))) return;
                        plugin.getDatabaseManager().setPlayerColor2(p.getUniqueId(), selectedHex);
                        FRThemeResolver.invalidate(p.getUniqueId());
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.2f);
                        String msg = plugin.getTranslation("gui_color_changed_accent", lang,
                                new String[]{"{color}", selectedHex});
                        p.sendMessage(msg);
                        refresh(plugin, p);
                    }),
                    ACCENT_SLOTS[i]);
        }

        // ── Back button ────────────────────────────────────────────────────
        ItemStack back = buildSimpleItem(Material.ARROW,
                Component.text(plugin.getTranslation("gui_color_back_name", lang, new String[0]))
                         .decoration(TextDecoration.ITALIC, false),
                List.of(Component.text(plugin.getTranslation("gui_color_back_lore", lang, new String[0]))
                                 .color(TextColor.color(0x555555))
                                 .decoration(TextDecoration.ITALIC, false)));
        setItem(new GuiButton(back, event -> {
            Player p = (Player) event.getWhoClicked();
            new SettingsMenu(plugin, p).show(p);
        }), BACK_SLOT);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private GuiButton buildColorButton(String hex, String name, Material mat,
                                       boolean selected, String loreLine,
                                       java.util.function.Consumer<org.bukkit.event.inventory.InventoryClickEvent> action) {
        TextColor tc = TextColor.fromHexString(hex);
        Component displayName = Component.text(name)
                .color(tc)
                .decoration(TextDecoration.ITALIC, false)
                .decoration(TextDecoration.BOLD, selected);

        Component loreComp = Component.text(loreLine)
                .color(selected ? TextColor.color(0x55ff55) : TextColor.color(0xaaaaaa))
                .decoration(TextDecoration.ITALIC, false);
        Component hexLine = Component.text(hex.toUpperCase())
                .color(TextColor.color(0x555555))
                .decoration(TextDecoration.ITALIC, false);

        ItemStack item = buildSimpleItem(mat, displayName, Arrays.asList(loreComp, hexLine));

        if (selected) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }

        return new GuiButton(item, action);
    }

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.builder()
            .character('\u00A7')
            .hexColors()
            .build();

    private ItemStack buildSimpleItem(Material mat, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LEGACY_SERIALIZER.serialize(name));
            meta.setLore(lore.stream().map(LEGACY_SERIALIZER::serialize).toList());
            item.setItemMeta(meta);
        }
        return item;
    }

    private void placeHeader(int slot, String text) {
        ItemStack item = buildSimpleItem(Material.PAPER,
                Component.text(text)
                         .color(TextColor.color(0xf0f0f0))
                         .decoration(TextDecoration.BOLD, true)
                         .decoration(TextDecoration.ITALIC, false),
                List.of());
        setItem(new GuiButton(item, e -> {}), slot);
    }

    private void placeBorder() {
        ItemStack pane = buildSimpleItem(Material.BLACK_STAINED_GLASS_PANE,
                Component.empty(), List.of());
        GuiButton border = new GuiButton(pane, e -> {});

        boolean[] occupied = new boolean[54];
        for (int s : PRIMARY_SLOTS) occupied[s] = true;
        for (int s : ACCENT_SLOTS)  occupied[s] = true;
        occupied[HEADER_PRIMARY_SLOT] = true;
        occupied[HEADER_ACCENT_SLOT]  = true;
        occupied[BACK_SLOT]           = true;

        for (int i = 0; i < 54; i++) {
            if (!occupied[i]) setItem(border, i);
        }
    }
}
