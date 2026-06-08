package dev.EfraGroup.formulaRacing.Command;

import dev.EfraGroup.formulaRacing.Command.Help.CommandHelpService;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.PacketSender;
import dev.EfraGroup.formulaRacing.TrackLeaderboard;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.Database.GridPosition;
import dev.EfraGroup.formulaRacing.Gui.PitStopEditorGui;
import dev.EfraGroup.formulaRacing.Heat.PitStopManager;
import dev.EfraGroup.formulaRacing.Heat.PitStopRegion;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.DiscordUtils;
import dev.EfraGroup.formulaRacing.Utils.TitleHelper;
import dev.EfraGroup.formulaRacing.Utils.WorldEditSelect;
import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CatchUnknown;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@CommandAlias("trackedit|te")
@CommandPermission("formularacing.admin")
@Description("Comandos de edição de pistas")
public class TrackEditorCommand extends BaseCommand {
    private final FormulaRacing plugin;
    private final DatabaseManager mysql;
    private final PacketSender packetSender;
    private final WorldEditSelect worldEditSelect;
    private final Map<UUID, String> selectedTracks = new HashMap();

    public TrackEditorCommand(FormulaRacing plugin, DatabaseManager mysql, PacketSender packetSender, WorldEditSelect worldEditSelect) {
        this.plugin = plugin;
        this.mysql = mysql;
        this.packetSender = packetSender;
        this.worldEditSelect = worldEditSelect;
    }

    @Default
    @CatchUnknown
    public void onDefault(Player player) {
        CommandHelpService.sendHelp(player, this, "/trackedit");
    }

    @Subcommand("help|ajuda|?")
    @Description("Mostra a ajuda do comando trackedit")
    public void onHelp(Player player) {
        CommandHelpService.sendHelp(player, this, "/trackedit");
    }

    private String normalizeTrackName(String name) {
        return name.replaceAll("\\s+", "").toLowerCase();
    }

    private String getOriginalTrackName(String normalizedName) {
        for(String track : this.mysql.getAllTracks()) {
            if (this.normalizeTrackName(track).equals(normalizedName)) {
                return track;
            }
        }

        return null;
    }

    public void setSelectedTrack(UUID playerUUID, String trackName) {
        this.selectedTracks.put(playerUUID, this.normalizeTrackName(trackName));
    }

    public String getSelectedTrack(UUID playerUUID) {
        String normalized = (String)this.selectedTracks.get(playerUUID);
        return normalized == null ? null : this.getOriginalTrackName(normalized);
    }

    private String getTargetTrack(Player player, String trackNameArg) {
        if (trackNameArg != null && !trackNameArg.isEmpty()) {
            return trackNameArg;
        } else {
            String selected = this.getSelectedTrack(player.getUniqueId());
            if (selected == null) {
                player.sendMessage("§cVocê não selecionou nenhuma pista e não forneceu um nome.");
                return null;
            } else {
                return selected;
            }
        }
    }

    private String formatLocation(Location loc) {
        return String.format("%.1f, %.1f, %.1f (yaw: %.0f°)", loc.getX(), loc.getY(), loc.getZ(), loc.getYaw());
    }

    @Subcommand("create")
    @Description("Cria uma nova pista")
    @CommandCompletion("@nothing")
    public void onCreate(Player player, String trackName) {
        if (trackName.length() > 30) {
            this.plugin.sendMessage(player, "te_track_name_length", new String[0]);
        } else if (this.mysql.isTrackExists(trackName)) {
            this.plugin.sendMessage(player, "te_track_exists", new String[0]);
        } else {
            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            String iconName = itemInHand.getType() == Material.AIR ? "PACKED_ICE" : itemInHand.getType().name();
            boolean created = this.mysql.createTrack(trackName, player.getLocation(), player.getName(), player.getUniqueId().toString());
            this.mysql.setTrackIcon(trackName, iconName);
            this.setSelectedTrack(player.getUniqueId(), trackName);
            if (created) {
                this.plugin.sendMessage(player, "te_created", new String[]{"{track}", trackName, "{icon}", iconName});
            } else {
                this.plugin.sendMessage(player, "te_create_error", new String[0]);
            }

        }
    }

    @Subcommand("select")
    @Description("Seleciona uma pista para edição (ou detecta a atual)")
    @CommandCompletion("@tracks")
    public void onSelect(Player player, @Optional String trackName) {
        if (trackName == null) {
            Location loc = player.getLocation();
            String detectedTrack = null;

            for(DatabaseManager.RegionData r : this.mysql.getAllRegions()) {
                if (r.getWorld().equalsIgnoreCase(loc.getWorld().getName()) && loc.getX() >= r.getMinX() && loc.getX() <= r.getMaxX() && loc.getY() >= r.getMinY() && loc.getY() <= r.getMaxY() && loc.getZ() >= r.getMinZ() && loc.getZ() <= r.getMaxZ()) {
                    detectedTrack = r.getTrackName();
                    break;
                }
            }

            if (detectedTrack == null) {
                Map<String, DatabaseManager.TrackData> tracks = this.mysql.getAllTracksWithData();

                for(DatabaseManager.TrackData td : tracks.values()) {
                    if (td.getSpawnLocation() != null && td.getSpawnLocation().getWorld().getName().equals(loc.getWorld().getName()) && td.getSpawnLocation().distanceSquared(loc) < (double)2500.0F) {
                        detectedTrack = td.getTrackName();
                        break;
                    }
                }
            }

            if (detectedTrack == null) {
                player.sendMessage("§cVocê não especificou uma pista e não foi possível detectar nenhuma próxima.");
                return;
            }

            trackName = detectedTrack;
            player.sendMessage("§e[Auto-Detect] Pista detectada: §f" + detectedTrack);
        }

        if (!this.mysql.isTrackExists(trackName)) {
            player.sendMessage("§cEssa pista não existe.");
        } else {
            this.setSelectedTrack(player.getUniqueId(), trackName);
            this.plugin.sendMessage(player, "te_selected", new String[]{"{track}", trackName});
        }
    }

    @Subcommand("view")
    @Description("Visualiza as regiões da pista com partículas")
    @CommandCompletion("@tracks")
    public void onView(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (!this.mysql.isTrackExists(trackName)) {
                player.sendMessage("§cEssa pista não existe.");
            } else {
                boolean isViewing = this.plugin.getTrackVisualizer().isViewing(player.getUniqueId(), trackName);
                if (!isViewing) {
                    Location spawn = this.mysql.getTrackSpawn(trackName);
                    if (spawn != null) {
                        player.teleport(spawn);
                        this.plugin.sendMessage(player, "te_teleported", new String[]{"{track}", trackName});
                    }
                }

                this.setSelectedTrack(player.getUniqueId(), trackName);
                this.plugin.sendMessage(player, "te_view_enabled", new String[]{"{track}", trackName});
                this.plugin.getTrackVisualizer().toggleView(player, trackName);
            }
        }
    }

    @Subcommand("delete")
    @Description("Deleta uma pista")
    @CommandCompletion("@tracks")
    public void onDelete(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (!this.mysql.isTrackExists(trackName)) {
                player.sendMessage("§cPista '" + trackName + "' não encontrada.");
            } else {
                this.mysql.deleteTrack(trackName);
                this.plugin.sendMessage(player, "te_deleted", new String[]{"{track}", trackName});
            }
        }
    }

    @Subcommand("broadcast newtrack")
    @Description("Envia mensagem de nova pista para o Discord")
    @CommandCompletion("@nothing @tracks")
    public void onBroadcastNewTrack(Player player, @Optional String imageUrl, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            DiscordUtils.sendNewTrackEmbed(this.plugin, trackName, this.mysql.getTrackOwner(trackName), (String)null, imageUrl);
            player.sendMessage("§a✅ Mensagem de teste enviada para o Discord!");
        }
    }

    @Subcommand("setowner")
    @Description("Define o dono de uma pista")
    @CommandCompletion("@players @tracks")
    public void onSetOwner(Player player, String newOwnerName, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            boolean success = this.mysql.setTrackOwner(trackName, newOwnerName);
            if (success) {
                this.plugin.sendMessage(player, "te_owner_set", new String[]{"{track}", trackName, "{owner}", newOwnerName});
            } else {
                player.sendMessage("§c❌ Erro ao atualizar o dono da pista. Veja o console para mais detalhes.");
            }

        }
    }

    @Subcommand("cam set|s")
    @Description("Adiciona uma câmera na pista")
    @CommandCompletion("@nothing @tracks")
    public void onCamSet(Player player, int id, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            Location loc = player.getLocation();
            this.mysql.addCamera(id, trackName, loc);
            this.plugin.sendMessage(player, "te_cam_added", new String[]{"{id}", String.valueOf(id), "{track}", trackName});
        }
    }

    @Subcommand("cam delete|d")
    @Description("Remove uma câmera da pista")
    @CommandCompletion("@nothing @tracks")
    public void onCamDelete(Player player, int id, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            this.mysql.removeCamera(trackName, id);
            this.plugin.sendMessage(player, "te_cam_removed", new String[]{"{id}", String.valueOf(id)});
        }
    }

    @Subcommand("cam list|l")
    @Description("Lista as câmeras de uma pista")
    @CommandCompletion("@tracks")
    public void onCamList(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            List<Integer> cameraIds = this.mysql.getCamerasForTrack(trackName);
            this.plugin.sendMessage(player, "te_cam_list", new String[]{"{track}", trackName, "{ids}", cameraIds.toString()});
        }
    }

    @Subcommand("resetalltimes")
    @Description("Reseta todos os tempos de uma pista")
    @CommandCompletion("@tracks")
    public void onResetAllTimes(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            this.mysql.resetAllTrackTimes(trackName);
            this.plugin.sendMessage(player, "te_times_reset", new String[]{"{track}", trackName});
        }
    }

    @Subcommand("region start")
    @Description("Define a região de largada (START)")
    @CommandCompletion("@tracks")
    public void onRegionStart(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (!WorldEditSelect.hasSelection(player)) {
                this.plugin.sendMessage(player, "te_no_selection", new String[0]);
            } else {
                WorldEditSelect.SelectionData data = WorldEditSelect.getSelectionData(player);
                if (data == null) {
                    this.plugin.sendMessage(player, "te_no_selection", new String[0]);
                    return;
                }
                int savedId = this.mysql.saveRegion(trackName, data.getMin(), data.getMax(), "START", data.getShape(), data.getPoints());
                if (savedId >= 0) {
                    this.plugin.sendMessage(player, "te_region_start_saved", new String[]{"{id}", String.valueOf(savedId)});
                    this.plugin.getRegionListener().reloadRegions();
                } else {
                    this.plugin.sendMessage(player, "te_region_error", new String[]{"{type}", "START"});
                }

            }
        }
    }

    @Subcommand("region reset")
    @Description("Define a região de reset (RESET)")
    @CommandCompletion("@tracks")
    public void onRegionReset(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (!WorldEditSelect.hasSelection(player)) {
                this.plugin.sendMessage(player, "te_no_selection", new String[0]);
            } else {
                WorldEditSelect.SelectionData data = WorldEditSelect.getSelectionData(player);
                if (data == null) {
                    this.plugin.sendMessage(player, "te_no_selection", new String[0]);
                    return;
                }
                int savedId = this.mysql.saveRegion(trackName, data.getMin(), data.getMax(), "RESET", data.getShape(), data.getPoints());
                if (savedId >= 0) {
                    player.sendMessage("§aRegião de RESET definida com sucesso! ID: " + savedId);
                    this.plugin.getRegionListener().reloadRegions();
                } else {
                    this.plugin.sendMessage(player, "te_region_error", new String[]{"{type}", "RESET"});
                }

            }
        }
    }

    @Subcommand("region end")
    @Description("Define a região de chegada (END)")
    @CommandCompletion("@tracks")
    public void onRegionEnd(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (!WorldEditSelect.hasSelection(player)) {
                this.plugin.sendMessage(player, "te_no_selection", new String[0]);
            } else {
                WorldEditSelect.SelectionData data = WorldEditSelect.getSelectionData(player);
                if (data == null) {
                    this.plugin.sendMessage(player, "te_no_selection", new String[0]);
                    return;
                }
                int savedId = this.mysql.saveRegion(trackName, data.getMin(), data.getMax(), "END", data.getShape(), data.getPoints());
                if (savedId >= 0) {
                    this.plugin.sendMessage(player, "te_region_end_saved", new String[]{"{id}", String.valueOf(savedId)});
                    this.plugin.getRegionListener().reloadRegions();
                } else {
                    this.plugin.sendMessage(player, "te_region_error", new String[]{"{type}", "END"});
                }

            }
        }
    }

    @Subcommand("region remove|delete")
    @Description("Remove uma região específica pelo ID")
    @CommandCompletion("@nothing")
    public void onRegionRemove(Player player, int regionId) {
        boolean deleted = this.mysql.deleteRegionById(regionId);
        if (deleted) {
            player.sendMessage("§aRegião #" + regionId + " removida com sucesso!");
            this.plugin.getRegionListener().reloadRegions();
        } else {
            player.sendMessage("§cNão foi possível remover a região TEM ID: " + regionId);
        }

    }

    @Subcommand("region clear")
    @Description("Limpa todas as regiões de um tipo específico na pista")
    @CommandCompletion("@tracks @nothing")
    public void onRegionClear(Player player, @Optional String trackNameArg, String type) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            String trackWS = trackName.replaceAll("\\s+", "").toLowerCase();
            if (!type.equalsIgnoreCase("START") && !type.equalsIgnoreCase("END") && !type.equalsIgnoreCase("RESET")) {
                player.sendMessage("§cTipo inválido. Use: START, END ou RESET");
            } else {
                int count = 0;

                for(DatabaseManager.RegionData r : this.mysql.getAllRegions()) {
                    if (r.getTrackName().equalsIgnoreCase(trackWS) && r.getType().equalsIgnoreCase(type) && this.mysql.deleteRegionById(r.getId())) {
                        ++count;
                    }
                }

                if (count > 0) {
                    player.sendMessage("§aRemovidas " + count + " regiões do tipo " + type.toUpperCase() + " da pista " + trackName);
                    this.plugin.getRegionListener().reloadRegions();
                } else {
                    player.sendMessage("§cNenhuma região do tipo " + type.toUpperCase() + " encontrada para remover.");
                }

            }
        }
    }

    @Subcommand("region expand")
    @Description("Expandir regiões muito finas automaticamente")
    @CommandCompletion("@tracks")
    public void onRegionExpand(Player player, @Optional String trackFilter) {
        player.sendMessage("§e[REGION EXPAND] Expandindo regiões muito finas...");
        List<DatabaseManager.RegionData> allRegions = this.mysql.getAllRegions();
        int expanded = 0;

        for(DatabaseManager.RegionData region : allRegions) {
            String type = region.getType().toUpperCase();
            if ((type.equals("START") || type.equals("END")) && (trackFilter == null || region.getTrackName().toLowerCase().contains(trackFilter.toLowerCase()))) {
                double height = Math.abs(region.getMaxY() - region.getMinY());
                double depth = Math.abs(region.getMaxZ() - region.getMinZ());
                double width = Math.abs(region.getMaxX() - region.getMinX());
                boolean needsExpansion = false;
                double newMinY = region.getMinY();
                double newMaxY = region.getMaxY();
                double newMinZ = region.getMinZ();
                double newMaxZ = region.getMaxZ();
                if (height < (double)5.0F) {
                    needsExpansion = true;
                    double centerY = (region.getMinY() + region.getMaxY()) / (double)2.0F;
                    newMinY = centerY - (double)2.5F;
                    newMaxY = centerY + (double)2.5F;
                }

                if (depth < (double)3.0F) {
                    needsExpansion = true;
                    double centerZ = (region.getMinZ() + region.getMaxZ()) / (double)2.0F;
                    newMinZ = centerZ - (double)1.5F;
                    newMaxZ = centerZ + (double)1.5F;
                }

                if (needsExpansion && this.mysql.updateRegionBounds(region.getId(), region.getMinX(), newMinY, newMinZ, region.getMaxX(), newMaxY, newMaxZ)) {
                    player.sendMessage(String.format("§a  ✓ %s [%s] ID=%d", region.getTrackName(), type, region.getId()));
                    player.sendMessage(String.format("    §7Dimensões antigas: %.1f x %.1f x %.1f", width, height, depth));
                    player.sendMessage(String.format("    §aNovas dimensões: %.1f x %.1f x %.1f", width, Math.abs(newMaxY - newMinY), Math.abs(newMaxZ - newMinZ)));
                    ++expanded;
                }
            }
        }

        if (expanded > 0) {
            player.sendMessage(String.format("§a[REGION EXPAND] %d região(ões) expandida(s)!", expanded));
            this.plugin.getRegionListener().reloadRegions();
            player.sendMessage("§aRegiões recarregadas! Teste agora o time trial.");
        } else {
            player.sendMessage("§a[REGION EXPAND] Nenhuma região precisa ser expandida.");
        }

    }

    @Subcommand("region debug")
    @Description("Lista regiões carregadas na memória")
    @CommandCompletion("@tracks")
    public void onRegionDebug(Player player, @Optional String trackFilter) {
        this.plugin.getRegionListener().debugListRegions("world", trackFilter);
        player.sendMessage("§aVerifique o console do servidor para ver as regiões carregadas!");
    }

    @Subcommand("region cleanup")
    @Description("Limpa regiões duplicadas")
    @CommandCompletion("@tracks")
    public void onRegionCleanup(Player player, @Optional String specificTrack) {
        if (specificTrack != null) {
            player.sendMessage("§e[REGION CLEANUP] Limpando regiões duplicadas da pista: §f" + specificTrack);
        } else {
            player.sendMessage("§e[REGION CLEANUP] Limpando regiões duplicadas de §cTODAS§e as pistas...");
        }

        List<DatabaseManager.RegionData> allRegions = this.mysql.getAllRegions();
        Map<String, List<DatabaseManager.RegionData>> regionsByTrackAndType = new HashMap();
        String normalizedSpecificTrack = specificTrack != null ? specificTrack.replace(" ", "").toLowerCase() : null;

        for(DatabaseManager.RegionData region : allRegions) {
            String type = region.getType().toUpperCase();
            if ((type.equals("START") || type.equals("END")) && (normalizedSpecificTrack == null || region.getTrackName().equalsIgnoreCase(normalizedSpecificTrack))) {
                String var10000 = region.getTrackName().toLowerCase();
                String key = var10000 + "_" + type;
                ((List)regionsByTrackAndType.computeIfAbsent(key, (k) -> new ArrayList())).add(region);
            }
        }

        int totalDeleted = 0;
        int tracksProcessed = 0;

        for(Map.Entry<String, List<DatabaseManager.RegionData>> entry : regionsByTrackAndType.entrySet()) {
            List<DatabaseManager.RegionData> regions = (List)entry.getValue();
            if (regions.size() > 1) {
                regions.sort(Comparator.comparingInt(DatabaseManager.RegionData::getId).reversed());
                DatabaseManager.RegionData newest = (DatabaseManager.RegionData)regions.get(0);
                player.sendMessage(String.format("§e  Pista §f%s §e(%s): Mantendo região ID %d, deletando %d antiga(s)...", newest.getTrackName(), newest.getType(), newest.getId(), regions.size() - 1));

                for(int i = 1; i < regions.size(); ++i) {
                    DatabaseManager.RegionData old = (DatabaseManager.RegionData)regions.get(i);
                    this.mysql.deleteRegionById(old.getId());
                    ++totalDeleted;
                }

                ++tracksProcessed;
            }
        }

        if (totalDeleted > 0) {
            player.sendMessage(String.format("§a[REGION CLEANUP] Concluído! %d região(ões) duplicada(s) removida(s) de %d pista(s).", totalDeleted, tracksProcessed));
        } else if (specificTrack != null) {
            player.sendMessage("§a[REGION CLEANUP] Nenhuma região duplicada encontrada na pista §f" + specificTrack);
        } else {
            player.sendMessage("§a[REGION CLEANUP] Nenhuma região duplicada encontrada!");
        }

        this.plugin.getRegionListener().reloadRegions();
    }

    @Subcommand("info")
    @Description("Mostra informações de uma pista")
    @CommandCompletion("@tracks")
    public void onInfo(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            String trackNameWS = this.normalizeTrackName(trackName);
            player.sendMessage("§6§l========== Informações da Pista ==========");
            player.sendMessage("§e Nome: §f" + trackName);
            player.sendMessage("§e Nome Normalizado: §f" + trackNameWS);
            player.sendMessage("");
            List<DatabaseManager.RegionData> allRegions = this.mysql.getAllRegions();
            boolean foundStart = false;
            boolean foundEnd = false;
            player.sendMessage("§6§l[Regiões START/END]");

            for(DatabaseManager.RegionData region : allRegions) {
                if (region.getTrackName().equalsIgnoreCase(trackNameWS)) {
                    String type = region.getType();
                    if (type.equalsIgnoreCase("START")) {
                        foundStart = true;
                        String var10001 = String.format("%.1f, %.1f, %.1f", region.getMinX(), region.getMinY(), region.getMinZ());
                        player.sendMessage("§a✓ START: §7Min(" + var10001 + ") Max(" + String.format("%.1f, %.1f, %.1f", region.getMaxX(), region.getMaxY(), region.getMaxZ()) + ")");
                    } else if (type.equalsIgnoreCase("END")) {
                        foundEnd = true;
                        String var24 = String.format("%.1f, %.1f, %.1f", region.getMinX(), region.getMinY(), region.getMinZ());
                        player.sendMessage("§a✓ END: §7Min(" + var24 + ") Max(" + String.format("%.1f, %.1f, %.1f", region.getMaxX(), region.getMaxY(), region.getMaxZ()) + ")");
                    }
                }
            }

            if (!foundStart) {
                player.sendMessage("§c✗ START: Não configurada");
            }

            if (!foundEnd) {
                player.sendMessage("§c✗ END: Não configurada");
            }

            player.sendMessage("");
            List<DatabaseManager.RegionData> checkpoints = this.mysql.getCheckpoints(trackNameWS);
            player.sendMessage("§6§l[Checkpoints]");
            if (checkpoints.isEmpty()) {
                player.sendMessage("§c✗ Nenhum checkpoint configurado");
            } else {
                player.sendMessage("§a✓ Total de checkpoints: §f" + checkpoints.size());

                for(int i = 0; i < checkpoints.size(); ++i) {
                    DatabaseManager.RegionData cp = (DatabaseManager.RegionData)checkpoints.get(i);
                    double centerX = (cp.getMinX() + cp.getMaxX()) / (double)2.0F;
                    double centerY = (cp.getMinY() + cp.getMaxY()) / (double)2.0F;
                    double centerZ = (cp.getMinZ() + cp.getMaxZ()) / (double)2.0F;
                    player.sendMessage("  §7CP" + i + ": §f" + String.format("%.1f, %.1f, %.1f", centerX, centerY, centerZ));
                }
            }

            player.sendMessage("");
            player.sendMessage("§6§l[Diagnóstico]");
            if (foundStart && foundEnd) {
                DatabaseManager.RegionData startRegion = null;
                DatabaseManager.RegionData endRegion = null;

                for(DatabaseManager.RegionData region : allRegions) {
                    if (region.getTrackName().equalsIgnoreCase(trackNameWS)) {
                        if (region.getType().equalsIgnoreCase("START")) {
                            startRegion = region;
                        }

                        if (region.getType().equalsIgnoreCase("END")) {
                            endRegion = region;
                        }
                    }
                }

                if (startRegion != null && endRegion != null) {
                    boolean sameRegion = Math.abs(startRegion.getMinX() - endRegion.getMinX()) < 0.1 && Math.abs(startRegion.getMinY() - endRegion.getMinY()) < 0.1 && Math.abs(startRegion.getMinZ() - endRegion.getMinZ()) < 0.1 && Math.abs(startRegion.getMaxX() - endRegion.getMaxX()) < 0.1 && Math.abs(startRegion.getMaxY() - endRegion.getMaxY()) < 0.1 && Math.abs(startRegion.getMaxZ() - endRegion.getMaxZ()) < 0.1;
                    if (sameRegion) {
                        player.sendMessage("§a✓ START e END são a mesma região (correto para circuitos)");
                    } else {
                        player.sendMessage("§e⚠ START e END são regiões diferentes");
                    }
                }
            }

            if (checkpoints.isEmpty()) {
                player.sendMessage("§c✗ ERRO: Pista sem checkpoints!");
                player.sendMessage("§7   Use: /trackedit checkpoint add <id> para adicionar");
            }

            player.sendMessage("§6§l==========================================");
        }
    }

    @Subcommand("location tp_finish_all")
    @Description("Define o local de teleporte para TODOS ao fim da corrida")
    @CommandCompletion("@tracks")
    public void onTpFinishAll(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            String trackNameWS = this.normalizeTrackName(trackName);
            this.mysql.setTrackFinishAll(trackNameWS, player.getLocation());
            player.sendMessage("§aLocal de teleporte final (ALL) definido para a pista " + trackName);
        }
    }

    @Subcommand("location tp_finish_pos")
    @Description("Define o local de teleporte para uma posição específica")
    @CommandCompletion("@nothing @tracks")
    public void onTpFinishPos(Player player, int position, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            String trackNameWS = this.normalizeTrackName(trackName);
            this.mysql.setTrackFinishPos(trackNameWS, position, player.getLocation());
            player.sendMessage("§aLocal de teleporte para a Posição #" + position + " definido para a pista " + trackName);
        }
    }

    @Subcommand("spawn")
    @Description("Define o spawn de uma pista")
    @CommandCompletion("@tracks")
    public void onSpawn(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            String trackNameWS = this.normalizeTrackName(trackName);
            this.plugin.getDebugManager().logRaceSystem("Salvando spawn da pista '" + trackName + "' (trackNameWS='" + trackNameWS + "') para o jogador " + player.getName());
            player.sendMessage("§eSalvando spawn da pista: §f" + trackName + " §7(normalizado: §f" + trackNameWS + "§7)");
            this.mysql.setTrackSpawn(trackName, player.getLocation());
            this.plugin.sendMessage(player, "te_spawn_saved", new String[0]);
        }
    }

    @Subcommand("checkpoint add")
    @Description("Adiciona um checkpoint")
    @CommandCompletion("@nothing @tracks")
    public void onCheckpointAdd(Player player, int id, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            String trackNameWS = this.normalizeTrackName(trackName);
            WorldEditSelect var10000 = this.worldEditSelect;
            if (!WorldEditSelect.hasSelection(player)) {
                player.sendMessage("§cVocê precisa fazer uma seleção com o WorldEdit para adicionar o checkpoint.");
            } else {
                Location min = WorldEditSelect.getMin(player);
                Location max = WorldEditSelect.getMax(player);
                String worldName = player.getWorld().getName();
                boolean success = this.mysql.addCheckpoint(id, trackNameWS, player);
                if (success) {
                    this.plugin.sendMessage(player, "te_checkpoint_added", new String[]{"{id}", String.valueOf(id), "{track}", trackName});
                    this.plugin.getDebugManager().logDatabaseOperations("[FormulaRacing] === Salvando Checkpoint ===");
                    this.plugin.getDebugManager().logDatabaseOperations("[FormulaRacing] Track: " + trackName);
                    this.plugin.getDebugManager().logDatabaseOperations("[FormulaRacing] ID: " + id);
                    this.plugin.getDebugManager().logDatabaseOperations("[FormulaRacing] Mundo: " + worldName);
                    DebugManager var10 = this.plugin.getDebugManager();
                    double var10001 = min.getX();
                    var10.logDatabaseOperations("[FormulaRacing] Min: X=" + var10001 + " Y=" + min.getY() + " Z=" + min.getZ());
                    var10 = this.plugin.getDebugManager();
                    var10001 = max.getX();
                    var10.logDatabaseOperations("[FormulaRacing] Max: X=" + var10001 + " Y=" + max.getY() + " Z=" + max.getZ());
                    this.plugin.getDebugManager().logDatabaseOperations("[FormulaRacing] =======================");
                } else {
                    player.sendMessage("§cErro ao adicionar o checkpoint " + id);
                }

            }
        }
    }

    @Subcommand("checkpoint remove")
    @Description("Remove um checkpoint por checkpointId")
    @CommandCompletion("@nothing @tracks")
    public void onCheckpointRemove(Player player, int id, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            String trackNameWS = this.normalizeTrackName(trackName);
            boolean success = this.mysql.removeCheckpoint(trackNameWS, id);
            if (success) {
                this.plugin.sendMessage(player, "te_checkpoint_removed", new String[]{"{id}", String.valueOf(id), "{track}", trackName});
            } else {
                this.plugin.sendMessage(player, "te_checkpoint_error", new String[]{"{id}", String.valueOf(id)});
            }

        }
    }

    @Subcommand("checkpoint removeid")
    @Description("Remove um checkpoint por ID da base de dados (para remover duplicados)")
    @CommandCompletion("@nothing")
    public void onCheckpointRemoveById(Player player, int dbId) {
        boolean success = this.mysql.removeCheckpointById(dbId);
        if (success) {
            this.plugin.sendMessage(player, "te_checkpoint_removed", new String[]{"{id}", String.valueOf(dbId), "{track}", ""});
            this.plugin.getTrackIntegrationManager().clearCheckpointCache(null);
        } else {
            this.plugin.sendMessage(player, "te_checkpoint_error", new String[]{"{id}", String.valueOf(dbId)});
        }
    }

    @Subcommand("time")
    @Description("Define o tempo de uma pista (Ticks)")
    @CommandCompletion("@nothing @tracks")
    public void onTime(Player player, long ticks, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            this.plugin.sendMessage(player, "te_time_ticks", new String[]{"{track}", trackName, "{ticks}", String.valueOf(ticks)});
        }
    }

    @Subcommand("icon")
    @Description("Define o ícone de uma pista")
    @CommandCompletion("@nothing @tracks")
    public void onIcon(Player player, String materialName, @Optional String trackNameArg) {
        Material iconMat;
        try {
            iconMat = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException var6) {
            player.sendMessage("§cMaterial inválido: " + materialName);
            return;
        }

        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (this.mysql.setTrackIcon(trackName, iconMat.name())) {
                this.plugin.sendMessage(player, "te_icon_updated", new String[]{"{track}", trackName, "{icon}", iconMat.name()});
            } else {
                player.sendMessage("§cErro ao atualizar ícone da pista.");
            }

        }
    }

    @Subcommand("open")
    @Description("Abre uma pista para uso")
    @CommandCompletion("@tracks")
    public void onOpen(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (this.mysql.isTrackOpen(trackName)) {
                this.plugin.sendMessage(player, "te_track_already_open", new String[]{"{track}", trackName});
            } else {
                this.mysql.setTrackOpen(trackName, true);
                this.plugin.sendMessage(player, "te_track_opened", new String[]{"{track}", trackName});
            }
        }
    }

    @Subcommand("close")
    @Description("Fecha uma pista para uso")
    @CommandCompletion("@tracks")
    public void onClose(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (!this.mysql.isTrackOpen(trackName)) {
                this.plugin.sendMessage(player, "te_track_already_closed", new String[]{"{track}", trackName});
            } else {
                this.mysql.setTrackOpen(trackName, false);
                this.plugin.sendMessage(player, "te_track_closed", new String[]{"{track}", trackName});
            }
        }
    }

    @Subcommand("pitstop")
    @Description("Configurações de Pit Stop")
    @CommandCompletion("addentry|addexit|remove|info|edit|list|check @tracks")
    public void onPitStop(Player player, String action, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            String trackNameWS = this.normalizeTrackName(trackName);
            switch (action.toLowerCase()) {
                case "edit":
                    (new PitStopEditorGui(this.plugin, trackNameWS, player)).show(player);
                    break;
                case "info":
                    PitStopRegion region = this.plugin.getPitStopManager().getPitStop(trackNameWS);
                    if (region == null) {
                        this.plugin.sendMessage(player, "te_pitstop_none", new String[]{"{track}", trackName});
                    } else {
                        this.plugin.sendMessage(player, "te_pitstop_configured", new String[]{"{track}", trackName});
                    }
                    break;
                case "addentry":
                    WorldEditSelect var19 = this.worldEditSelect;
                    if (!WorldEditSelect.hasSelection(player)) {
                        player.sendMessage("§cFaça uma seleção com WorldEdit.");
                        return;
                    }

                    PitStopManager var20 = this.plugin.getPitStopManager();
                    WorldEditSelect var27 = this.worldEditSelect;
                    Location var28 = WorldEditSelect.getMin(player);
                    WorldEditSelect var29 = this.worldEditSelect;
                    var20.addPitStopEntry(trackNameWS, var28, WorldEditSelect.getMax(player));
                    this.plugin.sendMessage(player, "te_pitstop_entry_set", new String[]{"{track}", trackName});
                    break;
                case "addexit":
                    WorldEditSelect var10000 = this.worldEditSelect;
                    if (!WorldEditSelect.hasSelection(player)) {
                        player.sendMessage("§cFaça uma seleção com WorldEdit.");
                        return;
                    }

                    PitStopManager var18 = this.plugin.getPitStopManager();
                    WorldEditSelect var10002 = this.worldEditSelect;
                    Location var26 = WorldEditSelect.getMin(player);
                    WorldEditSelect var10003 = this.worldEditSelect;
                    var18.addPitStopExit(trackNameWS, var26, WorldEditSelect.getMax(player));
                    this.plugin.sendMessage(player, "te_pitstop_exit_set", new String[]{"{track}", trackName});
                    break;
                case "remove":
                    if (this.plugin.getPitStopManager().removePitStop(trackNameWS)) {
                        this.plugin.sendMessage(player, "te_pitstop_removed", new String[]{"{track}", trackName});
                    } else {
                        this.plugin.sendMessage(player, "te_pitstop_none_remove", new String[0]);
                    }
                    break;
                case "list":
                    Set<String> tracks = this.plugin.getPitStopManager().getLoadedTracks();
                    if (tracks.isEmpty()) {
                        this.plugin.sendMessage(player, "te_pitstop_empty_list", new String[0]);
                    } else {
                        this.plugin.sendMessage(player, "te_pitstop_list", new String[]{"{count}", String.valueOf(tracks.size()), "{tracks}", String.join(", ", tracks)});
                    }
                    break;
                case "check":
                    Location loc = player.getLocation();
                    int var10001 = loc.getBlockX();
                    player.sendMessage("§eVerificando localização: " + var10001 + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + " (" + loc.getWorld().getName() + ")");
                    String entry = this.plugin.getPitStopManager().getPitStopEntryAtLocation(loc);
                    String var21 = entry == null ? "§cNenhuma" : "§a" + entry;
                    player.sendMessage("§7Entry Region: " + var21);
                    String exit = this.plugin.getPitStopManager().getPitStopExitAtLocation(loc);
                    var21 = exit == null ? "§cNenhuma" : "§a" + exit;
                    player.sendMessage("§7Exit Region: " + var21);
                    String area = this.plugin.getPitStopManager().getPitAreaAtLocation(loc);
                    var21 = area == null ? "§cNenhuma" : "§a" + area;
                    player.sendMessage("§7Pit Area: " + var21);
                    boolean overBlock = this.plugin.getPitStopManager().isOverPitBlock(loc);
                    player.sendMessage("§7Pit Block: " + (overBlock ? "§aSIM (TERRACOTTA)" : "§cNÃO"));
                    if (trackName != null) {
                        PitStopRegion region1 = this.plugin.getPitStopManager().getPitStop(trackNameWS);
                        if (region1 != null) {
                            player.sendMessage("§eRegião " + trackName + " (Entry):");
                            if (region1.hasEntry()) {
                                Location min = region1.getEntryRegion().getMin();
                                Location max = region1.getEntryRegion().getMax();
                                int var24 = min.getBlockX();
                                player.sendMessage("  §7Min: " + var24 + "," + min.getBlockY() + "," + min.getBlockZ());
                                var24 = max.getBlockX();
                                player.sendMessage("  §7Max: " + var24 + "," + max.getBlockY() + "," + max.getBlockZ());
                            } else {
                                player.sendMessage("  §cNão definida.");
                            }
                        }
                    }
                    break;
                default:
                    player.sendMessage("§cAção desconhecida. Use addentry, addexit, remove, info, edit, list ou check.");
            }

        }
    }

    @Subcommand("boatutils reset")
    @Description("Reseta todas as configurações BoatUtils de uma pista para o padrão")
    @CommandCompletion("@tracks")
    public void onBoatUtilsReset(Player player, String track) {
        String trackName = track.replace(" ", "");
        this.mysql.resetBoatUtilsSettings(trackName);
        player.sendMessage("§a✔ Configurações BoatUtils resetadas para §fVanilla §ana pista §e" + trackName);
    }

    @Subcommand("boatutils set group")
    @Description("Aplica um preset de configurações (modo de grupo)")
    @CommandCompletion("@boatutils_groups @tracks")
    public void onBoatUtilsSetGroup(Player player, BoatUtilsGroupMode mode, String track) {
        String trackName = track.replace(" ", "").toLowerCase();
        this.applyGroupMode(trackName, mode);
        String var10001 = mode.name();
        player.sendMessage("§a✔ Modo de grupo §b" + var10001 + " §aplicado com sucesso na pista §e" + trackName);
    }

    @Subcommand("boatutils set config")
    @Description("Configura um valor específico do BoatUtils")
    @CommandCompletion("@boatutils_settings @nothing @tracks")
    public void onBoatUtilsSetConfig(Player player, String setting, String value, @Optional String trackArg) {
        String trackName = this.getTargetTrack(player, trackArg);
        if (trackName != null) {
            trackName = trackName.replace(" ", "");
            String key = setting.toLowerCase();

            try {
                switch (key) {
                    case "defaultslipperiness":
                        this.mysql.setDefaultSlipperiness(trackName, (double)Float.parseFloat(value));
                        break;
                    case "jumpforce":
                        this.mysql.setJumpForce(trackName, (double)Float.parseFloat(value));
                        break;
                    case "yawacceleration":
                        this.mysql.setYawAcceleration(trackName, (double)Float.parseFloat(value));
                        break;
                    case "forwardacceleration":
                        this.mysql.setForwardAcceleration(trackName, (double)Float.parseFloat(value));
                        break;
                    case "backwardacceleration":
                        this.mysql.setBackwardAcceleration(trackName, (double)Float.parseFloat(value));
                        break;
                    case "turningforwardacceleration":
                        this.mysql.setTurningForwardAcceleration(trackName, (double)Float.parseFloat(value));
                        break;
                    case "swimforce":
                        this.mysql.setSwimForce(trackName, (double)Float.parseFloat(value));
                        break;
                    case "stepheight":
                        this.mysql.setStepHigh(trackName, Double.parseDouble(value));
                        break;
                    case "gravity":
                        this.mysql.setGravity(trackName, Double.parseDouble(value));
                        break;
                    case "falldamage":
                        this.mysql.setFallDamage(trackName, this.parseBoolean(value));
                        break;
                    case "waterelevation":
                        this.mysql.setWaterElevation(trackName, this.parseBoolean(value));
                        break;
                    case "aircontrol":
                        this.mysql.setAirControl(trackName, this.parseBoolean(value));
                        break;
                    case "allowaccelerationstacking":
                        this.mysql.setAllowAccelerationStacking(trackName, this.parseBoolean(value));
                        break;
                    case "underwatercontrol":
                        this.mysql.setUnderwaterControl(trackName, this.parseBoolean(value));
                        break;
                    case "surfacewatercontrol":
                        this.mysql.setSurfaceWaterControl(trackName, this.parseBoolean(value));
                        break;
                    case "waterjumping":
                        this.mysql.setWaterJumping(trackName, this.parseBoolean(value));
                        break;
                    case "airstepping":
                        this.mysql.setAirStepping(trackName, this.parseBoolean(value));
                        break;
                    case "tenstepinterpolation":
                        this.mysql.setTenStepInterpolation(trackName, this.parseBoolean(value));
                        break;
                    case "collisionmode":
                        this.mysql.setCollisionMode(trackName, Integer.parseInt(value));
                        break;
                    case "collisionresolution":
                        this.mysql.setCollisionResolution(trackName, Integer.parseInt(value));
                        break;
                    case "coyotetime":
                        this.mysql.setCoyoteTime(trackName, Integer.parseInt(value));
                        break;
                    default:
                        player.sendMessage("§c✘ Configuração desconhecida: §f" + setting);
                        return;
                }

                player.sendMessage("§a✔ Configuração §e" + setting + " §adefinida para §b" + value + " §ana pista §f" + trackName);
            } catch (NumberFormatException var9) {
                player.sendMessage("§c✘ O valor '§f" + value + "§c' não é válido para §f" + setting);
            }

        }
    }

    @Subcommand("boatutils set customslipperiness add")
    @Description("Define a aderência personalizada para um tipo de bloco")
    @CommandCompletion("@materials @nothing @tracks")
    public void onBoatUtilsAddSlipperiness(Player player, String materialName, float value, @Optional String trackArg) {
        String trackName = this.getTargetTrack(player, trackArg);
        if (trackName != null) {
            trackName = trackName.replace(" ", "");
            Material mat = Material.matchMaterial(materialName);
            if (mat == null) {
                player.sendMessage("§c✘ Bloco inválido: §f" + materialName);
            } else {
                String blockId = mat.getKey().toString();
                this.mysql.addCustomSlipperiness(trackName, blockId, value);
                player.sendMessage("§a✔ Slipperiness de §e" + mat.name() + " §adefinido para §b" + value + " §ana pista §f" + trackName);
            }
        }
    }

    @Subcommand("boatutils set customslipperiness reset")
    @Description("Remove todas as customizações de aderência de blocos")
    @CommandCompletion("@tracks")
    public void onBoatUtilsResetSlipperiness(Player player, @Optional String trackArg) {
        String trackName = this.getTargetTrack(player, trackArg);
        if (trackName != null) {
            trackName = trackName.replace(" ", "");
            this.mysql.resetCustomSlipperiness(trackName);
            player.sendMessage("§a✔ Custom Slipperiness resetado na pista §e" + trackName);
        }
    }

    private boolean parseBoolean(String value) {
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("1") || value.equalsIgnoreCase("yes");
    }

    @Subcommand("grid add")
    @Description("Adiciona uma posição de grid")
    @CommandCompletion("@nothing @tracks")
    public void onGridAdd(Player player, int id, @Optional String trackNameArg) {
        if (id >= 1 && id <= 200) {
            String trackName = this.getTargetTrack(player, trackNameArg);
            if (trackName != null) {
                if (this.mysql.addGridPosition(trackName, id, player.getLocation())) {
                    player.sendMessage("§a✓ Posição de grid P" + id + " adicionada em §e" + trackName);
                    String var10001 = this.formatLocation(player.getLocation());
                    player.sendMessage("§7Localização: " + var10001);
                } else {
                    player.sendMessage("§c✗ Erro ao adicionar a posição de grid.");
                }

            }
        } else {
            player.sendMessage("§cPosição deve estar entre 1 e 200.");
        }
    }

    @Subcommand("grid remove")
    @Description("Remove uma posição de grid")
    @CommandCompletion("@nothing @tracks")
    public void onGridRemove(Player player, int id, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (this.mysql.removeGridPosition(trackName, id)) {
                player.sendMessage("§a✓ Posição de grid P" + id + " removida de §e" + trackName);
            } else {
                player.sendMessage("§c✗ Erro ao remover a posição de grid.");
            }

        }
    }

    @Subcommand("grid clear")
    @Description("Limpa todas as posições de grid")
    @CommandCompletion("@tracks confirm")
    public void onGridClear(Player player, String trackName, @Optional String confirm) {
        if (confirm != null && confirm.equalsIgnoreCase("confirm")) {
            if (this.mysql.clearGridPositions(trackName)) {
                player.sendMessage("§a✓ Todas as posições de grid foram removidas de §e" + trackName);
            } else {
                player.sendMessage("§c✗ Erro ao limpar o grid.");
            }

        } else {
            int count = this.mysql.getGridPositions(trackName).size();
            player.sendMessage("§e⚠ Isso removerá §c" + count + " posições §ede grid!");
            player.sendMessage("§7Use §f/trackedit grid clear " + trackName + " confirm §7para confirmar.");
        }
    }

    @Subcommand("grid list")
    @Description("Lista as posições de grid")
    @CommandCompletion("@tracks")
    public void onGridList(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            List<GridPosition> positions = this.mysql.getGridPositions(trackName);
            player.sendMessage("§e═══════════════════════════════════");
            player.sendMessage("§6§lGrid de §f" + trackName);
            player.sendMessage("§e═══════════════════════════════════");
            if (positions.isEmpty()) {
                player.sendMessage("§7Nenhuma posição configurada.");
                player.sendMessage("§7Use §f/trackedit grid add <id> " + trackName + " §7para adicionar.");
            } else {
                player.sendMessage("§7Total: §f" + positions.size() + " posições");
                player.sendMessage("");

                for(GridPosition pos : positions) {
                    Location loc = pos.toLocation(this.plugin.getServer());
                    if (loc != null) {
                        int var10001 = pos.getPosition();
                        player.sendMessage("§6P" + var10001 + " §8→ §7" + this.formatLocation(loc));
                    }
                }
            }

            player.sendMessage("§e═══════════════════════════════════");
        }
    }

    @Subcommand("grid test")
    @Description("Testa o grid de uma pista")
    @CommandCompletion("@tracks")
    public void onGridTest(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            List<GridPosition> positions = this.mysql.getGridPositions(trackName);
            if (positions.isEmpty()) {
                player.sendMessage("§cNenhuma posição de grid configurada para esta pista.");
            } else {
                player.sendMessage("§a✓ Testando grid de §e" + trackName + "§a...");
                player.sendMessage("§7Você será teleportado para cada posição (1 segundo entre cada).");

                for(int i = 0; i < positions.size(); ++i) {
                    GridPosition pos = (GridPosition)positions.get(i);
                    int delay = i * 20;
                    this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                        Location loc = pos.toLocation(this.plugin.getServer());
                        if (loc != null) {
                            player.teleport(loc);
                            TitleHelper.sendThemedTitle(player, "&wP" + pos.getPosition(), "§7" + this.formatLocation(loc), 5, 30, 10);
                        }

                    }, (long)delay);
                }

            }
        }
    }

    @Subcommand("grid info")
    @Description("Mostra informações do grid")
    @CommandCompletion("@tracks")
    public void onGridInfo(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            List<GridPosition> positions = this.mysql.getGridPositions(trackName);
            DatabaseManager.TrackData trackData = this.mysql.getTrackData(trackName);
            player.sendMessage("§e═══════════════════════════════════");
            player.sendMessage("§6§lInformações de Grid: §f" + trackName);
            player.sendMessage("§e═══════════════════════════════════");
            player.sendMessage("");
            player.sendMessage("§7Pista: §f" + trackName);
            if (trackData != null) {
                player.sendMessage("§7Checkpoints: §f" + trackData.getTotalCheckpoints());
            }

            player.sendMessage("§7Posições configuradas: §f" + positions.size());
            player.sendMessage("");
            if (positions.isEmpty()) {
                player.sendMessage("§e⚠ Grid não configurado");
                player.sendMessage("§7O sistema usará geração automática.");
                player.sendMessage("§7Use §f/trackedit grid add <id> " + trackName + " §7para configurar.");
            } else {
                player.sendMessage("§a✓ Grid configurado manualmente");
                player.sendMessage("§7Use §f/trackedit grid list " + trackName + " §7para ver posições.");
                player.sendMessage("§7Use §f/trackedit grid test " + trackName + " §7para testar.");
            }

            player.sendMessage("§e═══════════════════════════════════");
        }
    }

    @Subcommand("location qualigrid")
    @Description("Adiciona/atualiza uma posição do qualigrid (grid da qualificatória)")
    @CommandCompletion("@nothing @tracks")
    public void onLocationQualiGrid(Player player, int id, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (this.mysql.addQualiGridPosition(trackName, id, player.getLocation())) {
                player.sendMessage("§a✓ Posição Q" + id + " do qualigrid adicionada em §e" + trackName);
                player.sendMessage("§7Localização: " + this.formatLocation(player.getLocation()));
            } else {
                player.sendMessage("§c✗ Erro ao adicionar posição do qualigrid.");
            }
        }
    }

    @Subcommand("qualigrid remove")
    @Description("Remove uma posição do qualigrid")
    @CommandCompletion("@nothing @tracks")
    public void onQualiGridRemove(Player player, int id, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (this.mysql.removeQualiGridPosition(trackName, id)) {
                player.sendMessage("§a✓ Posição Q" + id + " do qualigrid removida de §e" + trackName);
            } else {
                player.sendMessage("§c✗ Erro ao remover posição do qualigrid.");
            }
        }
    }

    @Subcommand("qualigrid clear")
    @Description("Limpa todas as posições do qualigrid")
    @CommandCompletion("@tracks confirm")
    public void onQualiGridClear(Player player, String trackName, @Optional String confirm) {
        if (confirm != null && confirm.equalsIgnoreCase("confirm")) {
            if (this.mysql.clearQualiGridPositions(trackName)) {
                player.sendMessage("§a✓ Todas as posições do qualigrid foram removidas de §e" + trackName);
            } else {
                player.sendMessage("§c✗ Erro ao limpar o qualigrid.");
            }
        } else {
            int count = this.mysql.getQualiGridPositions(trackName).size();
            player.sendMessage("§e⚠ Isso removerá §c" + count + " posições §edo qualigrid!");
            player.sendMessage("§7Use §f/trackedit qualigrid clear " + trackName + " confirm §7para confirmar.");
        }
    }

    @Subcommand("qualigrid list")
    @Description("Lista as posições do qualigrid")
    @CommandCompletion("@tracks")
    public void onQualiGridList(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            List<GridPosition> positions = this.mysql.getQualiGridPositions(trackName);
            player.sendMessage("§e═══════════════════════════════════");
            player.sendMessage("§6§lQualigrid de §f" + trackName);
            player.sendMessage("§e═══════════════════════════════════");
            if (positions.isEmpty()) {
                player.sendMessage("§7Nenhuma posição configurada.");
                player.sendMessage("§7Use §f/trackedit location qualigrid <id> " + trackName + " §7para adicionar.");
            } else {
                player.sendMessage("§7Total: §f" + positions.size() + " posições");
                player.sendMessage("");
                for (GridPosition pos : positions) {
                    Location loc = pos.toLocation(this.plugin.getServer());
                    if (loc != null) {
                        player.sendMessage("§6Q" + pos.getPosition() + " §8→ §7" + this.formatLocation(loc));
                    }
                }
            }
            player.sendMessage("§e═══════════════════════════════════");
        }
    }

    @Subcommand("qualigrid test")
    @Description("Testa as posições do qualigrid")
    @CommandCompletion("@tracks")
    public void onQualiGridTest(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            List<GridPosition> positions = this.mysql.getQualiGridPositions(trackName);
            if (positions.isEmpty()) {
                player.sendMessage("§cNenhuma posição do qualigrid configurada para esta pista.");
            } else {
                player.sendMessage("§a✓ Testando qualigrid de §e" + trackName + "§a...");
                for (int i = 0; i < positions.size(); ++i) {
                    GridPosition pos = positions.get(i);
                    int delay = i * 20;
                    this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                        Location loc = pos.toLocation(this.plugin.getServer());
                        if (loc != null) {
                            player.teleport(loc);
                            TitleHelper.sendThemedTitle(player, "&wQ" + pos.getPosition(), "§7" + this.formatLocation(loc), 5, 30, 10);
                        }
                    }, (long)delay);
                }
            }
        }
    }

    @Subcommand("qualigrid info")
    @Description("Mostra informações do qualigrid")
    @CommandCompletion("@tracks")
    public void onQualiGridInfo(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            List<GridPosition> positions = this.mysql.getQualiGridPositions(trackName);
            player.sendMessage("§e═══════════════════════════════════");
            player.sendMessage("§6§lInformações do Qualigrid: §f" + trackName);
            player.sendMessage("§e═══════════════════════════════════");
            player.sendMessage("§7Posições configuradas: §f" + positions.size());
            if (positions.isEmpty()) {
                player.sendMessage("§e⚠ Qualigrid não configurado");
                player.sendMessage("§7A qualificatória usará o spawn da pista.");
                player.sendMessage("§7Use §f/trackedit location qualigrid <id> " + trackName + " §7para configurar.");
            } else {
                player.sendMessage("§a✓ Qualigrid configurado manualmente");
                player.sendMessage("§7Use §f/trackedit qualigrid list " + trackName + " §7para ver posições.");
                player.sendMessage("§7Use §f/trackedit qualigrid test " + trackName + " §7para testar.");
            }
            player.sendMessage("§e═══════════════════════════════════");
        }
    }

    @Subcommand("pitstop entry")
    @Description("Adiciona a entrada do pit stop")
    @CommandCompletion("@tracks")
    public void onPitStopEntry(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            WorldEditSelect var10000 = this.worldEditSelect;
            if (!WorldEditSelect.hasSelection(player)) {
                player.sendMessage("§c✗ Faça uma seleção com WorldEdit primeiro!");
            } else {
                Location min = WorldEditSelect.getMin(player);
                Location max = WorldEditSelect.getMax(player);
                this.plugin.getPitStopManager().addPitStopEntry(trackName, min, max);
                player.sendMessage("§a✓ Entrada do pit stop definida para §e" + trackName + "§a!");
                String var10001 = this.formatLocation(min);
                player.sendMessage("§7Região: " + var10001 + " §7→ " + this.formatLocation(max));
            }
        }
    }

    @Subcommand("pitstop exit")
    @Description("Adiciona a saída do pit stop")
    @CommandCompletion("@tracks")
    public void onPitStopExit(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            WorldEditSelect var10000 = this.worldEditSelect;
            if (!WorldEditSelect.hasSelection(player)) {
                player.sendMessage("§c✗ Faça uma seleção com WorldEdit primeiro!");
            } else {
                Location min = WorldEditSelect.getMin(player);
                Location max = WorldEditSelect.getMax(player);
                this.plugin.getPitStopManager().addPitStopExit(trackName, min, max);
                player.sendMessage("§a✓ Saída do pit stop definida para §e" + trackName + "§a!");
                String var10001 = this.formatLocation(min);
                player.sendMessage("§7Região: " + var10001 + " §7→ " + this.formatLocation(max));
            }
        }
    }

    @Subcommand("pitstop area")
    @Description("Adiciona a AREA do pit stop (Minigame Zone)")
    @CommandCompletion("@tracks")
    public void onPitStopArea(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            WorldEditSelect var10000 = this.worldEditSelect;
            if (!WorldEditSelect.hasSelection(player)) {
                player.sendMessage("§c✗ Faça uma seleção com WorldEdit primeiro!");
            } else {
                Location min = WorldEditSelect.getMin(player);
                Location max = WorldEditSelect.getMax(player);
                this.plugin.getPitStopManager().addPitStopArea(trackName, min, max);
                player.sendMessage("§a✓ Área do pit stop (Minigame) definida para §e" + trackName + "§a!");
                String var10001 = this.formatLocation(min);
                player.sendMessage("§7Região: " + var10001 + " §7→ " + this.formatLocation(max));
                player.sendMessage("§eℹ Coloque blocos de YELLOW_GLAZED_TERRACOTTA dentro desta área para ativar o minigame.");
            }
        }
    }

    @Subcommand("pitstop start")
    @Description("Define a linha de contagem de volta (START) dentro do corredor do pit")
    @CommandCompletion("@tracks")
    public void onPitStopStart(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            WorldEditSelect var10000 = this.worldEditSelect;
            if (!WorldEditSelect.hasSelection(player)) {
                player.sendMessage("§c✗ Faça uma seleção com WorldEdit primeiro!");
            } else {
                Location min = WorldEditSelect.getMin(player);
                Location max = WorldEditSelect.getMax(player);
                this.plugin.getPitStopManager().addPitStopStart(trackName, min, max);
                player.sendMessage("§a✓ Linha de contagem de volta (§eSTART§a) definida para §e" + trackName + "§a!");
                String var10001 = this.formatLocation(min);
                player.sendMessage("§7Localização: " + var10001 + " §7→ " + this.formatLocation(max));
                player.sendMessage("§bℹ Esta região validará a volta do piloto quando ele passar por ela dentro do pit.");
            }
        }
    }

    @Subcommand("pitstop remove")
    @Description("Remove o pit stop de uma pista")
    @CommandCompletion("@tracks")
    public void onPitStopRemove(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (!this.plugin.getPitStopManager().hasPitStop(trackName)) {
                player.sendMessage("§c✗ A pista §f" + trackName + " §cnão tem pit stop region configurada.");
            } else {
                if (this.plugin.getPitStopManager().removePitStop(trackName)) {
                    player.sendMessage("§a✓ Pit stop region removida de §e" + trackName + "§a!");
                } else {
                    player.sendMessage("§c✗ Erro ao remover pit stop region.");
                }

            }
        }
    }

    @Subcommand("drs detect")
    @Description("Define a região de detecção do DRS")
    @CommandCompletion("@tracks")
    public void onDrsDetect(Player player, @Optional String trackNameArg) {
        this.saveDrsPart(player, "DETECT", trackNameArg);
    }

    @Subcommand("drs startdrs")
    @Description("Define a região de ativação do DRS")
    @CommandCompletion("@tracks")
    public void onDrsStart(Player player, @Optional String trackNameArg) {
        this.saveDrsPart(player, "START", trackNameArg);
    }

    @Subcommand("drs finishdrs")
    @Description("Define a região de desativação do DRS")
    @CommandCompletion("@tracks")
    public void onDrsFinish(Player player, @Optional String trackNameArg) {
        this.saveDrsPart(player, "FINISH", trackNameArg);
    }

    @Subcommand("drs delete detect")
    @Description("Remove uma região de detecção pelo ID")
    public void onDeleteDetect(Player player, Integer id) {
        this.deleteDrsById(player, id, "DETECT");
    }

    @Subcommand("drs delete startdrs")
    @Description("Remove uma região de ativação pelo ID")
    public void onDeleteStart(Player player, Integer id) {
        this.deleteDrsById(player, id, "START");
    }

    @Subcommand("drs delete finishdrs")
    @Description("Remove uma região de desativação pelo ID")
    public void onDeleteFinish(Player player, Integer id) {
        this.deleteDrsById(player, id, "FINISH");
    }

    private void deleteDrsById(Player player, int id, String type) {
        // Chamamos o MySQL passando o ID único da linha
        if (this.mysql.deleteDRSRegionByID(id)) {
            player.sendMessage("§a[DRS] Região ID §f#" + id + " (§e" + type + "§a) removida com sucesso!");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0F, 2.0F);
        } else {
            player.sendMessage("§c[DRS] Não foi possível encontrar uma região com o ID §f#" + id);
        }
    }

    private void saveDrsPart(Player player, String type, String trackArg) {
        String trackName = this.getTargetTrack(player, trackArg);
        if (trackName == null) return;

        if (!WorldEditSelect.hasSelection(player)) {
            this.plugin.sendMessage(player, "te_no_selection");
            return;
        }

        Location min = WorldEditSelect.getMin(player);
        Location max = WorldEditSelect.getMax(player);

        // Agora enviamos para o MySQL os dados para a nova tabela fr_drs
        // Note que passamos o 'type' para o banco saber qual parte da linha atualizar/inserir
        if (this.mysql.saveDrsZone(trackName, type, min, max)) {
            player.sendMessage("§a[DRS] Parte §f" + type.toUpperCase() + " §adefinida para a pista: §e" + trackName);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
        } else {
            player.sendMessage("§cErro ao salvar a região de DRS no banco de dados.");
        }
    }

    @Subcommand("pitstop list")
    @Description("Lista pistas com pit stop")
    public void onPitStopList(Player player) {
        Set<String> tracks = this.plugin.getPitStopManager().getTracksWithPitStop();
        if (tracks.isEmpty()) {
            player.sendMessage("§e⚠ Nenhuma pista tem pit stop region configurada.");
        } else {
            player.sendMessage("§e═══════════════════════════════════");
            player.sendMessage("§6§lPistas com Pit Stop Region (" + tracks.size() + ")");
            player.sendMessage("§e═══════════════════════════════════");

            for(String track : tracks) {
                PitStopRegion pitStop = this.plugin.getPitStopManager().getPitStop(track);
                String status = pitStop.hasEntry() && pitStop.hasExit() ? "§a[E+S]" : (pitStop.hasEntry() ? "§e[E]" : "§c[S]");
                player.sendMessage(String.format("§a▪ §f%s %s", track, status));
            }

            player.sendMessage("§e═══════════════════════════════════");
        }
    }

    @Subcommand("pitstop info")
    @Description("Mostra informações do pit stop")
    @CommandCompletion("@tracks")
    public void onPitStopInfo(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (!this.plugin.getPitStopManager().hasPitStop(trackName)) {
                player.sendMessage("§c✗ A pista §f" + trackName + " §cnão tem pit stop region configurada.");
            } else {
                PitStopRegion pitStop = this.plugin.getPitStopManager().getPitStop(trackName);
                player.sendMessage("§e═══════════════════════════════════");
                player.sendMessage("§6§lPit Stop Region: §f" + trackName);
                player.sendMessage("§e═══════════════════════════════════");
                player.sendMessage(pitStop.hasEntry() ? "§a✓ ENTRADA: " + this.formatLocation(pitStop.getEntryCenter()) : "§c✗ ENTRADA: não configurada");
                player.sendMessage(pitStop.hasExit() ? "§a✓ SAÍDA: " + this.formatLocation(pitStop.getExitCenter()) : "§c✗ SAÍDA: não configurada");
                player.sendMessage("");
                if (pitStop.hasArea()) {
                    String var10001 = this.formatLocation(pitStop.getAreaRegion().getMin());
                    player.sendMessage("§a✓ AREA: " + var10001);
                } else {
                    player.sendMessage("§c✗ AREA: não configurada");
                }

                player.sendMessage("§e═══════════════════════════════════");
            }
        }
    }

    @Subcommand("pitstop test")
    @Description("Testa sua posição no pit stop")
    public void onPitStopTest(Player player) {
        Location loc = player.getLocation();
        String entry = this.plugin.getPitStopManager().getPitStopEntryAtLocation(loc);
        String exit = this.plugin.getPitStopManager().getPitStopExitAtLocation(loc);
        if (entry != null) {
            player.sendMessage("§a✓ Você está na ENTRADA do pit stop da pista §e" + entry + "§a!");
        } else if (exit != null) {
            player.sendMessage("§a✓ Você está na SAÍDA do pit stop da pista §e" + exit + "§a!");
        } else {
            player.sendMessage("§c✗ Você NÃO está em nenhuma região de pit stop.");
        }

    }

    @Subcommand("location leaderboard java")
    @Description("Teleporta o holograma do leaderboard Java")
    @CommandCompletion("@tracks @nothing")
    public void onLocationLeaderboardJava(Player player, String trackName, @Optional Double x, @Optional Double y, @Optional Double z) {
        Location targetLocation = (x != null && y != null && z != null)
                ? new Location(player.getWorld(), x, y + 1.5, z)
                : player.getLocation();

        // Usa o método unificado que retorna a instância única da pista
        TrackLeaderboard leaderboard = this.plugin.getOrCreateLeaderboard(trackName, targetLocation);

        leaderboard.setLocation(targetLocation);
        leaderboard.updateJavaLeaderboard(); // Atualiza especificamente o lado Java

        player.sendMessage("§aHolograma JAVA da pista §e" + trackName + " §ateleportado!");
    }

    @Subcommand("location leaderboard bedrock")
    @Description("Teleporta o holograma do leaderboard Bedrock")
    @CommandCompletion("@tracks @nothing")
    public void onLocationLeaderboardBedrock(Player player, String trackName, @Optional Double x, @Optional Double y, @Optional Double z) {
        Location targetLocation = (x != null && y != null && z != null)
                ? new Location(player.getWorld(), x, y + 1.5, z)
                : player.getLocation();

        // Usa a MESMA instância que o Java usaria
        TrackLeaderboard leaderboard = this.plugin.getOrCreateLeaderboard(trackName, targetLocation);

        leaderboard.setLocation(targetLocation);
        leaderboard.updateBedrockLeaderboard(); // Atualiza especificamente o lado Bedrock

        player.sendMessage("§aHolograma BEDROCK da pista §e" + trackName + " §ateleportado!");
    }
    @Subcommand("location startline")
    @Description("Define a linha de largada")
    public void onLocationStartLine(Player player) {
        player.sendMessage("§eFunção de 'startline' ainda não implementada.");
    }

    public void applyGroupMode(String track, BoatUtilsGroupMode mode) {
        if (mode != TrackEditorCommand.BoatUtilsGroupMode.BA && mode != TrackEditorCommand.BoatUtilsGroupMode.BA_NOFD) {
            if (mode != TrackEditorCommand.BoatUtilsGroupMode.BA_BLUE_NOFD && mode != TrackEditorCommand.BoatUtilsGroupMode.BA_BLUE) {
                this.mysql.replaceAllBoatUtilsSettings(track, mode.stepHeight, mode.slipperiness, !mode.noFallDamage, mode.waterElevation, mode.airControl, mode.jumpForce == null ? 0.0F : mode.jumpForce, (double)-0.04F, 1.0F, 0.04F, 0.005F, 0.005F, true, true, true, 0, true, 0.0F, 0, false, false, 5, (String)null, (String)null);
            } else {
                this.mysql.replaceAllBoatUtilsSettings(track, mode.stepHeight, mode.slipperiness, !mode.noFallDamage, mode.waterElevation, mode.airControl, mode.jumpForce == null ? 0.0F : mode.jumpForce, (double)-0.04F, 1.0F, 0.04F, 0.005F, 0.005F, true, true, true, 0, true, 0.0F, 0, false, false, 5, "minecraft:air;0.989", (String)null);
            }
        } else {
            this.mysql.replaceAllBoatUtilsSettings(track, mode.stepHeight, mode.slipperiness, !mode.noFallDamage, mode.waterElevation, mode.airControl, mode.jumpForce == null ? 0.0F : mode.jumpForce, (double)-0.04F, 1.0F, 0.04F, 0.005F, 0.005F, true, true, true, 0, true, 0.0F, 0, false, false, 5, "minecraft:air;0.98", (String)null);
        }

    }

    public static enum BoatUtilsGroupMode {
        RALLY(8, 0.98F, true, false, 1.25F, true, 0.0F),
        RALLY_BLUE(9, 0.989F, true, false, 1.25F, true, 0.0F),
        BA_NOFD(10, 0.6F, true, false, 1.25F, true, 0.0F),
        PARKOUR(11, 0.98F, true, false, 0.5F, true, 0.36F),
        BA_BLUE_NOFD(12, 0.6F, true, false, 1.25F, true),
        PARKOUR_BLUE(13, 0.989F, true, false, 0.5F, true, 0.36F),
        BA(14, 0.6F, true, false, 1.25F),
        BA_BLUE(15, 0.6F, true, false, 1.25F),
        BROKEN_SLIME_RALLY(0, 0.98F, true, false, 1.25F, true),
        BROKEN_SLIME_RALLY_BLUE(1, 0.989F, true, false, 1.25F, true),
        BROKEN_SLIME_BA_NOFD(2, 0.6F, true, false, 1.25F, true),
        BROKEN_SLIME_PARKOUR(3, 0.98F, true, false, 0.5F, true, 0.36F),
        BROKEN_SLIME_BA_BLUE_NOFD(4, 0.6F, true, false, 1.25F, true),
        BROKEN_SLIME_PARKOUR_BLUE(5, 0.989F, true, false, 0.5F, true, 0.36F),
        BROKEN_SLIME_BA(6, 0.6F, true, false, 1.25F, true),
        BROKEN_SLIME_BA_BLUE(7, 0.6F, true, false, 1.25F, true);

        public final int id;
        public final float slipperiness;
        public final boolean airControl;
        public final boolean waterElevation;
        public final float stepHeight;
        public final Float jumpForce;
        public final boolean noFallDamage;

        private BoatUtilsGroupMode(int id, float slip, boolean air, boolean water, float step) {
            this(id, slip, air, water, step, false, (Float)null);
        }

        private BoatUtilsGroupMode(int id, float slip, boolean air, boolean water, float step, boolean noFallDamage) {
            this(id, slip, air, water, step, noFallDamage, (Float)null);
        }

        private BoatUtilsGroupMode(int id, float slip, boolean air, boolean water, float step, Float jumpForce) {
            this(id, slip, air, water, step, false, jumpForce);
        }

        private BoatUtilsGroupMode(int id, float slip, boolean air, boolean water, float step, boolean noFallDamage, Float jumpForce) {
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
