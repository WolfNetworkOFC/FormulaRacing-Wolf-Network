package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Gui.Framework.BaseGui;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiButton;
import dev.EfraGroup.formulaRacing.Heat.GimmickConfig;
import dev.EfraGroup.formulaRacing.Heat.GimmickManager;
import dev.EfraGroup.formulaRacing.Heat.GimmickSchedule;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Gimmick library of a track, with the schedule state of the selected heat.
 * Clicking a gimmick opens {@link GimmickDetailGui}.
 */
public class GimmickGui extends BaseGui {

    private static final int LIST_SLOTS = 45;

    private final Player viewer;
    private final String trackName;
    private final Heats heat;

    public GimmickGui(FormulaRacing plugin, Player viewer, String trackName, Heats heat) {
        super(
            "Gimmicks - " + trackName + (heat != null ? " (" + heat.getName() + ")" : ""),
            6
        );
        this.viewer = viewer;
        this.trackName = trackName;
        this.heat = heat;
        this.rebuild();
    }

    /** Rebuilds the contents in place, so a refresh keeps the same open inventory. */
    public void rebuild() {
        this.inventory.clear();
        this.buttons.clear();

        GimmickManager manager = plugin.getGimmickManager();
        List<GimmickConfig> gimmicks = manager.getGimmicksForTrack(trackName);

        if (gimmicks.isEmpty()) {
            this.setItem(
                new GuiButton(
                    this.item(
                        Material.STRUCTURE_VOID,
                        "§cNenhuma gimmick salva nessa pista",
                        List.of(
                            "§7Use §f//copy §7na área e depois:",
                            "§f/gimmick save <nome> " + trackName + " [-a]"
                        )
                    )
                ),
                22
            );
        } else {
            int slot = 0;
            for (GimmickConfig gimmick : gimmicks) {
                if (slot >= LIST_SLOTS) break;
                this.setItem(
                    new GuiButton(
                        this.describe(manager, gimmick),
                        event -> new GimmickDetailGui(plugin, viewer, gimmick, heat).show(viewer)
                    ),
                    slot++
                );
            }
        }

        this.buildFooter(manager, gimmicks.size());
    }

    private void buildFooter(GimmickManager manager, int total) {
        this.setItem(
            new GuiButton(
                this.item(
                    Material.HOPPER,
                    "§eAtualizar",
                    List.of("§7Recarrega a lista de gimmicks.")
                ),
                event -> this.rebuild()
            ),
            45
        );

        this.setItem(
            new GuiButton(
                this.item(
                    Material.REDSTONE,
                    "§cColadas agora: §f" + manager.countPasted(),
                    List.of(
                        "§7Gimmicks que estão aplicadas no mundo.",
                        "§7Ao terminar o heat elas são desfeitas.",
                        "§7Total de gimmicks na pista: §f" + total
                    )
                )
            ),
            47
        );

        this.setItem(
            new GuiButton(
                this.item(
                    Material.LAVA_BUCKET,
                    "§c§lRestaurar tudo",
                    List.of(
                        "§7Desfaz todas as gimmicks coladas,",
                        "§7inclusive as de uma sessão anterior",
                        "§7que ficou pela metade.",
                        "",
                        "§eClique para restaurar"
                    )
                ),
                event -> {
                    int restored = manager.restoreEverything();
                    viewer.closeInventory();
                    viewer.sendMessage(
                        "§a✓ " + restored + " gimmick(s) desfeita(s)."
                    );
                }
            ),
            49
        );

        this.setItem(
            new GuiButton(
                this.item(Material.BARRIER, "§cFechar", List.of("§7Fecha o menu.")),
                event -> viewer.closeInventory()
            ),
            53
        );
    }

    private ItemStack describe(GimmickManager manager, GimmickConfig gimmick) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Pista: §f" + gimmick.getTrackNameWS());
        lore.add(
            "§7Posição: §f" + gimmick.getWorldName() + " " +
                (int) gimmick.getX() + ", " + (int) gimmick.getY() + ", " + (int) gimmick.getZ()
        );
        lore.add("§7Colagem: §f" + (gimmick.isPasteWithAir() ? "com o ar (-a)" : "sem o ar"));

        int blocks = manager.countBlocks(gimmick);
        lore.add("§7Blocos: §f" + (blocks < 0 ? "arquivo ausente" : blocks));
        lore.add("§7Status: " + (gimmick.isEnabled() ? "§aATIVA" : "§cDESATIVADA"));

        GimmickSchedule schedule = manager.findSchedule(heat, gimmick);
        if (schedule != null) {
            lore.add(
                "§7Agendada: §e" + schedule.getTriggerLap() + "ª volta" +
                    (schedule.isTriggered() ? " §7(já colada)" : "")
            );
        } else {
            lore.add("§7Agendada: §7não");
        }

        lore.add("§7Na memória: " + (manager.isLoaded(gimmick) ? "§asim" : "§7não (fica no disco)"));

        if (gimmick.getAnnounceMessage() != null && !gimmick.getAnnounceMessage().isBlank()) {
            lore.add("§7Msg: §f" + org.bukkit.ChatColor.translateAlternateColorCodes('&', gimmick.getAnnounceMessage()));
        }

        lore.add("");
        lore.add("§eClique para abrir as opções");

        return this.item(
            Material.STRUCTURE_BLOCK,
            (gimmick.isEnabled() ? "§a" : "§c") + gimmick.getName(),
            lore
        );
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
