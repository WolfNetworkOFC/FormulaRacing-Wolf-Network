//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.CamUtils;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

public class CamListener implements Listener {
    private final FormulaRacing plugin;
    private final CamUtils camUtils;

    public CamListener(FormulaRacing plugin, final CamUtils camUtils) {
        this.plugin = plugin;
        this.camUtils = camUtils;
        SchedulerHelper.runTaskTimer(plugin, () -> camUtils.updateFollowersNormal(), 0L, 5L);
    }

    public void updateFollowerNormal(Player follower) {
        if (this.camUtils.isFollowingNormal(follower)) {
            Player target = this.camUtils.getTargetNormal(follower);
            if (target != null && target.isOnline()) {
                this.camUtils.updateFollowersNormal();
            }
        }
    }

    public void updateAllFollowersNormal() {
        for(Player follower : Bukkit.getOnlinePlayers()) {
            this.updateFollowerNormal(follower);
        }

    }
}
