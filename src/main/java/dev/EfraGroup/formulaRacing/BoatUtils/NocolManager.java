package dev.EfraGroup.formulaRacing.BoatUtils;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class NocolManager {
    private static final short PACKET_ID_NOCOL = 27;
    private static final short PACKET_ID_COLLISION_FILTER = 31;

    // Packet 27 values (matching Frosthex TimingSystem)
    // 0 = Vanilla (full collision), 1 = NoCol boats/players,
    // 2 = NoCol anything, 3 = Filtered, 4 = Filtered+
    private static final short PACKET_VALUE_VANILLA = 0;
    private static final short NOCOL_MODE_NO_COLLISION_ANY = 2;
    private static final short NOCOL_MODE_FILTERED_COLLISION = 3;

    // Legacy alias for backward compatibility
    private static final short PACKET_VALUE_NO_COLLISION = NOCOL_MODE_NO_COLLISION_ANY;

    private static final String CHANNEL = "openboatutils:settings";

    // Minimum version for packet 31 (filtered collision): 16 = 0.5.1
    private static final int MIN_VERSION_FILTERED_COLLISION = 16;

    public static void setCollisionMode(Player player, boolean shouldCollide) {
        if (player == null || !playerHasMod(player)) {
            return;
        }
        sendNocolShortPacket(player, shouldCollide ? PACKET_VALUE_VANILLA : NOCOL_MODE_NO_COLLISION_ANY);
        SchedulerHelper.runTaskFor(FormulaRacing.getInstance(), player, () -> {
            Entity vehicle = player.getVehicle();
            if (vehicle instanceof Boat) {
                sendNocolPacketForVehicle(vehicle, shouldCollide ? PACKET_VALUE_VANILLA : NOCOL_MODE_NO_COLLISION_ANY);
            }
        });
    }

    /**
     * LOW collision mode: filtered collision via packet 31 + packet 27 = 3.
     * Boats collide with blocks and other boats, but NOT with players/wandering traders/villagers.
     * Requires OpenBoatUtils version >= 16 (0.5.1). Falls back to vanilla (value 0) if client is too old.
     */
    public static void setLowCollisionMode(Player player) {
        if (player == null) return;

        if (!playerCanUseFilteredCollision(player)) {
            // Client too old — fall back to vanilla (full collision)
            FormulaRacing.getInstance().getDebugManager().logBoatUtils(
                    "[NocolManager] Client " + player.getName() + " too old for filtered collision, falling back to vanilla");
            setCollisionMode(player, true);
            return;
        }

        // Send packet 31 with entity types to filter
        sendShortAndStringPacket(player, PACKET_ID_COLLISION_FILTER,
                "minecraft:player,minecraft:villager,minecraft:wandering_trader");
        // Send packet 27 = 3 (filtered collision)
        sendNocolShortPacket(player, NOCOL_MODE_FILTERED_COLLISION);

        FormulaRacing.getInstance().getDebugManager().logBoatUtils(
                "[NocolManager] Applied LOW (filtered) collision mode for " + player.getName());
    }

    /**
     * Sends packet 27 with the given value to a single player.
     */
    private static void sendNocolShortPacket(Player player, short value) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(byteStream)) {

            out.writeShort(PACKET_ID_NOCOL);
            out.writeShort(value);

            player.sendPluginMessage(FormulaRacing.getInstance(), CHANNEL, byteStream.toByteArray());

            FormulaRacing.getInstance().getDebugManager().logBoatUtils(
                    String.format("[NocolManager] Packet 27 sent to %s | Value=%d",
                            player.getName(), value)
            );

        } catch (IOException e) {
            FormulaRacing.getInstance().getDebugManager().logBoatUtils(
                    "Error sending packet 27 to " + player.getName() + ": " + e.getMessage()
            );
        }
    }

    /**
     * Sends a packet with short ID + VarInt-prefixed string (used for packet 31 collision filter).
     */
    private static void sendShortAndStringPacket(Player player, short packetId, String value) {
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(byteStream)) {

            out.writeShort(packetId);
            writeString(out, value);

            player.sendPluginMessage(FormulaRacing.getInstance(), CHANNEL, byteStream.toByteArray());

            FormulaRacing.getInstance().getDebugManager().logBoatUtils(
                    String.format("[NocolManager] Packet %d sent to %s | Value='%s'",
                            packetId, player.getName(), value)
            );

        } catch (IOException e) {
            FormulaRacing.getInstance().getDebugManager().logBoatUtils(
                    "Error sending packet " + packetId + " to " + player.getName() + ": " + e.getMessage()
            );
        }
    }

    /**
     * Writes a VarInt-prefixed UTF-8 string (matching Frosthex's writeString format).
     */
    private static void writeString(DataOutputStream out, String stringValue) throws IOException {
        byte[] bytes = stringValue.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int length = bytes.length;
        final int SEGMENT_BITS = 0x7F;
        final int CONTINUE_BIT = 0x80;

        while (true) {
            if ((length & ~SEGMENT_BITS) == 0) {
                out.writeByte(length);
                break;
            }
            out.writeByte((length & SEGMENT_BITS) | CONTINUE_BIT);
            length >>>= 7;
        }

        out.write(bytes);
    }

    /**
     * Sends packet 27 to all passengers of a boat vehicle.
     */
    private static void sendNocolPacketForVehicle(Entity vehicle, short value) {
        if (!(vehicle instanceof Boat)) return;

        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player p && playerHasMod(p)) {
                sendNocolShortPacket(p, value);
            }
        }
    }

    public static boolean playerHasMod(Player player) {
        return player != null && FormulaRacing.hasOpenBoatUtilsMod(player);
    }

    /**
     * Checks if the player's OpenBoatUtils version supports packet 31 (filtered collision).
     * Requires version >= 16 (0.5.1), matching Frosthex.
     */
    public static boolean playerCanUseFilteredCollision(Player player) {
        if (player == null) return false;
        if (!playerHasMod(player)) return false;
        return OpenBoatUtilsVersion.hasMinVersion(player.getUniqueId(), MIN_VERSION_FILTERED_COLLISION);
    }

    public static void registerChannel() {
        FormulaRacing.getInstance().getServer().getMessenger()
                .registerOutgoingPluginChannel(FormulaRacing.getInstance(), CHANNEL);
    }
}