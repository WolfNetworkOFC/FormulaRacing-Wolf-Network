package dev.EfraGroup.formulaRacing.BoatUtils;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class NocolManager {
    // IDs de pacotes baseados na documentação do OpenBoatUtils
    private static final short PACKET_ID_NOCOL = 27;
    private static final short PACKET_VALUE_VANILLA = 0;
    private static final short PACKET_VALUE_NO_COLLISION = 4;
    private static final String CHANNEL = "openboatutils:settings";

    /**
     * Define o modo de colisão para um jogador e seu veículo.
     */
    public static void setCollisionMode(Player player, boolean shouldCollide) {
        if (player == null || !playerHasMod(player)) {
            return;
        }

        // Envia para o jogador
        sendNocolPacket(player, shouldCollide);

        // Se estiver em um barco, envia para o veículo (afeta todos os passageiros com o mod)
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof Boat) {
            sendNocolPacket(vehicle, shouldCollide);
        }
    }

    /**
     * Envia o pacote de colisão diretamente para um Player.
     */
    private static void sendNocolPacket(Player player, boolean shouldCollide) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(byteStream)) {

            short value = shouldCollide ? PACKET_VALUE_VANILLA : PACKET_VALUE_NO_COLLISION;

            out.writeShort(PACKET_ID_NOCOL);
            out.writeShort(value);

            player.sendPluginMessage(FormulaRacing.getInstance(), CHANNEL, byteStream.toByteArray());

            FormulaRacing.getInstance().getDebugManager().logBoatUtils(
                    String.format("[NocolManager] Pacote enviado para %s | Colisao=%b | Valor=%d",
                            player.getName(), shouldCollide, value)
            );

        } catch (IOException e) {
            FormulaRacing.getInstance().getDebugManager().logBoatUtils(
                    "Erro ao enviar pacote NoCol para " + player.getName() + ": " + e.getMessage()
            );
        }
    }

    /**
     * Itera sobre os passageiros de um veículo e envia o pacote para os que possuem o mod.
     */
    private static void sendNocolPacket(Entity vehicle, boolean shouldCollide) {
        if (!(vehicle instanceof Boat)) return;

        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player p && playerHasMod(p)) {
                sendNocolPacket(p, shouldCollide);
            }
        }
    }

    public static boolean playerHasMod(Player player) {
        return player != null && FormulaRacing.hasOpenBoatUtilsMod(player);
    }

    /**
     * Registra o canal de saída no servidor. Deve ser chamado no onEnable.
     */
    public static void registerChannel() {
        FormulaRacing.getInstance().getServer().getMessenger()
                .registerOutgoingPluginChannel(FormulaRacing.getInstance(), CHANNEL);
    }
}