package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Gui.Framework.BaseGui;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiButton;
import dev.EfraGroup.formulaRacing.Heat.HeatConfig;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Admin GUI for configuring a heat (/heat gui). Toggles apply on click;
 * numeric settings use left-click (+) and right-click (-). The GUI reopens
 * after every change to refresh the displayed values.
 */
public class HeatConfigGui extends BaseGui {

    private final Player player;
    private final Heats heat;

    public HeatConfigGui(FormulaRacing plugin, Player player, Heats heat) {
        super("§8Heat §f" + heat.getName() + " §8— Config", 6);
        this.player = player;
        this.heat = heat;
        this.setupContent();
    }

    private void setupContent() {
        HeatConfig cfg = this.heat.getHeatConfig();

        // Header: heat info
        this.setItem(4, this.createItem(Material.BOOK, "§e" + this.heat.getName(),
                Arrays.asList(
                        "§7Estado: §f" + this.heat.getHeatState(),
                        "§7Pilotos: §f" + this.heat.getDriverCount(),
                        "§7Pista: §f" + (this.heat.getTrackNameWS() != null ? this.heat.getTrackNameWS() : "—")
                )), null);

        // --- Row 1: mode toggles ---
        this.addToggle(10, Material.CLOCK, "Endurance (tempo)", cfg.isTimeBased(),
                v -> cfg.setTimeBased(v),
                "§7Corrida por tempo: quando acaba,",
                "§7todos fazem a volta final.",
                "§7Limite: o §fTimelimit§7 da linha de baixo.");
        this.addToggle(11, Material.LAVA_BUCKET, "Combustível", cfg.isFuelSystemEnabled(),
                v -> cfg.setFuelSystemEnabled(v),
                "§7Consumo de combustível na corrida.",
                "§7Carga: §f" + cfg.getStartingFuel() + "%§7, consumo/s: §f" + cfg.getFuelConsumptionPerSecond());
        this.addToggle(12, Material.TARGET, "Checkered Flag", cfg.isEnableCheckeredFlagFlow(),
                v -> cfg.setEnableCheckeredFlagFlow(v),
                "§7Fluxo de bandeira quadriculada.");
        this.addToggle(13, Material.REDSTONE, "React Start", cfg.isF1StartEnabled(),
                v -> cfg.setF1StartEnabled(v),
                "§7Largada por reação: hold aleatório",
                "§7após a 5ª luz + punição de jump start.",
                "§7Punição: §f" + cfg.getF1StartPenaltySeconds() + "s");
        this.addToggle(14, Material.ICE, "Realista", this.heat.getrealistc(),
                v -> this.heat.setrealistc(v),
                "§7Desgaste de pneu e física realista.");
        this.addToggle(15, Material.SUGAR, "DRS", this.heat.isDrsEnabled(),
                v -> this.heat.setDrsEnabled(v),
                "§7Sistema de DRS na corrida.");
        this.addToggle(16, Material.PISTON, "Push to Pass", this.heat.isPushtopass(),
                v -> this.heat.setPushtopass(v),
                "§7Impulso manual (sneak no barco).");

        // --- Row 2: more toggles ---
        this.addToggle(19, Material.GLOWSTONE_DUST, "ERS", this.heat.isErsEnabled(),
                v -> this.heat.setErsEnabled(v),
                "§7Sistema de energia ERS.");
        this.addToggle(20, Material.MINECART, "Driver Swap", this.heat.getDriverSwap(),
                v -> this.heat.setDriverSwap(v),
                "§7Troca de piloto no pit.");
        this.addToggle(21, Material.COMPASS, "Grid Reverso", this.heat.getreversegrid(),
                v -> this.heat.setreversegrid(v),
                "§7Inverte a ordem do grid na largada.");
        this.addToggle(22, Material.SNOWBALL, "Lonely", this.heat.isLonely(),
                v -> this.heat.setLonely(v),
                "§7Modo solitário (jogadores invisíveis).");

        // --- Row 3: numeric values ---
        this.addAdjustable(28, Material.REPEATER, "Voltas", this.heat.getTotalLaps(),
                1, 100, 1, "§7Voltas da corrida (0 em endurance).",
                v -> this.heat.setTotalLaps(v.intValue()));
        this.addAdjustable(29, Material.ANVIL, "Pits obrigatórios", this.heat.getTotalPits(),
                0, 20, 1, "§7Paradas obrigatórias no box.",
                v -> this.heat.setTotalPits(v.intValue()));
        this.addAdjustable(30, Material.BELL, "Timelimit", orZero(this.heat.getTimeLimit()),
                0, 14400, 30, "§7Limite em segundos (endurance/quali).",
                v -> this.heat.setTimeLimit(v.intValue()));
        this.addAdjustable(31, Material.HOPPER, "Start Delay", orZero(this.heat.getStartDelay()),
                0, 60, 1, "§7Delay de largada em segundos.",
                v -> this.heat.setStartDelay(v.intValue()));
        this.addAdjustable(32, Material.ARMOR_STAND, "Max Pilotos", orZero(this.heat.getMaxDrivers()),
                1, 64, 1, "§7Máximo de pilotos no heat.",
                v -> this.heat.setMaxDrivers(v.intValue()));

        // --- Row 4: powers ---
        this.addAdjustable(37, Material.GOLD_NUGGET, "P2P Power", this.heat.getpushtopasspower(),
                0.01, 2.0, 0.01, "§7Força do Push to Pass.",
                v -> this.heat.setpushtopasspower(v));
        this.addAdjustable(38, Material.GOLD_INGOT, "DRS Power", this.heat.getDrsdownpower(),
                0.01, 2.0, 0.01, "§7Força do DRS.",
                v -> this.heat.setDrsdownpower(v));

        // --- Row 5: ERS settings ---
        this.addAdjustable(40, Material.DIAMOND, "ERS Recharge", cfg.getErsRechargeSpeed(),
                0.01, 2.0, 0.01, "§7Velocidade de recarga do ERS.",
                v -> cfg.setErsRechargeSpeed(v));
        this.addAdjustable(41, Material.REDSTONE, "ERS Drain", cfg.getErsDrainSpeed(),
                0.01, 2.0, 0.01, "§7Velocidade de gasto em Deploy.",
                v -> cfg.setErsDrainSpeed(v));
        this.addAdjustable(42, Material.EMERALD, "ERS Power", cfg.getErsDeployPower(),
                0.01, 0.1, 0.001, "§7Potência do boost ERS.",
                v -> cfg.setErsDeployPower(v));

        // --- Bottom: reset / close ---
        this.setItem(45, this.createItem(Material.TNT, "§cResetar config avançada",
                Arrays.asList("§7Endurance, Fuel, Checkered,", "§7React Start voltam ao padrão.")),
                event -> {
                    this.heat.getHeatConfig().reset();
                    this.click();
                    new HeatConfigGui(this.plugin, this.player, this.heat).show(this.player);
                });
        this.setItem(49, this.createItem(Material.BARRIER, "§cFechar", null),
                event -> this.player.closeInventory());
    }

    private void addToggle(int slot, Material material, String label, boolean current,
                           java.util.function.Consumer<Boolean> setter, String... lore) {
        List<String> loreList = new ArrayList<>(Arrays.asList(lore));
        loreList.add("");
        loreList.add(current ? "§aClique para DESATIVAR" : "§cClique para ATIVAR");
        String name = (current ? "§a" : "§c") + label + (current ? " §2✔" : " §4✘");
        this.setItem(slot, this.createItem(material, name, loreList), event -> {
            setter.accept(!current);
            this.click();
            new HeatConfigGui(this.plugin, this.player, this.heat).show(this.player);
        });
    }

    private interface NumberSetter { void set(Double value); }

    private void addAdjustable(int slot, Material material, String label, double current,
                               double min, double max, double step, String desc, NumberSetter setter) {
        List<String> lore = new ArrayList<>();
        lore.add(desc);
        lore.add("");
        lore.add("§eEsquerdo: §a+§7 | §eDireito: §c-§7 (passo " + formatStep(step) + ")");
        String display = (step >= 1) ? String.valueOf((int) current) : String.format("%.2f", current);
        this.setItem(slot, this.createItem(material, "§b" + label + ": §f" + display, lore), event -> {
            double delta = event.isLeftClick() ? step : event.isRightClick() ? -step : 0;
            if (delta == 0) {
                return;
            }
            double next = Math.max(min, Math.min(max, current + delta));
            setter.set(next);
            this.click();
            new HeatConfigGui(this.plugin, this.player, this.heat).show(this.player);
        });
    }

    private static int orZero(Integer value) {
        return value != null ? value : 0;
    }

    /** setItem with a plain click action (wraps the stack in a GuiButton). */
    private void setItem(int slot, ItemStack stack, java.util.function.Consumer<InventoryClickEvent> action) {
        GuiButton button = new GuiButton(stack, action);
        this.buttons.put(slot, button);
        this.inventory.setItem(slot, stack);
    }

    private static String formatStep(double step) {
        return step >= 1 ? String.valueOf((int) step) : String.valueOf(step);
    }

    private void click() {
        this.player.playSound(this.player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7F, 1.4F);
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
