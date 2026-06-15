package dev.EfraGroup.formulaRacing.Gui;

import dev.EfraGroup.formulaRacing.Gui.Framework.BaseGui;
import dev.EfraGroup.formulaRacing.Gui.Framework.GuiButton;
import dev.EfraGroup.formulaRacing.Heat.Heats;
import dev.EfraGroup.formulaRacing.Participant.Driver;
import dev.EfraGroup.formulaRacing.Utils.SkullUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class ReadyCheckView extends BaseGui {
    private final Heats heat;

    public ReadyCheckView(Heats heat) {
        super(String.valueOf(ChatColor.DARK_GRAY) + "Ready Check - Pilotos #" + heat.getId(), 6);
        this.heat = heat;
    }

    public void update(Set<UUID> readyPlayers) {
        this.inventory.clear();
        this.buttons.clear();
        int slot = 0;

        for(Driver driver : this.heat.getDrivers().values()) {
            if (!readyPlayers.contains(driver.getUuid())) {
                Player p = Bukkit.getPlayer(driver.getUuid());
                if (p != null && p.isOnline()) {
                    ItemStack head = SkullUtils.getPlayerHead(p);
                    ItemMeta meta = head.getItemMeta();
                    if (meta != null) {
                        List<String> lore = new ArrayList();
                        String var10001 = String.valueOf(ChatColor.GRAY);
                        lore.add(var10001 + "Status: " + String.valueOf(ChatColor.RED) + "AGUARDANDO...");
                        lore.add("");
                        lore.add(String.valueOf(ChatColor.YELLOW) + "Aperte SHIFT para ficar pronto!");
                        meta.setLore(lore);
                        head.setItemMeta(meta);
                    }

                    GuiButton button = new GuiButton(head, (event) -> {
                    });
                    this.setItem(button, slot++);
                }

                if (slot >= 54) {
                    break;
                }
            }
        }

    }
}
