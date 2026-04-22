package dev.EfraGroup.formulaRacing.WolfMod;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.entity.Player;

public class WolfMOD {

    private final FormulaRacing plugin;
    public static final String CHANNEL = "wolfmod:packet";

    public WolfMOD(FormulaRacing plugin) {
        this.plugin = plugin;
        registerChannels();
    }

    /**
     * Registra os canais de comunicação no Messenger do Bukkit.
     */
    private void registerChannels() {
        // Canal para enviar dados do Servidor -> Mod
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);

        // Canal para receber dados do Mod -> Servidor (se necessário futuramente)
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, (channel, player, message) -> {
            // Lógica para processar dados vindos do Wolfmod (ex: confirmação de versão)
        });
    }

    /**
     * Método versátil para enviar payloads estruturados para o Wolfmod.
     * * @param player O jogador que receberá os dados.
     * @param key    A identificação do pacote (ex: "4", "telemetry", "ers").
     * @param values Valores adicionais (String, Integer, Boolean, Float, Double, Long).
     */
    public void sendPayload(Player player, String key, Object... values) {
        if (player == null || !player.isOnline()) return;

        ByteArrayDataOutput out = ByteStreams.newDataOutput();

        // 1. Escreve a chave (ID do pacote)
        out.writeUTF(key);

        // 2. Escreve os valores seguindo a ordem e tipo passados
        for (Object value : values) {
            if (value instanceof String) {
                out.writeUTF((String) value);
            } else if (value instanceof Integer) {
                out.writeInt((Integer) value);
            } else if (value instanceof Boolean) {
                out.writeBoolean((Boolean) value);
            } else if (value instanceof Float) {
                out.writeFloat((Float) value);
            } else if (value instanceof Double) {
                out.writeDouble((Double) value);
            } else if (value instanceof Long) {
                out.writeLong((Long) value);
            }
        }

        // 3. Despacha o pacote pelo canal oficial
        player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }

    /**
     * Atalho específico para disparar a animação de Fastest Lap.
     */
    public void sendFastestLap(Player player, String playerName, String lapTime) {
        sendPayload(player, "4", playerName, lapTime);
    }

    /**
     * Atalho para atualizar o estado do Cronômetro (Start/Stop).
     */
    public void setTimerState(Player player, boolean running) {
        String key = running ? "2" : "3";
        sendPayload(player, key, "");
    }
}