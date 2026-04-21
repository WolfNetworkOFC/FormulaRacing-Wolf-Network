package dev.EfraGroup.formulaRacing;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

public class PacketSender {

    private final DatabaseManager db;

    public PacketSender(DatabaseManager db, FormulaRacing plugin) {
        this.db = db;
    }

    public void sendBoatSetting(Player player, int packetId, Object... values) {
        try {
            ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(byteStream);

            out.writeShort(packetId); // ID do pacote

            for (Object value : values) {
                if (value instanceof Boolean b) out.writeBoolean(b);
                else if (value instanceof Float f) out.writeFloat(f);
                else if (value instanceof Double d) out.writeDouble(d);
                else if (value instanceof Integer i) out.writeInt(i);
                else if (value instanceof Short s) out.writeShort(s);
                else if (value instanceof Byte b) out.writeByte(b);
                else if (value instanceof String str) writeString(out, str);
            }

            player.sendPluginMessage(FormulaRacing.getInstance(), "openboatutils:settings", byteStream.toByteArray());

        } catch (IOException e) {
            Bukkit.getLogger().severe("[FormulaRacing] Falha ao enviar pacote OpenBoatUtils: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void writeString(DataOutputStream out, String stringValue) throws IOException {
        int length = stringValue.length();
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

        out.writeBytes(stringValue);
    }

    public void applyBoatUtilsToPlayer(Player player, String trackNameWS) {
        Map<String, Object> data = db.  getBoatUtilsRaw(trackNameWS);
        if (data == null || data.isEmpty()) {
            return; // não tem boatutils nessa pista
        }

        // VANILLA VALUES
        float VANILLA_STEP_HEIGHT = 0f;
        float VANILLA_DEFAULT_SLIPPERINESS = 0.6f;
        boolean VANILLA_FALL_DAMAGE = true;
        boolean VANILLA_WATER_ELEVATION = true;
        boolean VANILLA_AIR_CONTROL = false;
        float VANILLA_JUMP_FORCE = 0f;
        double VANILLA_GRAVITY = -0.03999999910593033;
        float VANILLA_YAW_ACCEL = 1.0f;
        float VANILLA_FORWARD_ACCEL = 0.04f;
        float VANILLA_BACKWARD_ACCEL = 0.005f;
        float VANILLA_TURN_FORWARD_ACCEL = 0.005f;
        boolean VANILLA_ALLOW_ACCEL_STACKING = true;
        boolean VANILLA_UNDERWATER_CONTROL = true;
        boolean VANILLA_SURFACE_WATER_CONTROL = true;
        int VANILLA_COYOTE_TIME = 0;
        boolean VANILLA_WATER_JUMPING = true;
        float VANILLA_SWIM_FORCE = 0.0f;
        short VANILLA_COLLISION_MODE = 0;
        boolean VANILLA_AIR_STEPPING = false;
        boolean VANILLA_TEN_STEP_INTERPOLATION = false;
        byte VANILLA_COLLISION_RESOLUTION = 5;

        // =============== 1. STEP HEIGHT (1) ===============
        float stepHeight = ((Number)data.get("stepHeight")).floatValue();
        if (stepHeight != VANILLA_STEP_HEIGHT) {
            sendBoatSetting(player, (short)1, stepHeight);
        }

        // =============== 2. DEFAULT SLIPPERINESS (2) ===============
        float defaultSlip = 0.6f;
        try {
            defaultSlip = ((Number) data.get("defaultSlipperiness")).floatValue();
        } catch (Exception e) {
            System.out.println("[DEBUG] Error parsing defaultSlipperiness: " + e.getMessage());
        }
        if (defaultSlip != VANILLA_DEFAULT_SLIPPERINESS) {
            sendBoatSetting(player, (short)2, defaultSlip);
            System.out.println("[DEBUG] Set default slipperiness -> " + defaultSlip);
        }

        String customSlip = (String) data.get("customSlipperiness");
        if (customSlip != null && !customSlip.isEmpty()) {
            Map<Float, List<String>> slipMap = new HashMap<>();
            String[] entries = customSlip.split(",");
            for (String entry : entries) {
                entry = entry.trim();
                if (entry.isEmpty()) continue;

                String[] parts = entry.split(";", 2); // divide block:value
                if (parts.length != 2) {
                    System.out.println("[DEBUG] Skipping invalid entry: " + entry);
                    continue;
                }

                String blockId = parts[0].trim();
                float slipValue;
                try {
                    slipValue = Float.parseFloat(parts[1].trim());
                } catch (NumberFormatException e) {
                    System.out.println("[DEBUG] Invalid float for block " + blockId + ":; " + parts[1]);
                    continue;
                }

                slipMap.computeIfAbsent(slipValue, k -> new ArrayList<>()).add(blockId);
            }

            // envia os pacotes
            for (Map.Entry<Float, List<String>> e : slipMap.entrySet()) {
                float value = e.getKey();
                String blocks = String.join(",", e.getValue());
                sendBoatSetting(player, (short)3, value, blocks);
                System.out.println("[DEBUG] Set custom slipperiness -> Value: " + value + ", Blocks: " + blocks);
            }
        }





        // =============== 4. FALL DAMAGE (4) ===============
        boolean fallDamage = (Boolean) data.get("fallDamage");
        if (fallDamage != VANILLA_FALL_DAMAGE) {
            sendBoatSetting(player, (short)4, fallDamage);
        }

        // =============== 5. WATER ELEVATION (5) ===============
        boolean waterElevation = (Boolean) data.get("waterElevation");
        if (waterElevation != VANILLA_WATER_ELEVATION) {
            sendBoatSetting(player, (short)5, waterElevation);
        }

        // =============== 6. AIR CONTROL (6) ===============
        boolean airControl = (Boolean) data.get("airControl");
        if (airControl != VANILLA_AIR_CONTROL) {
            sendBoatSetting(player, (short)6, airControl);
        }

        // =============== 7. JUMP FORCE (7) ===============
        float jumpForce = ((Number)data.get("jumpForce")).floatValue();
        if (jumpForce != VANILLA_JUMP_FORCE) {
            sendBoatSetting(player, (short)7, jumpForce);
        }

        // =============== 9. GRAVITY (9) ===============
        double gravity = ((Number)data.get("gravity")).doubleValue();
        if (gravity != VANILLA_GRAVITY) {
            sendBoatSetting(player, (short)9, gravity);
        }

        // =============== 10. YAW ACCEL (10) ===============
        float yawAccel = ((Number)data.get("yawAcceleration")).floatValue();
        if (yawAccel != VANILLA_YAW_ACCEL) {
            sendBoatSetting(player, (short)10, yawAccel);
        }

        // =============== 11. FORWARD ACCEL (11) ===============
        float fwdAccel = ((Number)data.get("forwardAcceleration")).floatValue();
        if (fwdAccel != VANILLA_FORWARD_ACCEL) {
            sendBoatSetting(player, (short)11, fwdAccel);
        }

        // =============== 12. BACKWARD ACCEL (12) ===============
        float backAccel = ((Number)data.get("backwardAcceleration")).floatValue();
        if (backAccel != VANILLA_BACKWARD_ACCEL) {
            sendBoatSetting(player, (short)12, backAccel);
        }

        // =============== 13. TURNING FORWARD ACCEL (13) ===============
        float turnFwdAccel = ((Number)data.get("turningForwardAcceleration")).floatValue();
        if (turnFwdAccel != VANILLA_TURN_FORWARD_ACCEL) {
            sendBoatSetting(player, (short)13, turnFwdAccel);
        }

        // =============== 14. ACCEL STACKING (14) ===============
        boolean stacking = (Boolean) data.get("allowAccelerationStacking");
        if (stacking != VANILLA_ALLOW_ACCEL_STACKING) {
            sendBoatSetting(player, (short)14, stacking);
        }

        // =============== 16. UNDERWATER CONTROL (16) ===============
        boolean underwater = (Boolean) data.get("underwaterControl");
        if (underwater != VANILLA_UNDERWATER_CONTROL) {
            sendBoatSetting(player, (short)16, underwater);
        }

        // =============== 17. SURFACE WATER CONTROL (17) ===============
        boolean surfaceWater = (Boolean) data.get("surfaceWaterControl");
        if (surfaceWater != VANILLA_SURFACE_WATER_CONTROL) {
            sendBoatSetting(player, (short)17, surfaceWater);
        }

        // =============== 19. COYOTE TIME (19) ===============
        int coyote = (Integer) data.get("coyoteTime");
        if (coyote != VANILLA_COYOTE_TIME) {
            sendBoatSetting(player, (short)19, coyote);
        }

        // =============== 20. WATER JUMPING (20) ===============
        boolean waterJumping = (Boolean) data.get("waterJumping");
        if (waterJumping != VANILLA_WATER_JUMPING) {
            sendBoatSetting(player, (short)20, waterJumping);
        }

        // =============== 21. SWIM FORCE (21) ===============
        float swimForce = ((Number)data.get("swimForce")).floatValue();
        if (swimForce != VANILLA_SWIM_FORCE) {
            sendBoatSetting(player, (short)21, swimForce);
        }

        // =============== 27. COLLISION MODE (27) ===============
        short collisionMode = ((Number)data.get("collisionMode")).shortValue();
        if (collisionMode != VANILLA_COLLISION_MODE) {
            sendBoatSetting(player, (short)27, collisionMode);
        }

        // =============== 28. AIR STEPPING (28) ===============
        boolean airStepping = (Boolean) data.get("airStepping");
        if (airStepping != VANILLA_AIR_STEPPING) {
            sendBoatSetting(player, (short)28, airStepping);
        }

        // =============== 29. TEN STEP INTERPOLATION (29) ===============
        boolean tenStep = (Boolean) data.get("tenStepInterpolation");
        if (tenStep != VANILLA_TEN_STEP_INTERPOLATION) {
            sendBoatSetting(player, (short)29, tenStep);
        }

        // =============== 30. COLLISION RESOLUTION (30) ===============
        byte resolution = ((Number)data.get("collisionResolution")).byteValue();
        if (resolution != VANILLA_COLLISION_RESOLUTION) {
            sendBoatSetting(player, (short)30, resolution);
        }

        // =============== 26. PER BLOCK SETTINGS (26) ===============
        String perBlock = (String) data.get("perBlockSetting");
        if (perBlock != null && !perBlock.isEmpty()) {
            // formato: setting:value:block1,block2
            String[] pieces = perBlock.split(":", 3);
            short settingId = Short.parseShort(pieces[0]);
            float value = Float.parseFloat(pieces[1]);
            String blocks = pieces.length > 2 ? pieces[2] : "";
            sendBoatSetting(player, (short)26, settingId, value, blocks);
        }
    }

    public void resetBoatUtilsToVanilla(Player player) {
        if (!FormulaRacing.hasOpenBoatUtilsMod(player)) {
            return;
        }

        sendBoatSetting(player, (short) 1, 0f);
        sendBoatSetting(player, (short) 2, 0.6f);
        sendBoatSetting(player, (short) 4, true);
        sendBoatSetting(player, (short) 5, true);
        sendBoatSetting(player, (short) 6, false);
        sendBoatSetting(player, (short) 7, 0f);
        sendBoatSetting(player, (short) 9, -0.03999999910593033d);
        sendBoatSetting(player, (short) 10, 1.0f);
        sendBoatSetting(player, (short) 11, 0.04f);
        sendBoatSetting(player, (short) 12, 0.005f);
        sendBoatSetting(player, (short) 13, 0.005f);
        sendBoatSetting(player, (short) 14, true);
        sendBoatSetting(player, (short) 16, true);
        sendBoatSetting(player, (short) 17, true);
        sendBoatSetting(player, (short) 19, 0);
        sendBoatSetting(player, (short) 20, true);
        sendBoatSetting(player, (short) 21, 0.0f);
        sendBoatSetting(player, (short) 27, (short) 0);
        sendBoatSetting(player, (short) 28, false);
        sendBoatSetting(player, (short) 29, false);
        sendBoatSetting(player, (short) 30, (byte) 5);
    }

    private final java.util.Set<UUID> lonelyPlayers = new java.util.HashSet<>();
    public void applyLonelyToPlayer(Player player, boolean lonelyActive) {
        UUID uuid = player.getUniqueId();
        boolean hasMod = FormulaRacing.hasOpenBoatUtilsMod(player);

        if (lonelyActive) {
            lonelyPlayers.add(uuid);

            if (hasMod) {
                // Packet 27, Valor 4: Sem colisão com barcos e players
                sendBoatSetting(player, (short) 27, (short) 4);
            } else {
                // Fallback para quem não tem o MOD
                org.bukkit.entity.Entity boat = player.getVehicle();

                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (other.equals(player)) continue;

                    // Esconde o jogador
                    other.hidePlayer(FormulaRacing.getInstance(), player);

                    // Esconde o barco (se ele estiver em um)
                    if (boat != null) {
                        other.hideEntity(FormulaRacing.getInstance(), boat);
                    }
                }
                Bukkit.getLogger().info("[FormulaRacing] Lonely ON (Invisibilidade total) para: " + player.getName());
            }
        } else {
            lonelyPlayers.remove(uuid);

            if (hasMod) {
                // Retorna ao Vanilla
                sendBoatSetting(player, (short) 27, (short) 0);
            } else {
                org.bukkit.entity.Entity boat = player.getVehicle();

                for (Player other : Bukkit.getOnlinePlayers()) {
                    if (other.equals(player)) continue;

                    other.showPlayer(FormulaRacing.getInstance(), player);

                    // Mostra o barco novamente
                    if (boat != null) {
                        other.showEntity(FormulaRacing.getInstance(), boat);
                    }
                }
                Bukkit.getLogger().info("[FormulaRacing] Lonely OFF (Visibilidade total) para: " + player.getName());
            }
        }
    }
}
