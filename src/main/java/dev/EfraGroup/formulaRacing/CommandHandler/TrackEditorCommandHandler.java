package dev.EfraGroup.formulaRacing.CommandHandler;

import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.TrackLeaderboard;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.Utils.DiscordUtils;
import dev.EfraGroup.formulaRacing.Utils.WorldEditSelect;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class TrackEditorCommandHandler implements CommandExecutor {

    private final PacketSender packetSender;
    private final DatabaseManager mysql;
    private final Map<UUID, String> selectedTracks = new HashMap<>();
    private final WorldEditSelect worldEditSelect;
    private final FormulaRacing plugin;

    public TrackEditorCommandHandler(DatabaseManager mysql, PacketSender packetSender, WorldEditSelect worldEditSelect, FormulaRacing plugin) {
        this.mysql = mysql;
        this.packetSender = packetSender;
        this.worldEditSelect = worldEditSelect;
        this.plugin = plugin;
    }

    private boolean checkSelectedTrack(Player player) {
        if (getSelectedTrack(player.getUniqueId()) == null) {
            player.sendMessage("§cVocê não selecionou nenhuma pista.");
            return false;
        }
        return true;
    }



    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command cannot be executed from the console.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§cUse: /trackedit <create|select|delete|boatutils|region|checkpoint|time|icon|open|close|grid|location>");
            return true;
        }

        String subcommand = args[0].toLowerCase();
        String selectedTrack = selectedTracks.get(player.getUniqueId());

        switch (subcommand) {
            case "broadcast": {
                if (args.length < 2) {
                    player.sendMessage("§cUso correto: /trackedit broadcast <subcomando> [mensagem/imagem]");
                    return true;
                }

                String sub = args[1].toLowerCase();
                switch (sub) {
                    case "newtrack" -> {
                        // Se houver mais argumentos após "newtrack", junta como URL/mensagem
                        String imageUrl = null;
                        if (args.length > 2) {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 2; i < args.length; i++) {
                                sb.append(args[i]);
                                if (i < args.length - 1) sb.append(" ");
                            }
                            imageUrl = sb.toString();
                        }

                        String trackName = getTargetTrack(player, args, 10);
                        if (trackName == null) return true;

                        // Supondo que você está dentro de uma classe que tem acesso à instância do plugin
                        DiscordUtils.sendNewTrackEmbed(
                                plugin, // aqui vai a instância do plugin
                                trackName,
                                mysql.getTrackOwner(trackName),
                                null,
                                imageUrl
                        );


                        player.sendMessage("§a✅ Mensagem de teste enviada para o Discord!");
                        return true;
                    }

                    default -> {
                        player.sendMessage("§cSubcomando inválido para broadcast.");
                        return true;
                    }
                }
            }

            case "setowner": {
                if (args.length < 2) {
                    player.sendMessage("§cUso correto: /trackedit setowner <nome_do_jogador> [nome_da_pista]");
                    return true;
                }

                // Nome do novo dono
                String newOwnerName = args[1];

                // Pega a pista alvo: se não for passada, usa a selecionada
                String trackName = getTargetTrack(player, args, 2);
                if (trackName == null) return true;

                // Atualiza o dono no banco de dados
                boolean success = mysql.setTrackOwner(trackName, newOwnerName);
                if (success) {
                    player.sendMessage("§a✅ Dono da pista §f" + trackName + " §aatualizado para: §f" + newOwnerName);
                } else {
                    player.sendMessage("§c❌ Erro ao atualizar o dono da pista. Veja o console para mais detalhes.");
                }
                return true;
            }


            case "cam": {
                if (args.length < 2) {
                    player.sendMessage("§cUse: /trackedit cam <set|delete|list>");
                    return true;
                }

                String sub = args[1].toLowerCase();
                switch (sub) {
                    case "set", "s" -> {
                        if (args.length < 3) {
                            player.sendMessage("§cUse: /trackedit cam set <id>");
                            return true;
                        }

                        int id;
                        try {
                            id = Integer.parseInt(args[2]);
                        } catch (NumberFormatException e) {
                            player.sendMessage("§cO ID da câmera deve ser um número!");
                            return true;
                        }

                        String trackName = getTargetTrack(player, args, 3);
                        if (trackName == null) return true; // mensagem já enviada

                        Location loc = player.getLocation();
                        mysql.addCamera(id, trackName, loc);
                        player.sendMessage("§aCâmera adicionada com ID §f" + id + " §ana pista §f" + trackName);
                    }

                    case "delete", "d" -> {
                        if (args.length < 3) {
                            player.sendMessage("§cUse: /trackedit cam delete <id>");
                            return true;
                        }
                        String trackName = getTargetTrack(player, args, 3);
                        int id;
                        try {
                            id = Integer.parseInt(args[2]);
                        } catch (NumberFormatException e) {
                            player.sendMessage("§cO ID da câmera deve ser um número!");
                            return true;
                        }

                        mysql.removeCamera(trackName, id);
                        player.sendMessage("§cCâmera de ID §f" + id + " §cremovida!");
                    }

                    case "list", "l" -> {
                        String trackName = getTargetTrack(player, args, 2);
                        if (trackName == null) return true;

                        List<Integer> cameraIds = mysql.getCamerasForTrack(trackName);
                        player.sendMessage("§eCâmeras da pista §f" + trackName + ": §a" + cameraIds);
                    }

                    default -> player.sendMessage("§cSubcomando inválido. Use set, delete ou list.");
                }
                return true;
            }
            case "create": {
                if (args.length < 2) {
                    player.sendMessage("§cUse: /trackedit create <nome>");
                    return true;
                }

                // Junta todos os argumentos em um único nome, permitindo espaços
                String trackName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

                // Limite de 30 caracteres
                if (trackName.length() > 30) {
                    player.sendMessage("§cO nome da pista não pode ter mais que 20 caracteres.");
                    return true;
                }

                // Verifica se já existe
                if (mysql.getAllTracks().contains(trackName)) {
                    player.sendMessage("§cJá existe uma pista com esse nome.");
                    return true;
                }

                // Define ícone
                ItemStack itemInHand = player.getInventory().getItemInMainHand();
                String iconName = (itemInHand.getType() == Material.AIR) ? "PACKED_ICE" : itemInHand.getType().name();

                // Cria a pista
                boolean created = mysql.createTrack(trackName, player.getLocation(), player.getName(), player.getUniqueId().toString());
                mysql.setTrackIcon(trackName, iconName);

                // Seleciona a pista automaticamente
                setSelectedTrack(player.getUniqueId(), trackName);

                if (created) {
                    player.sendMessage("§aPista '" + trackName + "' criada com sucesso com o ícone: §e" + iconName);
                } else {
                    player.sendMessage("§cErro ao criar a pista. Veja o console para mais detalhes.");
                }
                return true;
            }

            case "select": {
                if (args.length < 2) {
                    player.sendMessage("§cUse: /trackedit select <nome>");
                    return true;
                }

                // Concatena todos os argumentos após o subcomando para formar o nome completo da pista
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < args.length; i++) {
                    sb.append(args[i]);
                    if (i < args.length - 1) sb.append(" "); // adiciona espaço entre palavras
                }
                String trackName = sb.toString();

                if (!mysql.getAllTracks().contains(trackName)) {
                    player.sendMessage("§cEssa pista não existe.");
                    return true;
                }

                setSelectedTrack(player.getUniqueId(), trackName);
                player.sendMessage("§aPista selecionada: §f" + trackName);
                return true;
            }

            case "resetalltimes": {
                String trackName = getTargetTrack(player, args, 1); // 1 = primeiro argumento após o subcomando
                if (trackName == null) return true; // erro já exibido

                if (!mysql.getAllTracks().contains(trackName)) {
                    player.sendMessage("§cEssa pista não existe.");
                    return true;
                }

                mysql.resetAllTrackTimes(trackName);
                player.sendMessage("§aTodos os tempos da pista §f" + trackName + " §aforam resetados.");
                return true;
            }


            case "delete": {
                String trackName = getTargetTrack(player, args, 1);
                if (trackName == null) return true;

                if (!mysql.getAllTracks().contains(trackName)) {
                    player.sendMessage("§cPista '" + trackName + "' não encontrada.");
                    return true;
                }

                mysql.deleteTrack(trackName);
                player.sendMessage("§aPista '" + trackName + "' deletada com sucesso.");
                return true;
            }

            case "region": {
                String trackName = getTargetTrack(player, args, 2); // /region <start|end> [pista]
                if (trackName == null) return true;

                if (args.length < 2) {
                    player.sendMessage("§cUse: /trackedit region <start|end> [nome da pista]");
                    return true;
                }

                if (!worldEditSelect.hasSelection(player)) {
                    player.sendMessage("§cVocê precisa fazer uma seleção com o WorldEdit.");
                    return true;
                }

                String type = args[1].toLowerCase();
                Location regionMin = worldEditSelect.getMin(player);
                Location regionMax = worldEditSelect.getMax(player);

                int savedId;
                switch (type) {
                    case "start":
                        savedId = mysql.saveRegion(trackName, regionMin, regionMax, "START");
                        if (savedId >= 0) player.sendMessage("§aRegião START salva com sucesso! ID: " + savedId);
                        else player.sendMessage("§cErro ao salvar a região START.");
                        break;
                    case "end":
                        savedId = mysql.saveRegion(trackName, regionMin, regionMax, "END");
                        if (savedId >= 0) player.sendMessage("§aRegião END salva com sucesso! ID: " + savedId);
                        else player.sendMessage("§cErro ao salvar a região END.");
                        break;
                    default:
                        player.sendMessage("§cTipo inválido. Use apenas: start ou end.");
                        return true;
                }
                return true;
            }

            case "spawn": {
                String trackName = getTargetTrack(player, args, 3); // /checkpoint <add|remove> <id> [pista]
                if (trackName == null) return true;

                // Mostra para debug antes de salvar
                String trackNameWS = trackName.replace(" ", "").toLowerCase();
                plugin.getLogger().info("Salvando spawn da pista '" + trackName + "' (trackNameWS='" + trackNameWS + "') para o jogador " + player.getName());
                player.sendMessage("§eSalvando spawn da pista: §f" + trackName + " §7(normalizado: §f" + trackNameWS + "§7)");

                // Salva no MySQL/SQLite
                mysql.setTrackSpawn(trackName, player.getLocation());

                player.sendMessage("§aSpawn salvo com sucesso!");
                return true;
            }



            case "checkpoint": {
                String trackName = getTargetTrack(player, args, 3); // /checkpoint <add|remove> <id> [pista]
                if (trackName == null) return true;

                if (args.length < 3) {
                    player.sendMessage("§cUso: /trackedit checkpoint <add|remove> <id> [nome da pista]");
                    return true;
                }

                String action = args[1].toLowerCase();
                int id;
                try {
                    id = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cID inválido. Use um número inteiro.");
                    return true;
                }

                boolean success;
                switch (action) {
                    case "add":
                        if (!worldEditSelect.hasSelection(player)) {
                            player.sendMessage("§cVocê precisa fazer uma seleção com o WorldEdit para adicionar o checkpoint.");
                            return true;
                        }

                        // Pega dados da seleção
                        Location min = worldEditSelect.getMin(player);
                        Location max = worldEditSelect.getMax(player);
                        String worldName = player.getWorld().getName();

                        success = mysql.addCheckpoint(id, trackName, player);
                        if (success) {
                            player.sendMessage("§aCheckpoint " + id + " adicionado na pista §e" + trackName);

                            // Log detalhado no console
                            Bukkit.getLogger().info("[FormulaRacing] === Salvando Checkpoint ===");
                            Bukkit.getLogger().info("[FormulaRacing] Track: " + trackName);
                            Bukkit.getLogger().info("[FormulaRacing] ID: " + id);
                            Bukkit.getLogger().info("[FormulaRacing] Mundo: " + worldName);
                            Bukkit.getLogger().info("[FormulaRacing] Min: X=" + min.getX() + " Y=" + min.getY() + " Z=" + min.getZ());
                            Bukkit.getLogger().info("[FormulaRacing] Max: X=" + max.getX() + " Y=" + max.getY() + " Z=" + max.getZ());
                            Bukkit.getLogger().info("[FormulaRacing] =======================");
                        } else {
                            player.sendMessage("§cErro ao adicionar o checkpoint " + id);
                        }
                        break;

                    case "remove":
                        success = mysql.removeCheckpoint(trackName, id);
                        if (success) player.sendMessage("§cCheckpoint " + id + " removido da pista §e" + trackName);
                        else player.sendMessage("§cErro ao remover o checkpoint " + id);
                        break;

                    default:
                        player.sendMessage("§cUso: /trackedit checkpoint <add|remove> <id> [nome da pista]");
                        return true;
                }
                return true;
            }

            case "time": {
                String trackName = getTargetTrack(player, args, 2); // /time <ticks> [nome da pista]
                if (trackName == null) return true;

                if (args.length < 2) {
                    player.sendMessage("§cUse: /trackedit time <ticks> [nome da pista]");
                    return true;
                }

                long ticks;
                try {
                    ticks = Long.parseLong(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cTicks inválidos. Use um número válido.");
                    return true;
                }


            }


            case "icon": {
                if (args.length < 2) {
                    player.sendMessage("§cUse: /trackedit icon <material> [nome da pista]");
                    return true;
                }

                // Nome do material
                String iconName = args[1].toUpperCase();
                Material iconMat;
                try {
                    iconMat = Material.valueOf(iconName);
                } catch (IllegalArgumentException e) {
                    player.sendMessage("§cMaterial inválido: " + iconName);
                    return true;
                }

                // Nome da pista: se passou mais argumentos após o material, concatena
                String trackName = getTargetTrack(player, args, 2);
                if (trackName == null) return true; // erro já mostrado

                if (mysql.setTrackIcon(trackName, iconMat.name()))
                    player.sendMessage("§aÍcone da pista '" + trackName + "' atualizado para: §e" + iconMat.name());
                else
                    player.sendMessage("§cErro ao atualizar ícone da pista.");
                return true;
            }
            // ======================================================
//           SUBCOMANDOS DO BOATUTILS
// ======================================================
            // ============================================================================
// BOATUTILS
// ============================================================================
            case "boatutils": {

                if (args.length < 2) {
                    player.sendMessage("§cUse: /trackedit boatutils <reset|set>");
                    return true;
                }

                // ============================================================
                // /trackedit boatutils reset <track>
                // ============================================================
                if (args[1].equalsIgnoreCase("reset")) {

                    if (args.length < 3) {
                        player.sendMessage("§cUse: /trackedit boatutils reset <pista>");
                        return true;
                    }

                    String track = args[2].replace(" ", "");

                    mysql.resetBoatUtilsSettings(track);

                    player.sendMessage("§aConfigurações BoatUtils resetadas para vanilla na pista §e" + track);
                    return true;
                }

                // ============================================================
                // SET (group / config)
                // ============================================================
                if (!args[1].equalsIgnoreCase("set")) {
                    player.sendMessage("§cUse: /trackedit boatutils set <group|config>");
                    return true;
                }

                if (args.length < 3) {
                    player.sendMessage("§cUse: /trackedit boatutils set <group|config>");
                    return true;
                }

                // ============================================================
                // /trackedit boatutils set group <mode> <track>
                // ============================================================
                if (args[2].equalsIgnoreCase("group")) {

                    if (args.length < 5) {
                        player.sendMessage("§cUse: /trackedit boatutils set group <mode> <pista>");
                        return true;
                    }

                    String modeName = args[3].toUpperCase();
                    BoatUtilsGroupMode mode;

                    try {
                        mode = BoatUtilsGroupMode.valueOf(modeName);
                    } catch (Exception e) {
                        player.sendMessage("§cModo inválido! Use TAB para ver as opções.");
                        return true;
                    }

                    String track = args[4].replace(" ", "");

                    applyGroupMode(track, mode);

                    player.sendMessage("§aModo §b" + modeName + " §aaplicado na pista §e" + track);
                    return true;
                }

                // ============================================================
                // /trackedit boatutils set config <...>
                // ============================================================
                if (!args[2].equalsIgnoreCase("config")) {
                    player.sendMessage("§cUse: /trackedit boatutils set <group|config>");
                    return true;
                }

                if (args.length < 4) {
                    player.sendMessage("§cUse: /trackedit boatutils set config <setting> set <valor> [pista]");
                    return true;
                }

                String setting = args[3].toLowerCase();

                // ------------------------------------------------------------
                // Função utilitária para pegar a pista
                // ------------------------------------------------------------
                final java.util.function.Function<Integer, String> fetchTrack = (idx) -> {
                    String t = getTargetTrack(player, args, idx);
                    if (t == null) return null;
                    return t.replace(" ", "");
                };

                // ============================================================
                // FLOAT SETTINGS
                // ============================================================
                final Map<String, java.util.function.BiConsumer<String, Float>> floatSetters = Map.of(
                        "stepheight", (track, v) -> mysql.setStepHigh(track, (double) v),
                        "defaultslipperiness", mysql::setDefaultSlipperiness,
                        "jumpforce", mysql::setJumpForce,
                        "yawacceleration", mysql::setYawAcceleration,
                        "forwardacceleration", mysql::setForwardAcceleration,
                        "backwardacceleration", mysql::setBackwardAcceleration,
                        "turningforwardacceleration", mysql::setTurningForwardAcceleration,
                        "swimforce", mysql::setSwimForce
                );

                if (floatSetters.containsKey(setting)) {

                    if (args.length < 6 || !args[4].equalsIgnoreCase("set")) {
                        player.sendMessage("§cUse: /trackedit boatutils set config "
                                + setting + " set <float> [pista]");
                        return true;
                    }

                    float value;
                    try {
                        value = Float.parseFloat(args[5]);
                    } catch (Exception e) {
                        player.sendMessage("§cValor inválido. Exemplo: 1.25");
                        return true;
                    }

                    String track = fetchTrack.apply(6);
                    if (track == null) return true;

                    floatSetters.get(setting).accept(track, value);
                    player.sendMessage("§a" + setting + " atualizado para §f" + value);
                    return true;
                }

                // ============================================================
                // DOUBLE (gravity)
                // ============================================================
                if (setting.equals("gravity")) {

                    if (args.length < 6 || !args[4].equalsIgnoreCase("set")) {
                        player.sendMessage("§cUse: /trackedit boatutils set config gravity set <double> [pista]");
                        return true;
                    }

                    double value;
                    try {
                        value = Double.parseDouble(args[5]);
                    } catch (Exception e) {
                        player.sendMessage("§cNúmero inválido. Exemplo: -0.04");
                        return true;
                    }

                    String track = fetchTrack.apply(6);
                    if (track == null) return true;

                    mysql.setGravity(track, value);
                    player.sendMessage("§aGravity atualizada para §f" + value);
                    return true;
                }

                // ============================================================
                // BOOLEAN SETTINGS
                // ============================================================
                final Map<String, java.util.function.BiConsumer<String, Boolean>> booleanSetters = Map.of(
                        "falldamage", mysql::setFallDamage,
                        "waterelevation", mysql::setWaterElevation,
                        "aircontrol", mysql::setAirControl,
                        "allowaccelerationstacking", mysql::setAllowAccelerationStacking,
                        "underwatercontrol", mysql::setUnderwaterControl,
                        "surfacewatercontrol", mysql::setSurfaceWaterControl,
                        "waterjumping", mysql::setWaterJumping,
                        "airstepping", mysql::setAirStepping,
                        "tenstepinterpolation", mysql::setTenStepInterpolation
                );

                if (booleanSetters.containsKey(setting)) {

                    if (args.length < 6 || !args[4].equalsIgnoreCase("set")) {
                        player.sendMessage("§cUse: /trackedit boatutils set config " + setting
                                + " set <true|false> [pista]");
                        return true;
                    }

                    Boolean value = switch (args[5].toLowerCase()) {
                        case "true" -> true;
                        case "false" -> false;
                        default -> null;
                    };

                    if (value == null) {
                        player.sendMessage("§cValor inválido. Use true ou false.");
                        return true;
                    }

                    String track = fetchTrack.apply(6);
                    if (track == null) return true;

                    booleanSetters.get(setting).accept(track, value);
                    player.sendMessage("§a" + setting + " atualizado para §f" + value);
                    return true;
                }

                // ============================================================
                // INTEGER SETTINGS
                // ============================================================
                final Map<String, java.util.function.BiConsumer<String, Integer>> intSetters = Map.of(
                        "collisionmode", mysql::setCollisionMode,
                        "collisionresolution", mysql::setCollisionResolution,
                        "coyotetime", mysql::setCoyoteTime
                );

                if (intSetters.containsKey(setting)) {

                    if (args.length < 6 || !args[4].equalsIgnoreCase("set")) {
                        player.sendMessage("§cUse: /trackedit boatutils set config "
                                + setting + " set <int> [pista]");
                        return true;
                    }

                    int value;
                    try {
                        value = Integer.parseInt(args[5]);
                    } catch (Exception e) {
                        player.sendMessage("§cNúmero inteiro inválido.");
                        return true;
                    }

                    String track = fetchTrack.apply(6);
                    if (track == null) return true;

                    intSetters.get(setting).accept(track, value);
                    player.sendMessage("§a" + setting + " atualizado para §f" + value);
                    return true;
                }

                // ============================================================
                // CUSTOM SLIPPERINESS
                // ============================================================
                if (setting.equals("customslipperiness")) {

                    if (args.length < 5) {
                        player.sendMessage("§cUse:");
                        player.sendMessage("§e/trackedit boatutils set config customslipperiness add <bloco> <valor> [pista]");
                        player.sendMessage("§e/trackedit boatutils set config customslipperiness reset [pista]");
                        return true;
                    }

                    String sub = args[4].toLowerCase();

                    // ---------------------- RESET ----------------------
                    if (sub.equals("reset")) {

                        String track = fetchTrack.apply(5);
                        if (track == null) return true;

                        mysql.resetCustomSlipperiness(track);

                        player.sendMessage("§aCustomSlipperiness resetado na pista §e" + track);
                        return true;
                    }

                    // ---------------------- ADD ------------------------
                    if (sub.equals("add")) {

                        if (args.length < 7) {
                            player.sendMessage("§cUse: /trackedit boatutils set config customslipperiness add <bloco> <valor> [pista]");
                            return true;
                        }

                        Material mat = Material.matchMaterial(args[5]);
                        if (mat == null) {
                            player.sendMessage("§cBloco inválido: §f" + args[5]);
                            return true;
                        }

                        float value;
                        try {
                            value = Float.parseFloat(args[6]);
                        } catch (Exception e) {
                            player.sendMessage("§cValor inválido. Ex: 0.98");
                            return true;
                        }

                        String track = fetchTrack.apply(7);
                        if (track == null) return true;

                        String blockId = mat.getKey().toString();

                        mysql.addCustomSlipperiness(track, blockId, value);

                        player.sendMessage("§aSlipperiness do bloco §e" + blockId + " §aatualizado para §b" + value);
                        return true;
                    }

                    player.sendMessage("§cSubcomando inválido. Use add/reset.");
                    return true;
                }

                // ============================================================
                // SETTING INVÁLIDO
                // ============================================================
                player.sendMessage("§cConfiguração inválida.");
                return true;
            }



            case "open": {
                String trackName = getTargetTrack(player, args, 1);
                if (trackName == null) return true;

                if (mysql.isTrackOpen(trackName)) {
                    player.sendMessage("§cA pista " + trackName + " já está aberta.");
                    return true;
                }
                mysql.setTrackOpen(trackName, true);
                player.sendMessage("§ePista " + trackName + " aberta com sucesso.");
                return true;
            }

            case "close": {
                String trackName = getTargetTrack(player, args, 1);
                if (trackName == null) return true;

                if (!mysql.isTrackOpen(trackName)) {
                    player.sendMessage("§cA pista " + trackName + " já está fechada.");
                    return true;
                }
                mysql.setTrackOpen(trackName, false);
                player.sendMessage("§ePista " + trackName + " fechada com sucesso.");
                return true;
            }

            case "grid": {
                if (args.length < 3) {
                    player.sendMessage("§cUso: /trackedit grid <add|remove> <id> [nome da pista]");
                    return true;
                }

                String action = args[1].toLowerCase();
                int id;
                try {
                    id = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    player.sendMessage("§cO ID deve ser um número inteiro.");
                    return true;
                }

                String trackName = getTargetTrack(player, args, 3);
                if (trackName == null) return true;

                if ("add".equals(action)) {
                    if (mysql.addGridPosition(trackName, id, player.getLocation()))
                        player.sendMessage("§aPosição de grid " + id + " adicionada!");
                    else
                        player.sendMessage("§cErro ao adicionar a posição de grid.");
                } else if ("remove".equals(action)) {
                    if (mysql.removeGridPosition(trackName, id))
                        player.sendMessage("§cPosição de grid " + id + " removida!");
                    else
                        player.sendMessage("§cErro ao remover a posição de grid.");
                } else
                    player.sendMessage("§cUso: /trackedit grid <add|remove> <id> [nome da pista]");
                return true;
            }

            case "location": {
                if (args.length < 2) {
                    player.sendMessage("§cUse: /trackedit location <subcomando> ...");
                    return true;
                }

                String subCommand = args[1].toLowerCase();

                switch (subCommand) {
                    case "leaderboard": {
                        if (args.length < 3) {
                            player.sendMessage("§cUse: /trackedit location leaderboard <track> [x y z]");
                            return true;
                        }

                        String trackName = args[2];
                        Location targetLocation;

                        // Se houver coordenadas
                        if (args.length >= 6) {
                            try {
                                double x = Double.parseDouble(args[3]);
                                double y = Double.parseDouble(args[4]);
                                double z = Double.parseDouble(args[5]);
                                targetLocation = new Location(player.getWorld(), x, y + 3, z);
                            } catch (NumberFormatException e) {
                                player.sendMessage("§cCoordenadas inválidas.");
                                return true;
                            }
                        } else {
                            // Usa a posição atual do jogador
                            targetLocation = player.getLocation();
                        }

                        TrackLeaderboard leaderboard = plugin.getOrCreateLeaderboard(trackName, targetLocation);
                        leaderboard.setLocation(targetLocation);
                        leaderboard.updateLeaderboard();

                        player.sendMessage("§aHolograma da pista §e" + trackName + " §ateleportado para " +
                                String.format("X: %.2f Y: %.2f Z: %.2f",
                                        targetLocation.getX(), targetLocation.getY(), targetLocation.getZ()));
                        return true;
                    }

                    // Exemplo: outros subcomandos futuros
                    case "startline": {
                        player.sendMessage("§eFunção de 'startline' ainda não implementada.");
                        return true;
                    }

                    default:
                        player.sendMessage("§cSubcomando desconhecido. Use: /trackedit location <leaderboard|startline|...>");
                        return true;
                }
            }

        }
        return false;
    }

    // ======== Normalização ========
    private String normalizeTrackName(String name) {
        return name.replaceAll("\\s+", "").toLowerCase();
    }

    private String getOriginalTrackName(String normalizedName) {
        for (String track : mysql.getAllTracks()) {
            if (normalizeTrackName(track).equals(normalizedName)) return track;
        }
        return null;
    }

    public void setSelectedTrack(UUID playerUUID, String trackName) {
        selectedTracks.put(playerUUID, normalizeTrackName(trackName));
    }

    public String getSelectedTrack(UUID playerUUID) {
        String normalized = selectedTracks.get(playerUUID);
        if (normalized == null) return null;
        return getOriginalTrackName(normalized);
    }

    public void clearSelectedTrack(UUID playerUUID) {
        selectedTracks.remove(playerUUID);
    }

    // Método utilitário para pegar a pista alvo
    public String getTargetTrack(Player player, String[] args, int startArgIndex) {
        if (args.length > startArgIndex) {
            // Concatena todos os argumentos a partir de startArgIndex
            StringBuilder sb = new StringBuilder();
            for (int i = startArgIndex; i < args.length; i++) {
                sb.append(args[i]);
                if (i < args.length - 1) sb.append(" ");
            }
            return sb.toString();
        } else {
            // Usa o selectedTrack
            String selected = getSelectedTrack(player.getUniqueId());
            if (selected == null) {
                player.sendMessage("§cVocê não selecionou nenhuma pista e não forneceu um nome.");
                return null;
            }
            return selected;
        }
    }

    public void applyGroupMode(String track, BoatUtilsGroupMode mode) {
        if (mode == BoatUtilsGroupMode.BA || mode ==  BoatUtilsGroupMode.BA_NOFD) {
            mysql.replaceAllBoatUtilsSettings(
                    track,
                    mode.stepHeight,
                    mode.slipperiness,
                    !mode.noFallDamage,
                    mode.waterElevation,
                    mode.airControl,
                    mode.jumpForce == null ? 0.0f : mode.jumpForce,
                    -0.03999999910593033,
                    1.0f,
                    0.04f,
                    0.005f,
                    0.005f,
                    true,
                    true,
                    true,
                    0,
                    true,
                    0.0f,
                    0,
                    false,
                    false,
                    5,
                    "minecraft:air;0.98",
                    null

            );

        } else if (mode ==  BoatUtilsGroupMode.BA_BLUE_NOFD || mode ==  BoatUtilsGroupMode.BA_BLUE) {
            mysql.replaceAllBoatUtilsSettings(
                    track,
                    mode.stepHeight,
                    mode.slipperiness,
                    !mode.noFallDamage,
                    mode.waterElevation,
                    mode.airControl,
                    mode.jumpForce == null ? 0.0f : mode.jumpForce,
                    -0.03999999910593033,
                    1.0f,
                    0.04f,
                    0.005f,
                    0.005f,
                    true,
                    true,
                    true,
                    0,
                    true,
                    0.0f,
                    0,
                    false,
                    false,
                    5,
                    "minecraft:air;0.989",
                    null

            );
        } else {
        mysql.replaceAllBoatUtilsSettings(
                track,
                mode.stepHeight,
                mode.slipperiness,
                !mode.noFallDamage,
                mode.waterElevation,
                mode.airControl,
                mode.jumpForce == null ? 0.0f : mode.jumpForce,
                -0.03999999910593033,
                1.0f,
                0.04f,
                0.005f,
                0.005f,
                true,
                true,
                true,
                0,
                true,
                0.0f,
                0,
                false,
                false,
                5,
                null,
                null

        );
    }
    }


    public enum BoatUtilsGroupMode {

        RALLY(8, 0.98f, true, true, 1.25f, true, 0.0f),
        RALLY_BLUE(9, 0.989f, true, true, 1.25f, true, 0.0f),

        BA_NOFD(10, 0.6f, true, true, 1.25f, true, 0.0f),
        PARKOUR(11, 0.98f, true, true, 0.5f, true, 0.36f),

        BA_BLUE_NOFD(12, 0.6f, true, true, 1.25f, true),
        PARKOUR_BLUE(13, 0.989f, true, true, 0.5f, true, 0.36f),

        BA(14, 0.6f, true, true, 1.25f),
        BA_BLUE(15, 0.6f, true, true, 1.25f),

        // -------- BROKEN SLIME (deprecated fix) --------
        BROKEN_SLIME_RALLY(0, 0.98f, true, true, 1.25f, true),
        BROKEN_SLIME_RALLY_BLUE(1, 0.989f, true, true, 1.25f, true),
        BROKEN_SLIME_BA_NOFD(2, 0.6f, true, false, 1.25f, true),
        BROKEN_SLIME_PARKOUR(3, 0.98f, true, false, 0.5f, true, 0.36f),
        BROKEN_SLIME_BA_BLUE_NOFD(4, 0.6f, true, false, 1.25f, true),
        BROKEN_SLIME_PARKOUR_BLUE(5, 0.989f, true, false, 0.5f, true, 0.36f),
        BROKEN_SLIME_BA(6, 0.6f, true, true, 1.25f, true),
        BROKEN_SLIME_BA_BLUE(7, 0.6f, true, true, 1.25f, true);

        public final int id;
        public final float slipperiness;
        public final boolean airControl;
        public final boolean waterElevation;
        public final float stepHeight;
        public final Float jumpForce; // null = usar padrão
        public final boolean noFallDamage;

        BoatUtilsGroupMode(int id, float slip, boolean air, boolean water, float step) {
            this(id, slip, air, water, step, false, null);
        }

        BoatUtilsGroupMode(int id, float slip, boolean air, boolean water, float step, boolean noFallDamage) {
            this(id, slip, air, water, step, noFallDamage, null);
        }

        BoatUtilsGroupMode(int id, float slip, boolean air, boolean water, float step, Float jumpForce) {
            this(id, slip, air, water, step, false, jumpForce);
        }

        BoatUtilsGroupMode(int id, float slip, boolean air, boolean water, float step, boolean noFallDamage, Float jumpForce) {
            this.id = id;
            this.slipperiness = slip;
            this.airControl = air;
            this.waterElevation = water;
            this.stepHeight = step;
            this.noFallDamage = noFallDamage;
            this.jumpForce = jumpForce;
        }
    }


}
