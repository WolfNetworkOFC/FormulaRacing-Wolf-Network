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
import dev.EfraGroup.formulaRacing.Medals.MedalManager;
import dev.EfraGroup.formulaRacing.Utils.DebugManager;
import dev.EfraGroup.formulaRacing.Utils.DiscordUtils;
import dev.EfraGroup.formulaRacing.Utils.TitleHelper;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.Utils.WorldEditSelect;
import dev.EfraGroup.formulaRacing.Utils.trackexchange.TrackExchangeManager;
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
@Description("Track editing commands")
public class TrackEditorCommand extends BaseCommand {
    private final FormulaRacing plugin;
    private final DatabaseManager mysql;
    private final PacketSender packetSender;
    private final WorldEditSelect worldEditSelect;
    private final TrackExchangeManager trackExchange;
    private final Map<UUID, String> selectedTracks = new HashMap<>();

    public TrackEditorCommand(FormulaRacing plugin, DatabaseManager mysql, PacketSender packetSender, WorldEditSelect worldEditSelect, TrackExchangeManager trackExchange) {
        this.plugin = plugin;
        this.mysql = mysql;
        this.packetSender = packetSender;
        this.worldEditSelect = worldEditSelect;
        this.trackExchange = trackExchange;
    }

    @Default
    @CatchUnknown
    public void onDefault(Player player) {
        CommandHelpService.sendHelp(player, this, "/trackedit");
    }

    @Subcommand("help|ajuda|?")
    @Description("Shows help for the trackedit command")
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
                player.sendMessage("§cYou did not select any track and did not provide a name.");
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
    @Description("Creates a new track")
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
    @Description("Selects a track for editing (or detects the current one)")
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
                player.sendMessage("§cYou did not specify a track and none could be detected nearby.");
                return;
            }

            trackName = detectedTrack;
            player.sendMessage("§e[Auto-Detect] Track detected: §f" + detectedTrack);
        }

        if (!this.mysql.isTrackExists(trackName)) {
                player.sendMessage("§cThat track does not exist.");
            } else {
                this.setSelectedTrack(player.getUniqueId(), trackName);
            this.plugin.sendMessage(player, "te_selected", new String[]{"{track}", trackName});
        }
    }

    @Subcommand("view")
    @Description("Views track regions with particles")
    @CommandCompletion("@tracks")
    public void onView(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (!this.mysql.isTrackExists(trackName)) {
                player.sendMessage("§cThat track does not exist.");
            } else {
                boolean isViewing = this.plugin.getTrackVisualizer().isViewing(player.getUniqueId(), trackName);
                if (!isViewing) {
                    Location spawn = this.mysql.getTrackSpawn(trackName);
                    if (spawn != null) {
                        SchedulerHelper.teleport(player, spawn);
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
    @Description("Deletes a track")
    @CommandCompletion("@tracks")
    public void onDelete(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (!this.mysql.isTrackExists(trackName)) {
                player.sendMessage("§cTrack '" + trackName + "' not found.");
            } else {
                this.mysql.deleteTrack(trackName);
                this.plugin.sendMessage(player, "te_deleted", new String[]{"{track}", trackName});
            }
        }
    }

    @Subcommand("rename fullname")
    @Description("Renames a track (uses display name with spaces) and updates all DB references")
    @CommandCompletion("@tracks @nothing")
    public void onRenameFullname(Player player, String trackName, String newName) {
        executeRename(player, trackName, newName,
                trackName.replaceAll("\\s+", ""), newName.replaceAll("\\s+", ""));
    }

    @Subcommand("rename trackname")
    @Description("Renames a track (uses internal WS name without spaces) and updates all DB references")
    @CommandCompletion("@tracks @nothing")
    public void onRenameTrackname(Player player, String trackNameWS, String newNameWS) {
        executeRename(player, trackNameWS, newNameWS, trackNameWS, newNameWS);
    }

    private void executeRename(Player player, String trackName, String newName, String trackNameWS, String newNameWS) {
        if (!this.mysql.isTrackExists(trackNameWS)) {
            player.sendMessage("§cTrack '" + trackName + "' not found.");
            return;
        }
        if (trackNameWS.equalsIgnoreCase(newNameWS)) {
            player.sendMessage("§cThe new name is the same as the current name.");
            return;
        }
        if (this.mysql.isTrackExists(newNameWS)) {
            player.sendMessage("§cA track with the name '" + newName + "' already exists.");
            return;
        }

        boolean success = this.mysql.renameTrack(trackName, newName);
        if (success) {
            player.sendMessage("§a✅ Track renamed to '" + newName + "'!");
            player.sendMessage("§7All database references, regions, checkpoints, "
                    + "boatutils settings, ghost files, and leaderboards have been updated.");
            this.plugin.getTrackLeaderboards().remove(trackNameWS);
            org.bukkit.Location holoLoc = this.mysql.getHologramLocation(newNameWS);
            if (holoLoc != null) {
                this.plugin.getOrCreateLeaderboard(newNameWS, holoLoc);
            }
        } else {
            player.sendMessage("§c❌ Error renaming track. Check console for details.");
        }
    }

    @Subcommand("broadcast newtrack")
    @Description("Sends new track message to Discord")
    @CommandCompletion("@nothing @tracks")
    public void onBroadcastNewTrack(Player player, @Optional String imageUrl, @Optional String tags, @Optional String collaborators, @Optional String boatMode, @Optional String trackNameArg) {
        if (!DiscordUtils.isEnabled()) {
            player.sendMessage("§c❌ Discord webhook not configured! Set it up in the config.yml first.");
            return;
        }
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            List<String> tagsList = java.util.List.of();
            if (tags != null && !tags.isEmpty()) {
                tagsList = java.util.Arrays.asList(tags.split(","));
            }
            DiscordUtils.sendNewTrackEmbed(this.plugin, trackName, this.mysql.getTrackOwner(trackName), collaborators, boatMode != null ? boatMode : "", tagsList,
                imageUrl != null && !imageUrl.isEmpty() ? java.util.List.of(imageUrl) : java.util.List.of());
            player.sendMessage("§a✅ Broadcast sent to Discord!");
        }
    }

    @Subcommand("setowner")
    @Description("Sets the owner of a track")
    @CommandCompletion("@players @tracks")
    public void onSetOwner(Player player, String newOwnerName, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            boolean success = this.mysql.setTrackOwner(trackName, newOwnerName);
            if (success) {
                this.plugin.sendMessage(player, "te_owner_set", new String[]{"{track}", trackName, "{owner}", newOwnerName});
            } else {
                player.sendMessage("§c❌ Error updating track owner. Check console for details.");
            }

        }
    }

    @Subcommand("cam set|s")
    @Description("Adds a camera to the track")
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
    @Description("Removes a camera from the track")
    @CommandCompletion("@nothing @tracks")
    public void onCamDelete(Player player, int id, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            this.mysql.removeCamera(trackName, id);
            this.plugin.sendMessage(player, "te_cam_removed", new String[]{"{id}", String.valueOf(id)});
        }
    }

    @Subcommand("cam list|l")
    @Description("Lists cameras of a track")
    @CommandCompletion("@tracks")
    public void onCamList(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            List<Integer> cameraIds = this.mysql.getCamerasForTrack(trackName);
            this.plugin.sendMessage(player, "te_cam_list", new String[]{"{track}", trackName, "{ids}", cameraIds.toString()});
        }
    }

    @Subcommand("resetalltimes")
    @Description("Resets all times of a track")
    @CommandCompletion("@tracks")
    public void onResetAllTimes(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            this.mysql.resetAllTrackTimes(trackName);
            this.plugin.sendMessage(player, "te_times_reset", new String[]{"{track}", trackName});
        }
    }

    @Subcommand("region start")
    @Description("Sets the start region (START)")
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
    @Description("Sets the reset region (RESET)")
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
                    player.sendMessage("§aRESET region set successfully! ID: " + savedId);
                    this.plugin.getRegionListener().reloadRegions();
                } else {
                    this.plugin.sendMessage(player, "te_region_error", new String[]{"{type}", "RESET"});
                }

            }
        }
    }

    @Subcommand("region end")
    @Description("Sets the finish region (END)")
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
    @Description("Removes a specific region by ID")
    @CommandCompletion("@nothing")
    public void onRegionRemove(Player player, int regionId) {
        boolean deleted = this.mysql.deleteRegionById(regionId);
        if (deleted) {
            player.sendMessage("§aRegion #" + regionId + " removed successfully!");
            this.plugin.getRegionListener().reloadRegions();
        } else {
            player.sendMessage("§cCould not remove region with ID: " + regionId);
        }

    }

    @Subcommand("region clear")
    @Description("Clears all regions of a specific type on the track")
    @CommandCompletion("@tracks @nothing")
    public void onRegionClear(Player player, @Optional String trackNameArg, String type) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            String trackWS = trackName.replaceAll("\\s+", "").toLowerCase();
            if (!type.equalsIgnoreCase("START") && !type.equalsIgnoreCase("END") && !type.equalsIgnoreCase("RESET")) {
                player.sendMessage("§cInvalid type. Use: START, END or RESET");
            } else {
                int count = 0;

                for(DatabaseManager.RegionData r : this.mysql.getAllRegions()) {
                    if (r.getTrackName().equalsIgnoreCase(trackWS) && r.getType().equalsIgnoreCase(type) && this.mysql.deleteRegionById(r.getId())) {
                        ++count;
                    }
                }

                if (count > 0) {
                    player.sendMessage("§aRemoved " + count + " regions of type " + type.toUpperCase() + " from track " + trackName);
                    this.plugin.getRegionListener().reloadRegions();
                } else {
                    player.sendMessage("§cNo region of type " + type.toUpperCase() + " found to remove.");
                }

            }
        }
    }

    @Subcommand("region expand")
    @Description("Automatically expands very thin regions")
    @CommandCompletion("@tracks")
    public void onRegionExpand(Player player, @Optional String trackFilter) {
        player.sendMessage("§e[REGION EXPAND] Expanding very thin regions...");
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
            player.sendMessage(String.format("§a[REGION EXPAND] %d region(s) expanded!", expanded));
            this.plugin.getRegionListener().reloadRegions();
            player.sendMessage("§aRegions reloaded! Test the time trial now.");
        } else {
            player.sendMessage("§a[REGION EXPAND] No regions need expanding.");
        }

    }

    @Subcommand("region debug")
    @Description("Lists regions loaded in memory")
    @CommandCompletion("@tracks")
    public void onRegionDebug(Player player, @Optional String trackFilter) {
        this.plugin.getRegionListener().debugListRegions("world", trackFilter);
        player.sendMessage("§aCheck the server console to see loaded regions!");
    }

    @Subcommand("region cleanup")
    @Description("Cleans up duplicate regions")
    @CommandCompletion("@tracks")
    public void onRegionCleanup(Player player, @Optional String specificTrack) {
        if (specificTrack != null) {
            player.sendMessage("§e[REGION CLEANUP] Cleaning duplicate regions for track: §f" + specificTrack);
        } else {
            player.sendMessage("§e[REGION CLEANUP] Cleaning duplicate regions for §cALL§e tracks...");
        }

        List<DatabaseManager.RegionData> allRegions = this.mysql.getAllRegions();
        Map<String, List<DatabaseManager.RegionData>> regionsByTrackAndType = new HashMap<>();
        String normalizedSpecificTrack = specificTrack != null ? specificTrack.replace(" ", "").toLowerCase() : null;

        for(DatabaseManager.RegionData region : allRegions) {
            String type = region.getType().toUpperCase();
            if ((type.equals("START") || type.equals("END")) && (normalizedSpecificTrack == null || region.getTrackName().equalsIgnoreCase(normalizedSpecificTrack))) {
                String var10000 = region.getTrackName().toLowerCase();
                String key = var10000 + "_" + type;
                regionsByTrackAndType.computeIfAbsent(key, (k) -> new ArrayList<>()).add(region);
            }
        }

        int totalDeleted = 0;
        int tracksProcessed = 0;

        for(Map.Entry<String, List<DatabaseManager.RegionData>> entry : regionsByTrackAndType.entrySet()) {
            List<DatabaseManager.RegionData> regions = entry.getValue();
            if (regions.size() > 1) {
                regions.sort(Comparator.comparingInt(DatabaseManager.RegionData::getId).reversed());
                DatabaseManager.RegionData newest = (DatabaseManager.RegionData)regions.get(0);
                player.sendMessage(String.format("§e  Track §f%s §e(%s): Keeping region ID %d, deleting %d old one(s)...", newest.getTrackName(), newest.getType(), newest.getId(), regions.size() - 1));

                for(int i = 1; i < regions.size(); ++i) {
                    DatabaseManager.RegionData old = (DatabaseManager.RegionData)regions.get(i);
                    this.mysql.deleteRegionById(old.getId());
                    ++totalDeleted;
                }

                ++tracksProcessed;
            }
        }

        if (totalDeleted > 0) {
            player.sendMessage(String.format("§a[REGION CLEANUP] Completed! %d duplicate region(s) removed from %d track(s).", totalDeleted, tracksProcessed));
        } else if (specificTrack != null) {
            player.sendMessage("§a[REGION CLEANUP] No duplicate regions found in track §f" + specificTrack);
        } else {
            player.sendMessage("§a[REGION CLEANUP] No duplicate regions found!");
        }

        this.plugin.getRegionListener().reloadRegions();
    }

    @Subcommand("info")
    @Description("Shows track information")
    @CommandCompletion("@tracks")
    public void onInfo(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            String trackNameWS = this.normalizeTrackName(trackName);
            player.sendMessage("§6§l========== Track Information ==========");
            player.sendMessage("§e Name: §f" + trackName);
            player.sendMessage("§e Normalized Name: §f" + trackNameWS);
            player.sendMessage("");
            List<DatabaseManager.RegionData> allRegions = this.mysql.getAllRegions();
            boolean foundStart = false;
            boolean foundEnd = false;
            player.sendMessage("§6§l[START/END Regions]");

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
                player.sendMessage("§c✗ START: Not configured");
            }

            if (!foundEnd) {
                player.sendMessage("§c✗ END: Not configured");
            }

            player.sendMessage("");
            List<DatabaseManager.RegionData> checkpoints = this.mysql.getCheckpoints(trackNameWS);
            player.sendMessage("§6§l[Checkpoints]");
            if (checkpoints.isEmpty()) {
                player.sendMessage("§c✗ No checkpoint configured");
            } else {
                player.sendMessage("§a✓ Total checkpoints: §f" + checkpoints.size());

                for(int i = 0; i < checkpoints.size(); ++i) {
                    DatabaseManager.RegionData cp = (DatabaseManager.RegionData)checkpoints.get(i);
                    double centerX = (cp.getMinX() + cp.getMaxX()) / (double)2.0F;
                    double centerY = (cp.getMinY() + cp.getMaxY()) / (double)2.0F;
                    double centerZ = (cp.getMinZ() + cp.getMaxZ()) / (double)2.0F;
                    player.sendMessage("  §7CP" + i + ": §f" + String.format("%.1f, %.1f, %.1f", centerX, centerY, centerZ));
                }
            }

            player.sendMessage("");
            player.sendMessage("§6§l[Diagnostic]");
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
                        player.sendMessage("§a✓ START and END are the same region (correct for circuits)");
                    } else {
                        player.sendMessage("§e⚠ START and END are different regions");
                    }
                }
            }

            if (checkpoints.isEmpty()) {
                player.sendMessage("§c✗ ERROR: Track without checkpoints!");
                player.sendMessage("§7   Use: /trackedit checkpoint add <id> to add");
            }

            player.sendMessage("§6§l==========================================");
        }
    }

    @Subcommand("location tp_finish_all")
    @Description("Sets the teleport location for ALL at race end")
    @CommandCompletion("@tracks")
    public void onTpFinishAll(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            String trackNameWS = this.normalizeTrackName(trackName);
            this.mysql.setTrackFinishAll(trackNameWS, player.getLocation());
            player.sendMessage("§aFinal teleport location (ALL) set for track " + trackName);
        }
    }

    @Subcommand("location tp_finish_pos")
    @Description("Sets the teleport location for a specific position")
    @CommandCompletion("@nothing @tracks")
    public void onTpFinishPos(Player player, int position, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            String trackNameWS = this.normalizeTrackName(trackName);
            this.mysql.setTrackFinishPos(trackNameWS, position, player.getLocation());
            player.sendMessage("§aTeleport location for Position #" + position + " set for track " + trackName);
        }
    }

    @Subcommand("spawn")
    @Description("Sets the track spawn")
    @CommandCompletion("@tracks")
    public void onSpawn(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            String trackNameWS = this.normalizeTrackName(trackName);
            this.plugin.getDebugManager().logRaceSystem("Saving track spawn '" + trackName + "' (trackNameWS='" + trackNameWS + "') for player " + player.getName());
            player.sendMessage("§eSaving track spawn: §f" + trackName + " §7(normalized: §f" + trackNameWS + "§7)");
            this.mysql.setTrackSpawn(trackName, player.getLocation());
            this.plugin.sendMessage(player, "te_spawn_saved", new String[0]);
        }
    }

    @Subcommand("checkpoint add")
    @Description("Adds a checkpoint")
    @CommandCompletion("@nothing @tracks")
    public void onCheckpointAdd(Player player, int id, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            String trackNameWS = this.normalizeTrackName(trackName);
            WorldEditSelect var10000 = this.worldEditSelect;
            if (!WorldEditSelect.hasSelection(player)) {
                player.sendMessage("§cYou need to make a WorldEdit selection to add the checkpoint.");
            } else {
                Location min = WorldEditSelect.getMin(player);
                Location max = WorldEditSelect.getMax(player);
                String worldName = player.getWorld().getName();
                boolean success = this.mysql.addCheckpoint(id, trackNameWS, player);
                if (success) {
                    this.mysql.clearCheckpointTimesForTrack(trackNameWS);
                    this.plugin.sendMessage(player, "te_checkpoint_added", new String[]{"{id}", String.valueOf(id), "{track}", trackName});
                    this.plugin.getDebugManager().logDatabaseOperations("[FormulaRacing] === Saving Checkpoint ===");
                    this.plugin.getDebugManager().logDatabaseOperations("[FormulaRacing] Track: " + trackName);
                    this.plugin.getDebugManager().logDatabaseOperations("[FormulaRacing] ID: " + id);
                    this.plugin.getDebugManager().logDatabaseOperations("[FormulaRacing] World: " + worldName);
                    DebugManager var10 = this.plugin.getDebugManager();
                    double var10001 = min.getX();
                    var10.logDatabaseOperations("[FormulaRacing] Min: X=" + var10001 + " Y=" + min.getY() + " Z=" + min.getZ());
                    var10 = this.plugin.getDebugManager();
                    var10001 = max.getX();
                    var10.logDatabaseOperations("[FormulaRacing] Max: X=" + var10001 + " Y=" + max.getY() + " Z=" + max.getZ());
                    this.plugin.getDebugManager().logDatabaseOperations("[FormulaRacing] =======================");
                } else {
                    player.sendMessage("§cError adding checkpoint " + id);
                }

            }
        }
    }

    @Subcommand("checkpoint remove")
    @Description("Removes a checkpoint by checkpointId")
    @CommandCompletion("@nothing @tracks")
    public void onCheckpointRemove(Player player, int id, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            String trackNameWS = this.normalizeTrackName(trackName);
            boolean success = this.mysql.removeCheckpoint(trackNameWS, id);
            if (success) {
                this.mysql.clearCheckpointTimesForTrack(trackNameWS);
                this.plugin.sendMessage(player, "te_checkpoint_removed", new String[]{"{id}", String.valueOf(id), "{track}", trackName});
            } else {
                this.plugin.sendMessage(player, "te_checkpoint_error", new String[]{"{id}", String.valueOf(id)});
            }

        }
    }

    @Subcommand("checkpoint removeid")
    @Description("Removes a checkpoint by database ID (to remove duplicates)")
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
    @Description("Sets the track time (Ticks)")
    @CommandCompletion("@nothing @tracks")
    public void onTime(Player player, long ticks, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            boolean saved = this.mysql.setTrackGameTime(trackName, ticks);
            if (saved) {
                // Preview the time change for the admin
                player.setPlayerTime(ticks, false);
                this.plugin.sendMessage(player, "te_time_ticks", new String[]{"{track}", trackName, "{ticks}", String.valueOf(ticks)});
            } else {
                player.sendMessage("§c❌ Error saving game time for track §e" + trackName);
            }
        }
    }

    @Subcommand("time reset")
    @Description("Resets the track time to normal (world time)")
    @CommandCompletion("@tracks")
    public void onTimeReset(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            boolean saved = this.mysql.setTrackGameTime(trackName, -1L);
            if (saved) {
                // Reset the admin's time preview back to world time
                player.resetPlayerTime();
                player.sendMessage("§a✅ Track time reset to normal for §e" + trackName);
            } else {
                player.sendMessage("§c❌ Error resetting game time for track §e" + trackName);
            }
        }
    }

    @Subcommand("medals")
    @Description("Sets a track medal time. Syntax: /te medals <saphira|netherite|diamond|gold|silver|bronze> <tempo> [pista]")
    @CommandCompletion("saphira netherite diamond gold silver bronze @nothing @tracks")
    public void onMedals(Player player, String medalType, String timeArg, @Optional String trackNameArg) {
        String medal = MedalManager.normalizeMedal(medalType);
        if (!MedalManager.MEDAL_TYPES.contains(medal)) {
            player.sendMessage("§cMedalha inválida. Use: saphira, netherite, diamond, gold, silver ou bronze.");
            return;
        }
        double timeSeconds;
        try {
            timeSeconds = MedalManager.parseTimeToSeconds(timeArg);
        } catch (NumberFormatException e) {
            player.sendMessage("§cTempo inválido: " + timeArg + " (use formato 1:32.434 ou 92.434)");
            return;
        }
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName == null) return;
        boolean saved = this.plugin.getMedalManager().setMedalTime(trackName, medal, timeSeconds);
        if (saved) {
            player.sendMessage("§a✅ Medalha §e" + medal.toUpperCase() + "§a definida em §e" + trackName
                    + "§a — §e" + MedalManager.formatTime(timeSeconds));
        } else {
            player.sendMessage("§c❌ Erro ao salvar a medalha na database.");
        }
    }

    @Subcommand("medals record")
    @Description("Records the next lap as a medal (time + line). Syntax: /te medals record <medal> [pista]")
    @CommandCompletion("saphira netherite diamond gold silver bronze @tracks")
    public void onMedalsRecord(Player player, String medalType, @Optional String trackNameArg) {
        String medal = MedalManager.normalizeMedal(medalType);
        if (!MedalManager.MEDAL_TYPES.contains(medal)) {
            player.sendMessage("§cMedalha inválida. Use: saphira, netherite, diamond, gold, silver ou bronze.");
            return;
        }
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName == null) return;
        this.plugin.getMedalManager().armRecord(player, medal, trackName);
        player.sendMessage("§a🎯 Gravação armada: a próxima volta em §e" + trackName
                + "§a será salva como medalha §e" + medal.toUpperCase() + "§a (tempo + linha).");
    }

    @Subcommand("icon")
    @Description("Sets the track icon. Syntax: /te icon material[props] [amount] [track]")
    @CommandCompletion("@nothing @nothing @tracks")
    public void onIcon(Player player, String iconArg, @Optional String amountArg, @Optional String trackNameArg) {
        String materialName;
        String iconMeta = null;
        int amount = 1;

        // Parse bracket syntax: light[level=2]
        int bracketStart = iconArg.indexOf('[');
        if (bracketStart > 0 && iconArg.endsWith("]")) {
            materialName = iconArg.substring(0, bracketStart);
            iconMeta = iconArg.substring(bracketStart + 1, iconArg.length() - 1);
        } else {
            materialName = iconArg;
        }

        // Parse optional amount from second argument
        if (amountArg != null && !amountArg.isEmpty()) {
            try {
                // amountArg might be a track name if user didn't specify amount — detect by checking if it's a number
                if (amountArg.matches("\\d+")) {
                    amount = Integer.parseInt(amountArg);
                    if (amount < 1 || amount > 99) {
                        player.sendMessage("§cAmount must be between 1 and 99.");
                        return;
                    }
                    // Second arg was consumed as amount, third arg is trackName
                } else {
                    // Second arg was a track name, use default amount
                    trackNameArg = amountArg;
                    amountArg = null;
                }
            } catch (NumberFormatException e) {
                // Not a number, treat as track name
                trackNameArg = amountArg;
            }
        }

        Material iconMat;
        try {
            iconMat = Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid material: " + materialName);
            return;
        }

        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (this.mysql.setTrackIcon(trackName, iconMat.name(), amount, iconMeta)) {
                String msg = "§a✅ Track icon updated to §e" + iconMat.name();
                if (iconMeta != null) msg += "[" + iconMeta + "]";
                if (amount > 1) msg += " §7x" + amount;
                player.sendMessage(msg);
            } else {
                player.sendMessage("§cError updating track icon.");
            }
        }
    }

    @Subcommand("open")
    @Description("Opens a track for use")
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
    @Description("Closes a track for use")
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
    @Description("Pit Stop settings")
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
                        player.sendMessage("§cMake a WorldEdit selection.");
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
                        player.sendMessage("§cMake a WorldEdit selection.");
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
                    player.sendMessage("§eChecking location: " + var10001 + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + " (" + loc.getWorld().getName() + ")");
                    String entry = this.plugin.getPitStopManager().getPitStopEntryAtLocation(loc);
                    String var21 = entry == null ? "§cNone" : "§a" + entry;
                    player.sendMessage("§7Entry Region: " + var21);
                    String exit = this.plugin.getPitStopManager().getPitStopExitAtLocation(loc);
                    var21 = exit == null ? "§cNone" : "§a" + exit;
                    player.sendMessage("§7Exit Region: " + var21);
                    String area = this.plugin.getPitStopManager().getPitAreaAtLocation(loc);
                    var21 = area == null ? "§cNone" : "§a" + area;
                    player.sendMessage("§7Pit Area: " + var21);
                    boolean overBlock = this.plugin.getPitStopManager().isOverPitBlock(loc);
                    player.sendMessage("§7Pit Block: " + (overBlock ? "§aYES (TERRACOTTA)" : "§cNO"));
                    if (trackName != null) {
                        PitStopRegion region1 = this.plugin.getPitStopManager().getPitStop(trackNameWS);
                        if (region1 != null) {
                            player.sendMessage("§eRegion " + trackName + " (Entry):");
                            if (region1.hasEntry()) {
                                Location min = region1.getEntryRegion().getMin();
                                Location max = region1.getEntryRegion().getMax();
                                int var24 = min.getBlockX();
                                player.sendMessage("  §7Min: " + var24 + "," + min.getBlockY() + "," + min.getBlockZ());
                                var24 = max.getBlockX();
                                player.sendMessage("  §7Max: " + var24 + "," + max.getBlockY() + "," + max.getBlockZ());
                            } else {
                                player.sendMessage("  §cNot set.");
                            }
                        }
                    }
                    break;
                default:
                    player.sendMessage("§cUnknown action. Use addentry, addexit, remove, info, edit, list or check.");
            }

        }
    }

    @Subcommand("boatutils reset")
    @Description("Resets all BoatUtils settings of a track to default")
    @CommandCompletion("@tracks")
    public void onBoatUtilsReset(Player player, String track) {
        String trackName = track.replace(" ", "").toLowerCase();
        this.mysql.resetBoatUtilsSettings(trackName);
        player.sendMessage("§a✔ BoatUtils settings reset to §fVanilla §aon track §e" + trackName);
    }

    @Subcommand("boatutils group set")
    @Description("Resets to vanilla and applies a full settings preset")
    @CommandCompletion("@boatutils_group_modes @tracks")
    public void onBoatUtilsGroupSet(Player player, BoatUtilsGroupMode mode, String track) {
        String trackName = track.replace(" ", "").toLowerCase();
        this.mysql.resetBoatUtilsSettings(trackName);
        this.applyGroupMode(trackName, mode);
        player.sendMessage("§a✔ Mode §b" + mode.name() + " §aapplied (reset + mode) on track §e" + trackName);
    }

    @Subcommand("boatutils group add")
    @Description("Applies only preset values without resetting existing settings")
    @CommandCompletion("@boatutils_group_modes @tracks")
    public void onBoatUtilsGroupAdd(Player player, BoatUtilsGroupMode mode, String track) {
        String trackName = track.replace(" ", "").toLowerCase();
        this.mysql.applyGroupModeValues(trackName, mode.stepHeight, mode.slipperiness, !mode.noFallDamage, mode.waterElevation, mode.airControl, mode.jumpForce);
        player.sendMessage("§a✔ Mode §b" + mode.name() + " §aapplied (without reset) on track §e" + trackName);
    }

    @Subcommand("boatutils config")
    @Description("Configures a specific BoatUtils value")
    @CommandCompletion("@boatutils_settings @nothing @tracks")
    public void onBoatUtilsSetConfig(Player player, String setting, String value, @Optional String trackArg) {
        String trackName = this.getTargetTrack(player, trackArg);
        if (trackName != null) {
            trackName = trackName.replace(" ", "").toLowerCase();
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
                    case "walltapmultiplier":
                        this.mysql.setWalltapMultiplier(trackName, Float.parseFloat(value));
                        break;
                    case "jumps":
                        this.mysql.setJumps(trackName, Integer.parseInt(value));
                        break;
                    case "scale":
                        this.mysql.setScale(trackName, Float.parseFloat(value));
                        break;
                    case "stepupslipperiness":
                        this.mysql.setStepUpSlipperiness(trackName, Float.parseFloat(value));
                        break;
                    case "fixdoublewaterelevation":
                        this.mysql.setFixDoubleWaterElevation(trackName, this.parseBoolean(value));
                        break;
                    case "lateralslipperiness":
                        this.mysql.setLateralSlipperiness(trackName, Float.parseFloat(value));
                        break;
                    case "brakeslipperiness":
                        this.mysql.setBrakeSlipperiness(trackName, Float.parseFloat(value));
                        break;
                    case "multistepping":
                        this.mysql.setMultiStepping(trackName, this.parseBoolean(value));
                        break;
                    case "maxspeed":
                        this.mysql.setMaxSpeed(trackName, Float.parseFloat(value));
                        break;
                    case "maxspeedresistance":
                        this.mysql.setMaxSpeedResistance(trackName, Float.parseFloat(value));
                        break;
                    case "honeycompatibility":
                        this.mysql.setHoneyCompatibility(trackName, this.parseBoolean(value));
                        break;
                    case "collisionfilter":
                        this.mysql.setCollisionFilter(trackName, value);
                        break;
                    case "customslipperiness":
                        String[] entries = value.split(",");
                        for (String entry : entries) {
                            String[] parts = entry.split(";");
                            if (parts.length == 2) {
                                this.mysql.addCustomSlipperiness(trackName, parts[0], Float.parseFloat(parts[1]));
                            } else {
                                player.sendMessage("§c✘ Invalid format: §f" + entry + " §c(expected: material;value)");
                                return;
                            }
                        }
                        break;
                    case "perblocksetting":
                        // format: settingId:value:block1,block2
                        String[] perBlockParts = value.split(":", 3);
                        if (perBlockParts.length < 2) {
                            player.sendMessage("§c✘ Invalid format. Use: §f<settingId>:<value>[:blocks]");
                            player.sendMessage("§7Setting IDs: 0=JUMP_FORCE, 1=FORWARDS_ACCEL, 2=BACKWARDS_ACCEL, 3=YAW_ACCEL, 4=TURN_FORWARDS_ACCEL, 5=WALLTAP_MULTIPLIER, 6=JUMPS, 7=COYOTE_TIME, 8=STEP_UP_SLIPPERINESS, 9=LATERAL_SLIPPERINESS, 10=BRAKE_SLIPPERINESS, 11=MAX_SPEED, 12=MAX_SPEED_RESISTANCE");
                            return;
                        }
                        String settingId = perBlockParts[0];
                        float perBlockValue = Float.parseFloat(perBlockParts[1]);
                        String blocks = perBlockParts.length > 2 ? perBlockParts[2] : "";
                        this.mysql.setPerBlockSetting(trackName, settingId + ":" + perBlockValue + ":" + blocks);
                        break;
                    default:
                        player.sendMessage("§c✘ Unknown setting: §f" + setting);
                        return;
                }

                player.sendMessage("§a✔ Configuração §e" + setting + " §aset to §b" + value + " §aon track §f" + trackName);
            } catch (NumberFormatException var9) {
                player.sendMessage("§c✘ The value '§f" + value + "§c' is not valid for §f" + setting);
            }

        }
    }

    @Subcommand("boatutils config customslipperiness add")
    @Description("Sets custom slipperiness for a block type")
    @CommandCompletion("@materials @nothing @tracks")
    public void onBoatUtilsAddSlipperiness(Player player, String materialName, float value, @Optional String trackArg) {
        String trackName = this.getTargetTrack(player, trackArg);
        if (trackName != null) {
            trackName = trackName.replace(" ", "");
            Material mat = Material.matchMaterial(materialName);
            if (mat == null) {
                player.sendMessage("§c✘ Invalid block: §f" + materialName);
            } else {
                String blockId = mat.getKey().toString();
                this.mysql.addCustomSlipperiness(trackName, blockId, value);
                player.sendMessage("§a✔ Slipperiness of §e" + mat.name() + " §aset to §b" + value + " §aon track §f" + trackName);
            }
        }
    }

    @Subcommand("boatutils config customslipperiness reset")
    @Description("Removes all block slipperiness customizations")
    @CommandCompletion("@tracks")
    public void onBoatUtilsResetSlipperiness(Player player, @Optional String trackArg) {
        String trackName = this.getTargetTrack(player, trackArg);
        if (trackName != null) {
            trackName = trackName.replace(" ", "");
            this.mysql.resetCustomSlipperiness(trackName);
            player.sendMessage("§a✔ Custom Slipperiness reset on track §e" + trackName);
        }
    }

    private boolean parseBoolean(String value) {
        return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("on") || value.equalsIgnoreCase("1") || value.equalsIgnoreCase("yes");
    }

    @Subcommand("grid add")
    @Description("Adds a grid position")
    @CommandCompletion("@nothing @tracks")
    public void onGridAdd(Player player, int id, @Optional String trackNameArg) {
        if (id >= 1 && id <= 200) {
            String trackName = this.getTargetTrack(player, trackNameArg);
            if (trackName != null) {
                if (this.mysql.addGridPosition(trackName, id, player.getLocation())) {
                    player.sendMessage("§a✓ Grid position P" + id + " added at §e" + trackName);
                    String var10001 = this.formatLocation(player.getLocation());
                    player.sendMessage("§7Location: " + var10001);
                } else {
                    player.sendMessage("§c✗ Error adding grid position.");
                }

            }
        } else {
            player.sendMessage("§cPosition must be between 1 and 200.");
        }
    }

    @Subcommand("grid remove")
    @Description("Removes a grid position")
    @CommandCompletion("@nothing @tracks")
    public void onGridRemove(Player player, int id, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (this.mysql.removeGridPosition(trackName, id)) {
                player.sendMessage("§a✓ Grid position P" + id + " removed from §e" + trackName);
            } else {
                player.sendMessage("§c✗ Error removing grid position.");
            }

        }
    }

    @Subcommand("grid clear")
    @Description("Clears all grid positions")
    @CommandCompletion("@tracks confirm")
    public void onGridClear(Player player, String trackName, @Optional String confirm) {
        if (confirm != null && confirm.equalsIgnoreCase("confirm")) {
            if (this.mysql.clearGridPositions(trackName)) {
                player.sendMessage("§a✓ All grid positions removed from §e" + trackName);
            } else {
                player.sendMessage("§c✗ Error clearing grid.");
            }

        } else {
            int count = this.mysql.getGridPositions(trackName).size();
            player.sendMessage("§e⚠ This will remove §c" + count + " positions §efrom grid!");
            player.sendMessage("§7Use §f/trackedit grid clear " + trackName + " confirm §7to confirm.");
        }
    }

    @Subcommand("grid list")
    @Description("Lists grid positions")
    @CommandCompletion("@tracks")
    public void onGridList(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            List<GridPosition> positions = this.mysql.getGridPositions(trackName);
            player.sendMessage("§e═══════════════════════════════════");
            player.sendMessage("§6§lGrid of §f" + trackName);
            player.sendMessage("§e═══════════════════════════════════");
            if (positions.isEmpty()) {
                player.sendMessage("§7No positions configured.");
                player.sendMessage("§7Use §f/trackedit grid add <id> " + trackName + " §7to add.");
            } else {
                player.sendMessage("§7Total: §f" + positions.size() + " positions");
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
    @Description("Tests the grid of a track")
    @CommandCompletion("@tracks")
    public void onGridTest(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            List<GridPosition> positions = this.mysql.getGridPositions(trackName);
            if (positions.isEmpty()) {
                player.sendMessage("§cNo grid positions configured for this track.");
            } else {
                player.sendMessage("§a✓ Testing grid of §e" + trackName + "§a...");
                player.sendMessage("§7You will be teleported to each position (1 second between each).");

                for(int i = 0; i < positions.size(); ++i) {
                    GridPosition pos = (GridPosition)positions.get(i);
                    int delay = i * 20;
                    SchedulerHelper.runTaskLater(this.plugin, () -> {
                        Location loc = pos.toLocation(this.plugin.getServer());
                        if (loc != null) {
                            SchedulerHelper.teleport(player, loc);
                            TitleHelper.sendThemedTitle(player, "&wP" + pos.getPosition(), "§7" + this.formatLocation(loc), 5, 30, 10);
                        }

                    }, delay);
                }

            }
        }
    }

    @Subcommand("grid info")
    @Description("Shows grid information")
    @CommandCompletion("@tracks")
    public void onGridInfo(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            List<GridPosition> positions = this.mysql.getGridPositions(trackName);
            DatabaseManager.TrackData trackData = this.mysql.getTrackData(trackName);
            player.sendMessage("§e═══════════════════════════════════");
            player.sendMessage("§6§lGrid Information: §f" + trackName);
            player.sendMessage("§e═══════════════════════════════════");
            player.sendMessage("");
            player.sendMessage("§7Track: §f" + trackName);
            if (trackData != null) {
                player.sendMessage("§7Checkpoints: §f" + trackData.getTotalCheckpoints());
            }

            player.sendMessage("§7Configured positions: §f" + positions.size());
            player.sendMessage("");
            if (positions.isEmpty()) {
                player.sendMessage("§e⚠ Grid not configured");
                player.sendMessage("§7The system will use automatic generation.");
                player.sendMessage("§7Use §f/trackedit grid add <id> " + trackName + " §7to configure.");
            } else {
                player.sendMessage("§a✓ Grid configured manually");
                player.sendMessage("§7Use §f/trackedit grid list " + trackName + " §7to view positions.");
                player.sendMessage("§7Use §f/trackedit grid test " + trackName + " §7to test.");
            }

            player.sendMessage("§e═══════════════════════════════════");
        }
    }

    @Subcommand("location qualigrid")
    @Description("Adds/updates a qualifying grid position")
    @CommandCompletion("@nothing @tracks")
    public void onLocationQualiGrid(Player player, int id, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (this.mysql.addQualiGridPosition(trackName, id, player.getLocation())) {
                player.sendMessage("§a✓ Quali position Q" + id + " added at §e" + trackName);
                player.sendMessage("§7Location: " + this.formatLocation(player.getLocation()));
            } else {
                player.sendMessage("§c✗ Error adding qualifying grid position.");
            }
        }
    }

    @Subcommand("qualigrid remove")
    @Description("Removes a qualifying grid position")
    @CommandCompletion("@nothing @tracks")
    public void onQualiGridRemove(Player player, int id, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (this.mysql.removeQualiGridPosition(trackName, id)) {
                player.sendMessage("§a✓ Quali position Q" + id + " removed from §e" + trackName);
            } else {
                player.sendMessage("§c✗ Error removing qualifying grid position.");
            }
        }
    }

    @Subcommand("qualigrid clear")
    @Description("Clears all qualifying grid positions")
    @CommandCompletion("@tracks confirm")
    public void onQualiGridClear(Player player, String trackName, @Optional String confirm) {
        if (confirm != null && confirm.equalsIgnoreCase("confirm")) {
            if (this.mysql.clearQualiGridPositions(trackName)) {
                player.sendMessage("§a✓ All qualifying grid positions removed from §e" + trackName);
            } else {
                player.sendMessage("§c✗ Error clearing qualifying grid.");
            }
        } else {
            int count = this.mysql.getQualiGridPositions(trackName).size();
            player.sendMessage("§e⚠ This will remove §c" + count + " positions §efrom qualifying grid!");
            player.sendMessage("§7Use §f/trackedit qualigrid clear " + trackName + " confirm §7to confirm.");
        }
    }

    @Subcommand("qualigrid list")
    @Description("Lists qualifying grid positions")
    @CommandCompletion("@tracks")
    public void onQualiGridList(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            List<GridPosition> positions = this.mysql.getQualiGridPositions(trackName);
            player.sendMessage("§e═══════════════════════════════════");
            player.sendMessage("§6§lQualifying grid of §f" + trackName);
            player.sendMessage("§e═══════════════════════════════════");
            if (positions.isEmpty()) {
                player.sendMessage("§7No positions configured.");
                player.sendMessage("§7Use §f/trackedit location qualigrid <id> " + trackName + " §7to add.");
            } else {
                player.sendMessage("§7Total: §f" + positions.size() + " positions");
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
    @Description("Tests qualifying grid positions")
    @CommandCompletion("@tracks")
    public void onQualiGridTest(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            List<GridPosition> positions = this.mysql.getQualiGridPositions(trackName);
            if (positions.isEmpty()) {
                player.sendMessage("§cNo qualifying grid positions configured for this track.");
            } else {
                player.sendMessage("§a✓ Testing qualifying grid of §e" + trackName + "§a...");
                for (int i = 0; i < positions.size(); ++i) {
                    GridPosition pos = positions.get(i);
                    int delay = i * 20;
                    SchedulerHelper.runTaskLater(this.plugin, () -> {
                        Location loc = pos.toLocation(this.plugin.getServer());
                        if (loc != null) {
                            SchedulerHelper.teleport(player, loc);
                            TitleHelper.sendThemedTitle(player, "&wQ" + pos.getPosition(), "§7" + this.formatLocation(loc), 5, 30, 10);
                        }
                    }, delay);
                }
            }
        }
    }

    @Subcommand("qualigrid info")
    @Description("Shows qualifying grid information")
    @CommandCompletion("@tracks")
    public void onQualiGridInfo(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            List<GridPosition> positions = this.mysql.getQualiGridPositions(trackName);
            player.sendMessage("§e═══════════════════════════════════");
            player.sendMessage("§6§lQualifying Grid Information: §f" + trackName);
            player.sendMessage("§e═══════════════════════════════════");
            player.sendMessage("§7Configured positions: §f" + positions.size());
            if (positions.isEmpty()) {
                player.sendMessage("§e⚠ Qualifying grid not configured");
                player.sendMessage("§7Qualifying will use the track spawn.");
                player.sendMessage("§7Use §f/trackedit location qualigrid <id> " + trackName + " §7to configure.");
            } else {
                player.sendMessage("§a✓ Qualifying grid configured manually");
                player.sendMessage("§7Use §f/trackedit qualigrid list " + trackName + " §7to view positions.");
                player.sendMessage("§7Use §f/trackedit qualigrid test " + trackName + " §7to test.");
            }
            player.sendMessage("§e═══════════════════════════════════");
        }
    }

    @Subcommand("pitstop entry")
    @Description("Adds the pit stop entry")
    @CommandCompletion("@tracks")
    public void onPitStopEntry(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            WorldEditSelect var10000 = this.worldEditSelect;
            if (!WorldEditSelect.hasSelection(player)) {
                player.sendMessage("§c✗ Make a WorldEdit selection first!");
            } else {
                Location min = WorldEditSelect.getMin(player);
                Location max = WorldEditSelect.getMax(player);
                this.plugin.getPitStopManager().addPitStopEntry(trackName, min, max);
                player.sendMessage("§a✓ Pit stop entry set for §e" + trackName + "§a!");
                String var10001 = this.formatLocation(min);
                player.sendMessage("§7Region: " + var10001 + " §7→ " + this.formatLocation(max));
            }
        }
    }

    @Subcommand("pitstop exit")
    @Description("Adds the pit stop exit")
    @CommandCompletion("@tracks")
    public void onPitStopExit(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            WorldEditSelect var10000 = this.worldEditSelect;
            if (!WorldEditSelect.hasSelection(player)) {
                player.sendMessage("§c✗ Make a WorldEdit selection first!");
            } else {
                Location min = WorldEditSelect.getMin(player);
                Location max = WorldEditSelect.getMax(player);
                this.plugin.getPitStopManager().addPitStopExit(trackName, min, max);
                player.sendMessage("§a✓ Pit stop exit set for §e" + trackName + "§a!");
                String var10001 = this.formatLocation(min);
                player.sendMessage("§7Region: " + var10001 + " §7→ " + this.formatLocation(max));
            }
        }
    }

    @Subcommand("pitstop area")
    @Description("Adds the pit stop AREA (Minigame Zone)")
    @CommandCompletion("@tracks")
    public void onPitStopArea(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            WorldEditSelect var10000 = this.worldEditSelect;
            if (!WorldEditSelect.hasSelection(player)) {
                player.sendMessage("§c✗ Make a WorldEdit selection first!");
            } else {
                Location min = WorldEditSelect.getMin(player);
                Location max = WorldEditSelect.getMax(player);
                this.plugin.getPitStopManager().addPitStopArea(trackName, min, max);
                player.sendMessage("§a✓ Pit stop area (Minigame) set for §e" + trackName + "§a!");
                String var10001 = this.formatLocation(min);
                player.sendMessage("§7Region: " + var10001 + " §7→ " + this.formatLocation(max));
                player.sendMessage("§eℹ Place YELLOW_GLAZED_TERRACOTTA blocks inside this area to activate the minigame.");
            }
        }
    }

    @Subcommand("pitstop start")
    @Description("Sets the lap validation line (START) inside the pit lane")
    @CommandCompletion("@tracks")
    public void onPitStopStart(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            WorldEditSelect var10000 = this.worldEditSelect;
            if (!WorldEditSelect.hasSelection(player)) {
                player.sendMessage("§c✗ Make a WorldEdit selection first!");
            } else {
                Location min = WorldEditSelect.getMin(player);
                Location max = WorldEditSelect.getMax(player);
                this.plugin.getPitStopManager().addPitStopStart(trackName, min, max);
                player.sendMessage("§a✓ Lap validation line (§eSTART§a) set for §e" + trackName + "§a!");
                String var10001 = this.formatLocation(min);
                player.sendMessage("§7Location: " + var10001 + " §7→ " + this.formatLocation(max));
                player.sendMessage("§bℹ This region will validate the driver's lap when they pass through it inside the pit.");
            }
        }
    }

    @Subcommand("pitstop remove")
    @Description("Removes the pit stop from a track")
    @CommandCompletion("@tracks")
    public void onPitStopRemove(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (!this.plugin.getPitStopManager().hasPitStop(trackName)) {
                player.sendMessage("§c✗ Track §f" + trackName + " §cdoes not have a pit stop region configured.");
            } else {
                if (this.plugin.getPitStopManager().removePitStop(trackName)) {
                    player.sendMessage("§a✓ Pit stop region removed from §e" + trackName + "§a!");
                } else {
                    player.sendMessage("§c✗ Error removing pit stop region.");
                }

            }
        }
    }

    @Subcommand("drs detect")
    @Description("Sets the DRS detection region")
    @CommandCompletion("@tracks")
    public void onDrsDetect(Player player, @Optional String trackNameArg) {
        this.saveDrsPart(player, "DETECT", trackNameArg);
    }

    @Subcommand("drs startdrs")
    @Description("Sets the DRS activation region")
    @CommandCompletion("@tracks")
    public void onDrsStart(Player player, @Optional String trackNameArg) {
        this.saveDrsPart(player, "DRS", trackNameArg);
    }

    @Subcommand("drs finishdrs")
    @Description("Sets the DRS deactivation region")
    @CommandCompletion("@tracks")
    public void onDrsFinish(Player player, @Optional String trackNameArg) {
        this.saveDrsPart(player, "END", trackNameArg);
    }

    @Subcommand("drs delete detect")
    @Description("Removes a detection region by ID")
    public void onDeleteDetect(Player player, Integer id) {
        this.deleteDrsById(player, id, "DETECT");
    }

    @Subcommand("drs delete startdrs")
    @Description("Removes an activation region by ID")
    public void onDeleteStart(Player player, Integer id) {
        this.deleteDrsById(player, id, "DRS");
    }

    @Subcommand("drs delete finishdrs")
    @Description("Removes a deactivation region by ID")
    public void onDeleteFinish(Player player, Integer id) {
        this.deleteDrsById(player, id, "END");
    }

    private void deleteDrsById(Player player, int id, String type) {
        // Call MySQL passing the unique row ID
        if (this.mysql.deleteDRSRegionByID(id)) {
            player.sendMessage("§a[DRS] Region ID §f#" + id + " (§e" + type + "§a) removida com sucesso!");
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0F, 2.0F);
        } else {
            player.sendMessage("§c[DRS] Could not find a region with ID §f#" + id);
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

        // Now send the data to MySQL for the new fr_drs table
        // Note that we pass 'type' so the database knows which part to update/insert
        if (this.mysql.saveDrsZone(trackName, type, min, max)) {
            player.sendMessage("§a[DRS] Part §f" + type.toUpperCase() + " §aset for track: §e" + trackName);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
        } else {
            player.sendMessage("§cError saving DRS region to database.");
        }
    }

    @Subcommand("pitstop list")
    @Description("Lists tracks with pit stop")
    public void onPitStopList(Player player) {
        Set<String> tracks = this.plugin.getPitStopManager().getTracksWithPitStop();
        if (tracks.isEmpty()) {
            player.sendMessage("§e⚠ No track has a pit stop region configured.");
        } else {
            player.sendMessage("§e═══════════════════════════════════");
            player.sendMessage("§6§lTracks with Pit Stop Region (" + tracks.size() + ")");
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
    @Description("Shows pit stop information")
    @CommandCompletion("@tracks")
    public void onPitStopInfo(Player player, @Optional String trackNameArg) {
        String trackName = this.getTargetTrack(player, trackNameArg);
        if (trackName != null) {
            if (!this.plugin.getPitStopManager().hasPitStop(trackName)) {
                player.sendMessage("§c✗ Track §f" + trackName + " §cdoes not have a pit stop region configured.");
            } else {
                PitStopRegion pitStop = this.plugin.getPitStopManager().getPitStop(trackName);
                player.sendMessage("§e═══════════════════════════════════");
                player.sendMessage("§6§lPit Stop Region: §f" + trackName);
                player.sendMessage("§e═══════════════════════════════════");
                player.sendMessage(pitStop.hasEntry() ? "§a✓ ENTRY: " + this.formatLocation(pitStop.getEntryCenter()) : "§c✗ ENTRY: not configured");
                player.sendMessage(pitStop.hasExit() ? "§a✓ EXIT: " + this.formatLocation(pitStop.getExitCenter()) : "§c✗ EXIT: not configured");
                player.sendMessage("");
                if (pitStop.hasArea()) {
                    String var10001 = this.formatLocation(pitStop.getAreaRegion().getMin());
                    player.sendMessage("§a✓ AREA: " + var10001);
                } else {
                    player.sendMessage("§c✗ AREA: not configured");
                }

                player.sendMessage("§e═══════════════════════════════════");
            }
        }
    }

    @Subcommand("pitstop test")
    @Description("Tests your position in the pit stop")
    public void onPitStopTest(Player player) {
        Location loc = player.getLocation();
        String entry = this.plugin.getPitStopManager().getPitStopEntryAtLocation(loc);
        String exit = this.plugin.getPitStopManager().getPitStopExitAtLocation(loc);
        if (entry != null) {
            player.sendMessage("§a✓ You are in the ENTRY of pit stop for track §e" + entry + "§a!");
        } else if (exit != null) {
            player.sendMessage("§a✓ You are in the EXIT of pit stop for track §e" + exit + "§a!");
        } else {
            player.sendMessage("§c✗ You are NOT in any pit stop region.");
        }

    }

    @Subcommand("location leaderboard java")
    @Description("Teleports the Java leaderboard hologram")
    @CommandCompletion("@tracks @nothing")
    public void onLocationLeaderboardJava(Player player, String trackName, @Optional Double x, @Optional Double y, @Optional Double z) {
        Location targetLocation = (x != null && y != null && z != null)
                ? new Location(player.getWorld(), x, y + 1.5, z)
                : player.getLocation();

        // Uses the unified method that returns the track's unique instance
        TrackLeaderboard leaderboard = this.plugin.getOrCreateLeaderboard(trackName, targetLocation);

        leaderboard.setLocation(targetLocation, "java");
        leaderboard.updateJavaLeaderboard(); // Updates specifically the Java side

        player.sendMessage("§aJAVA hologram for track §e" + trackName + " §ateleported!");
    }

    @Subcommand("location leaderboard bedrock")
    @Description("Teleports the Bedrock leaderboard hologram")
    @CommandCompletion("@tracks @nothing")
    public void onLocationLeaderboardBedrock(Player player, String trackName, @Optional Double x, @Optional Double y, @Optional Double z) {
        Location targetLocation = (x != null && y != null && z != null)
                ? new Location(player.getWorld(), x, y + 1.5, z)
                : player.getLocation();

        // Uses the SAME instance that Java would use
        TrackLeaderboard leaderboard = this.plugin.getOrCreateLeaderboard(trackName, targetLocation);

        leaderboard.setLocation(targetLocation, "bedrock");
        leaderboard.updateBedrockLeaderboard(); // Updates specifically the Bedrock side

        player.sendMessage("§aBEDROCK hologram for track §e" + trackName + " §ateleported!");
    }

    @Subcommand("togglehologram java")
    @Description("Toggles the Java leaderboard hologram on/off for a track")
    @CommandCompletion("@nothing @tracks")
    public void onToggleHologramJava(Player player, boolean enabled, @Optional String trackNameArg) {
        String targetTrack = getTargetTrack(player, trackNameArg);
        if (targetTrack == null) return;

        TrackLeaderboard leaderboard = this.plugin.getOrCreateLeaderboard(targetTrack, player.getLocation());
        leaderboard.setJavaEnabled(enabled);
        player.sendMessage("§aJava hologram for track §e" + targetTrack + " §a" + (enabled ? "enabled" : "disabled") + "!");
    }

    @Subcommand("togglehologram bedrock")
    @Description("Toggles the Bedrock leaderboard hologram on/off for a track")
    @CommandCompletion("@nothing @tracks")
    public void onToggleHologramBedrock(Player player, boolean enabled, @Optional String trackNameArg) {
        String targetTrack = getTargetTrack(player, trackNameArg);
        if (targetTrack == null) return;

        TrackLeaderboard leaderboard = this.plugin.getOrCreateLeaderboard(targetTrack, player.getLocation());
        leaderboard.setBedrockEnabled(enabled);
        player.sendMessage("§aBedrock hologram for track §e" + targetTrack + " §a" + (enabled ? "enabled" : "disabled") + "!");
    }

    @Subcommand("location startline")
    @Description("Sets the start line")
    public void onLocationStartLine(Player player) {
        player.sendMessage("§e'startline' function not yet implemented.");
    }

    @Subcommand("export")
    @Description("Exports a track to .trackexchange format")
    @CommandCompletion("@tracks")
    public void onExport(Player player, String trackName, @Optional String fileName) {
        String targetTrack = getTargetTrack(player, trackName);
        if (targetTrack == null) return;
        player.sendMessage("§eExporting track '" + targetTrack + "'...");
        trackExchange.exportTrack(player, targetTrack, fileName);
    }

    @Subcommand("import")
    @Description("Imports a track from a .trackexchange file")
    @CommandCompletion("@trackexchangeFiles")
    public void onImport(Player player, String fileName, @Optional String newName) {
        player.sendMessage("§eImporting track from '" + fileName + "'...");
        trackExchange.importTrack(player, fileName, newName);
    }

    public void applyGroupMode(String track, BoatUtilsGroupMode mode) {
        if (mode != TrackEditorCommand.BoatUtilsGroupMode.BA && mode != TrackEditorCommand.BoatUtilsGroupMode.BA_NOFD) {
            if (mode != TrackEditorCommand.BoatUtilsGroupMode.BA_BLUE_NOFD && mode != TrackEditorCommand.BoatUtilsGroupMode.BA_BLUE) {
                this.mysql.replaceAllBoatUtilsSettings(track, mode.stepHeight, mode.slipperiness, !mode.noFallDamage, mode.waterElevation, mode.airControl, mode.jumpForce == null ? 0.0F : mode.jumpForce, (double)-0.04F, 1.0F, 0.04F, 0.005F, 0.005F, true, true, true, 0, true, 0.0F, 0, false, false, 5, (String)null, (String)null, 0.0F, 1, 1.0F, 1.0F, false, 1.0F, 1.0F, false, -1.0F, 0.0F, false);
            } else {
                this.mysql.replaceAllBoatUtilsSettings(track, mode.stepHeight, mode.slipperiness, !mode.noFallDamage, mode.waterElevation, mode.airControl, mode.jumpForce == null ? 0.0F : mode.jumpForce, (double)-0.04F, 1.0F, 0.04F, 0.005F, 0.005F, true, true, true, 0, true, 0.0F, 0, false, false, 5, "minecraft:air;0.989", (String)null, 0.0F, 1, 1.0F, 1.0F, false, 1.0F, 1.0F, false, -1.0F, 0.0F, false);
            }
        } else {
            this.mysql.replaceAllBoatUtilsSettings(track, mode.stepHeight, mode.slipperiness, !mode.noFallDamage, mode.waterElevation, mode.airControl, mode.jumpForce == null ? 0.0F : mode.jumpForce, (double)-0.04F, 1.0F, 0.04F, 0.005F, 0.005F, true, true, true, 0, true, 0.0F, 0, false, false, 5, "minecraft:air;0.98", (String)null, 0.0F, 1, 1.0F, 1.0F, false, 1.0F, 1.0F, false, -1.0F, 0.0F, false);
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



