package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Gui.Framework.BaseGui;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiButton;
import dev.EfraGroup.formulaRacing.Heat.GimmickConfig;
import dev.EfraGroup.formulaRacing.Heat.GimmickException;
import dev.EfraGroup.formulaRacing.Heat.GimmickManager;
import dev.EfraGroup.formulaRacing.Heat.GimmickSchedule;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Options of a single gimmick: pick the lap, schedule it in the selected heat,
 * enable/disable, test paste and delete.
 */
public class GimmickDetailGui extends BaseGui {

    private static final int MAX_LAP = 999;

    private final Player viewer;
    private final GimmickConfig gimmick;
    private final Heats heat;

    /** Lap the schedule buttons will use; starts on the first lap that is still allowed. */
    private int lap;

    public GimmickDetailGui(FormulaRacing plugin, Player viewer, GimmickConfig gimmick, Heats heat) {
        super("Gimmick: " + gimmick.getName(), 3);
        this.viewer = viewer;
        this.gimmick = gimmick;
        this.heat = heat;

        GimmickSchedule schedule = plugin.getGimmickManager().findSchedule(heat, gimmick);
        if (schedule != null) {
            this.lap = schedule.getTriggerLap();
        } else {
            this.lap = Math.max(
                GimmickManager.MIN_TRIGGER_LAP,
                heat == null ? GimmickManager.MIN_TRIGGER_LAP : plugin.getGimmickManager().currentHeatLap(heat) + 1
            );
        }
        this.rebuild();
    }

    private void rebuild() {
        GimmickManager manager = plugin.getGimmickManager();
        this.inventory.clear();
        this.buttons.clear();

        // Header: the gimmick itself.
        GimmickSchedule schedule = manager.findSchedule(heat, gimmick);
        List<String> info = new ArrayList<>();
        info.add("§7Pista: §f" + gimmick.getTrackNameWS());
        info.add(
            "§7Posição: §f" + gimmick.getWorldName() + " " +
                (int) gimmick.getX() + ", " + (int) gimmick.getY() + ", " + (int) gimmick.getZ()
        );
        info.add("§7Colagem: §f" + (gimmick.isPasteWithAir() ? "com o ar (-a)" : "sem o ar"));
        info.add("§7Blocos: §f" + manager.countBlocks(gimmick));
        info.add("§7Na memória: " + (manager.isLoaded(gimmick) ? "§asim" : "§7não (fica no disco)"));
        if (heat == null) {
            info.add("§7Heat: §cnão selecionado (use /heat select)");
        } else {
            info.add("§7Heat: §f" + heat.getName() + " §7(volta atual: §f" + manager.currentHeatLap(heat) + "§7)");
        }
        this.setItem(new GuiButton(this.item(Material.STRUCTURE_BLOCK, "§e" + gimmick.getName(), info)), 4);

        // Row 2: lap chooser.
        this.setItem(new GuiButton(this.item(Material.RED_STAINED_GLASS_PANE, "§c-10", null), e -> move(-10)), 9);
        this.setItem(new GuiButton(this.item(Material.RED_STAINED_GLASS_PANE, "§c-5", null), e -> move(-5)), 10);
        this.setItem(new GuiButton(this.item(Material.RED_STAINED_GLASS_PANE, "§c-1", null), e -> move(-1)), 11);

        List<String> lapLore = new ArrayList<>();
        lapLore.add("§7Volta em que a gimmick será colada.");
        lapLore.add("§7Mínimo: §f" + GimmickManager.MIN_TRIGGER_LAP + " §7(a largada é a volta 1)");
        if (heat != null && heat.getTotalLaps() != null && heat.getTotalLaps() > 0) {
            lapLore.add("§7Máximo: §f" + heat.getTotalLaps());
        }
        if (schedule != null) {
            lapLore.add("");
            lapLore.add("§aAgendada na volta " + schedule.getTriggerLap() +
                (schedule.isTriggered() ? " §7(já colada)" : ""));
        }
        this.setItem(new GuiButton(this.lapItem(lapLore)), 13);

        this.setItem(new GuiButton(this.item(Material.LIME_STAINED_GLASS_PANE, "§a+1", null), e -> move(1)), 15);
        this.setItem(new GuiButton(this.item(Material.LIME_STAINED_GLASS_PANE, "§a+5", null), e -> move(5)), 16);
        this.setItem(new GuiButton(this.item(Material.LIME_STAINED_GLASS_PANE, "§a+10", null), e -> move(10)), 17);

        // Row 3: actions.
        this.setItem(
            new GuiButton(
                this.item(Material.ARROW, "§7Voltar", List.of("§7Volta para a lista de gimmicks.")),
                e -> new GimmickGui(plugin, viewer, gimmick.getTrackNameWS(), heat).show(viewer)
            ),
            18
        );

        if (schedule != null) {
            this.setItem(
                new GuiButton(
                    this.item(
                        Material.RED_DYE,
                        "§cRemover do heat",
                        List.of("§7Remove o agendamento da volta §f" + schedule.getTriggerLap() + "§7.")
                    ),
                    e -> {
                        manager.unscheduleGimmick(heat, gimmick);
                        this.message("§e⚠ Gimmick '" + gimmick.getName() + "' removida do agendamento do heat.");
                        this.rebuild();
                    }
                ),
                20
            );
        } else {
            this.setItem(
                new GuiButton(
                    this.item(
                        Material.LIME_DYE,
                        "§a§lAgendar na volta " + lap,
                        List.of(
                            "§7Cola essa gimmick na volta §f" + lap + "§7 do heat.",
                            heat == null ? "§cNenhum heat selecionado." : "§7Heat: §f" + heat.getName(),
                            "",
                            "§eClique para agendar"
                        )
                    ),
                    e -> this.schedule(manager, lap)
                ),
                20
            );
        }

        this.setItem(
            new GuiButton(
                this.item(
                    Material.LEVER,
                    (gimmick.isEnabled() ? "§aAtiva" : "§cDesativada"),
                    List.of("§7Clique para " + (gimmick.isEnabled() ? "desativar" : "ativar") + "§7.")
                ),
                e -> {
                    boolean enabled = manager.toggleGimmick(gimmick);
                    this.message(enabled ? "§a✓ Gimmick ativada." : "§e⚠ Gimmick desativada.");
                    this.rebuild();
                }
            ),
            22
        );

        this.setItem(
            new GuiButton(
                this.item(
                    Material.STRUCTURE_VOID,
                    "§bColar agora",
                    List.of(
                        "§7Cola a gimmick imediatamente,",
                        "§7para você testar a posição.",
                        "",
                        "§7Desfaça com §f/gimmick restore"
                    )
                ),
                e -> {
                    try {
                        manager.pasteNow(gimmick);
                        this.message("§a✓ Colando '" + gimmick.getName() + "'...");
                    } catch (GimmickException ex) {
                        this.message("§c✗ " + ex.getMessage());
                    }
                    viewer.closeInventory();
                }
            ),
            24
        );

        this.setItem(
            new GuiButton(
                this.item(
                    Material.BARRIER,
                    "§4§lApagar gimmick",
                    List.of(
                        "§7Apaga a definição, o arquivo",
                        "§7e os agendamentos dela.",
                        "",
                        "§cShift + clique para confirmar"
                    )
                ),
                e -> {
                    if (!e.isShiftClick()) {
                        this.message("§cSegure Shift e clique para apagar.");
                        return;
                    }
                    manager.deleteGimmick(gimmick);
                    viewer.closeInventory();
                    viewer.sendMessage("§a✓ Gimmick '" + gimmick.getName() + "' apagada.");
                }
            ),
            26
        );
    }

    private void move(int delta) {
        int totalLaps = heat != null && heat.getTotalLaps() != null ? heat.getTotalLaps() : 0;
        int max = totalLaps > 0 ? totalLaps : MAX_LAP;

        int before = lap;
        lap = Math.min(max, Math.max(GimmickManager.MIN_TRIGGER_LAP, lap + delta));
        if (lap == before) {
            this.message(
                "§cLimite: de " + GimmickManager.MIN_TRIGGER_LAP + " até " + max + "."
            );
            return;
        }
        this.rebuild();
    }

    private void schedule(GimmickManager manager, int lap) {
        try {
            manager.scheduleGimmick(heat, gimmick, lap);
            this.message("§a✓ Gimmick '" + gimmick.getName() + "' agendada para a volta " + lap + ".");
        } catch (GimmickException e) {
            this.message("§c✗ " + e.getMessage());
        }
        this.rebuild();
    }

    private ItemStack lapItem(List<String> lore) {
        ItemStack stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lVolta: §f§l" + lap);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        stack.setAmount(Math.min(64, Math.max(1, lap)));
        return stack;
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

    private void message(String text) {
        viewer.sendMessage(text);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.4F);
    }
}
