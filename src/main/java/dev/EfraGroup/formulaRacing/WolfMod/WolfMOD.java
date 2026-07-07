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
     * Registers communication channels in Bukkit's Messenger.
     */
    private void registerChannels() {
        // Channel to send data from Server -> Mod
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);

        // Channel to receive data from Mod -> Server (for future use)
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, (channel, player, message) -> {
            // Logic to process data from Wolfmod (e.g., version confirmation)
        });
    }

    /**
     * Versatile method to send structured payloads to Wolfmod.
     * * @param player The player who will receive the data.
     * @param key    The packet identifier (e.g., "4", "telemetry", "ers").
     * @param values Additional values (String, Integer, Boolean, Float, Double, Long).
     */
    public void sendPayload(Player player, String key, Object... values) {
        if (player == null || !player.isOnline()) return;

        ByteArrayDataOutput out = ByteStreams.newDataOutput();

        // 1. Write the key (packet ID)
        out.writeUTF(key);

        // 2. Write values following the passed order and types
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

        // 3. Dispatch the packet through the official channel
        player.sendPluginMessage(plugin, CHANNEL, out.toByteArray());
    }

    /**
     * Shortcut to trigger the Fastest Lap animation.
     */
    public void sendFastestLap(Player player, String playerName, String lapTime) {
        sendPayload(player, "4", playerName, lapTime);
    }

    /**
     * Shortcut to update the Timer state (Start/Stop).
     */
    public void setTimerState(Player player, boolean running) {
        String key = running ? "2" : "3";
        sendPayload(player, key, "");
    }
}