//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Gui.Framework;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class GuiManager {
    private final Map<UUID, BaseGui> openGuis = new HashMap();

    public GuiManager() {
    }

    public void setOpenGui(Player player, BaseGui gui) {
        this.openGuis.put(player.getUniqueId(), gui);
    }

    public BaseGui getOpenGui(Player player) {
        return this.openGuis.get(player.getUniqueId());
    }

    public void removeOpenGui(Player player) {
        this.openGuis.remove(player.getUniqueId());
    }

    public void closeAll() {
        for (UUID uuid : new HashSet<>(this.openGuis.keySet())) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.closeInventory();
            }
        }

        this.openGuis.clear();
    }
}
