package dev.EfraGroup.formulaRacing.BoatUtils;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import org.bukkit.entity.Boat;
import org.bukkit.entity.ChestBoat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.logging.Level;

public class NocolManager {

    private static final short PACKET_ID_NOCOL = 27;
    private static final short NOCOL_MODE_ON = 2;
    private static final short NOCOL_MODE_OFF = 0;

    /**
     * Ativa ou desativa colisão para jogador e barco.
     * Só funciona se o jogador tiver o mod OpenBoatUtils.
     */
    public static void setCollisionMode(Player player, boolean shouldCollide) {
        if (player == null) return;

        boolean hasMod = playerHasMod(player);
        //FormulaRacing.getInstance().getLogger().info("[NocolManager] Jogador " + player.getName() + " tem mod? " + hasMod);

        if (!hasMod) return;

        // Jogador
        sendNocolPacket(player, shouldCollide);

        // Veículo (se estiver em barco)
        Entity vehicle = player.getVehicle();
        if (vehicle instanceof Boat || vehicle instanceof ChestBoat) {
            sendNocolPacket(vehicle, shouldCollide);
        }
    }

    /**
     * Envia pacote NoCol para jogador
     */
    private static void sendNocolPacket(Player player, boolean shouldCollide) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteStream);

            out.writeShort(PACKET_ID_NOCOL);
            out.writeShort(shouldCollide ? NOCOL_MODE_ON : NOCOL_MODE_OFF);

            byte[] data = byteStream.toByteArray();
            player.sendPluginMessage(FormulaRacing.getInstance(), "openboatutils:settings", data);

            //FormulaRacing.getInstance().getLogger().info("[NocolManager] Pacote enviado para jogador " + player.getName() +
            //        " | shouldCollide=" + shouldCollide);

        } catch (IOException e) {
            FormulaRacing.getInstance().getLogger().log(Level.SEVERE,
                    "Erro ao enviar pacote NoCol para " + player.getName(), e);
        }
    }

    /**
     * Envia pacote NoCol para passageiros de um barco
     */
    private static void sendNocolPacket(Entity vehicle, boolean shouldCollide) {
        if (!(vehicle instanceof Boat || vehicle instanceof ChestBoat)) return;

        vehicle.getPassengers().forEach(passenger -> {
            if (passenger instanceof Player p && playerHasMod(p)) {
                try {
                    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
                    DataOutputStream out = new DataOutputStream(byteStream);

                    out.writeShort(PACKET_ID_NOCOL);
                    out.writeShort(shouldCollide ? NOCOL_MODE_ON : NOCOL_MODE_OFF);

                    byte[] data = byteStream.toByteArray();
                    p.sendPluginMessage(FormulaRacing.getInstance(), "openboatutils:settings", data);

              //      FormulaRacing.getInstance().getLogger().info("[NocolManager] Pacote enviado para passageiro " + p.getName() +
                //            " do veículo | shouldCollide=" + shouldCollide);

                } catch (IOException e) {
                    FormulaRacing.getInstance().getLogger().log(Level.SEVERE,
                            "Erro ao enviar pacote NoCol para o passageiro " + p.getName(), e);
                }
            }
        });
    }

    /**
     * Verifica se o jogador possui o mod OpenBoatUtils
     */
    public static boolean playerHasMod(Player player) {
        return player != null && FormulaRacing.hasOpenBoatUtilsMod(player);
    }

    /**
     * Registrar o canal no onEnable
     */
    public static void registerChannel() {
        FormulaRacing.getInstance().getServer().getMessenger().registerOutgoingPluginChannel(
                FormulaRacing.getInstance(), "openboatutils:settings");
       // FormulaRacing.getInstance().getLogger().info("[NocolManager] Canal 'openboatutils:settings' registrado com sucesso.");
    }
}
