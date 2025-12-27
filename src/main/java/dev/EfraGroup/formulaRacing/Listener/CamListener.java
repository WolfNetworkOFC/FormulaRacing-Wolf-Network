package dev.EfraGroup.formulaRacing.Listener;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.CamUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;

public class CamListener implements Listener {

    private final FormulaRacing plugin;
    private final CamUtils camUtils;

    public CamListener(FormulaRacing plugin, CamUtils camUtils) {
        this.plugin = plugin;
        this.camUtils = camUtils;

        // Task que atualiza todos os seguidores normais
        new BukkitRunnable() {
            @Override
            public void run() {
                camUtils.updateFollowersNormal();
            }
        }.runTaskTimer(plugin, 0L, 5L); // atualiza a cada 20 ticks (1 segundo)
    }

    /**
     * Método que você deve chamar quando quiser teleportar o jogador para a câmera mais próxima.
     * Isso acontece apenas uma vez, quando o jogador estiver mais próximo de uma câmera que da anterior.
     */
    public void updateFollowerNormal(Player follower) {
        if (!camUtils.isFollowingNormal(follower)) return;

        Player target = camUtils.getTargetNormal(follower);
        if (target == null || !target.isOnline()) return;

        camUtils.updateFollowersNormal(); // Esse método do CamUtils já verifica a câmera mais próxima e teleporta apenas uma vez
    }

    /**
     * Caso queira atualizar todos os jogadores online de uma vez (opcional)
     */
    public void updateAllFollowersNormal() {
        for (Player follower : Bukkit.getOnlinePlayers()) {
            updateFollowerNormal(follower);
        }
    }
}
