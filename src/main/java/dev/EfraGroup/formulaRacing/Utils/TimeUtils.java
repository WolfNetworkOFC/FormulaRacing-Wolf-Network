package dev.EfraGroup.formulaRacing.Utils;

import org.bukkit.entity.Player;

public class TimeUtils {

    /**
     * Altera o horário do dia de um jogador específico
     *
     * @param player Jogador que terá o horário alterado
     * @param time   Horário em ticks (0 a 24000)
     */
    public static void setPlayerTime(Player player, long time) {
        player.setPlayerTime(time, false); // false = hora fixa só para ele
    }

    /**
     * Reseta o horário do jogador para o tempo do mundo
     *
     * @param player Jogador que terá o tempo resetado
     */
    public static void resetPlayerTime(Player player) {
        player.resetPlayerTime();
    }
}
