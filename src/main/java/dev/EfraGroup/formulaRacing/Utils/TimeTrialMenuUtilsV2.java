package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.APIFormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.PacketSender;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

public class TimeTrialMenuUtilsV2 implements Listener {

    private final FormulaRacing plugin;
    private final PacketSender ps;
    private final DatabaseManager mysql;
    private final APIFormulaRacing api;
    private final TimerUtils timerUtils;
    private final ScoreboardTimeTrialUtils stt;
    private final Map<UUID, PlayerMenuSession> sessions = new HashMap<
        UUID,
        PlayerMenuSession
    >();
    private final Map<UUID, Long> lastClickTime = new HashMap<UUID, Long>();

    public TimeTrialMenuUtilsV2(
        FormulaRacing plugin,
        DatabaseManager mysql,
        APIFormulaRacing api,
        PacketSender ps,
        TimerUtils timerUtils,
        ScoreboardTimeTrialUtils stt
    ) {
        this.plugin = plugin;
        this.mysql = mysql;
        this.api = api;
        this.ps = ps;
        this.timerUtils = timerUtils;
        this.stt = stt;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        // Detecta Bedrock na thread principal (Floodgate exige thread segura).
        final boolean bedrock = plugin.isBedrockPlayer(player);
        SchedulerHelper.runAsync(plugin, () -> {
            try {
                // Fixed: Defining types for the Map returned by MySQL
                Map<String, DatabaseManager.TrackData> tracksData =
                    mysql.getAllTracksWithData();
                List<TrackMenuInfo> loadedTracks = new ArrayList<>();

                // Batch load ALL WRs and PBs in 2 queries instead of 900+ individual queries
                Map<String, Double> allWRs = mysql.getAllBestTimes();
                Map<String, Double> allPBs = mysql.getPlayerAllBestTimes(player.getName());
                Map<String, String> allDifficulties = mysql.getAllTrackDifficulties();
                Map<String, Integer> allTimeCounts = mysql.getAllTrackTimeCounts();
                Map<String, DatabaseManager.TrackIconData> allIconData = mysql.getAllTrackIconData();
                Map<String, List<String>> allTags = mysql.getAllTrackTags();

                // Now the loop can iterate correctly with defined types
                for (Map.Entry<
                    String,
                    DatabaseManager.TrackData
                > entry : tracksData.entrySet()) {
                    String trackName = entry.getKey();

                    DatabaseManager.TrackData data = entry.getValue();

                    // Skip tracks that are not open (flag já vem na query principal, sem query extra)
                    if (!data.isOpen()) continue;

                    // Bedrock: mostra só pistas sem boatutils (OBU não funciona no Bedrock)
                    String trackWS = trackName.replaceAll("\\s+", "");
                    if (bedrock && this.mysql.trackHaveBoatUtils(trackWS)) continue;

                    String icon = data.getIconName();
                    String trackNameWS = trackName.replaceAll("\\s+", "").toLowerCase();
                    Double wr = allWRs.get(trackNameWS);
                    Double pb = allPBs.get(trackNameWS);
                    String difficulty = allDifficulties.getOrDefault(trackNameWS, "");
                    int timeCount = allTimeCounts.getOrDefault(trackNameWS, 0);
                    List<String> tags = allTags.getOrDefault(trackNameWS, Collections.emptyList());
                    DatabaseManager.TrackIconData iconData = allIconData.getOrDefault(
                        trackNameWS,
                        new DatabaseManager.TrackIconData(icon, 1, null)
                    );

                    loadedTracks.add(
                        new TrackMenuInfo(
                            trackName,
                            data,
                            iconData,
                            wr,
                            pb,
                            difficulty,
                            timeCount,
                            tags
                        )
                    );
                }

                // Menu session setup
                PlayerMenuSession session = new PlayerMenuSession();
                session.allTracksRaw = loadedTracks;
                this.applySortAndFilter(session);

                // Back to the main thread (Sync) to open the inventory
                SchedulerHelper.runTaskFor(this.plugin, player, () -> {
                    this.sessions.put(player.getUniqueId(), session);
                    this.captureAndOpenMenu(player, session);
                });
            } catch (Exception e) {
                this.plugin.getDebugManager().logRaceSystem(
                    "Error loading menu for " +
                        player.getName() +
                        ": " +
                        e.getMessage()
                );
                player.sendMessage("§cError loading track data.");
            }
        });
    }

    private void captureAndOpenMenu(Player player, PlayerMenuSession session) {
        PlayerInventory inventory = player.getInventory();
        session.storedInventory = new StoredInventory(
            inventory,
            player.getItemOnCursor()
        );
        inventory.clear();
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(null);
        player.setItemOnCursor(null);
        this.openPage(player);
    }

    private void restoreInventory(Player player, PlayerMenuSession session) {
        StoredInventory storedInventory = session.storedInventory;
        if (storedInventory == null) {
            return;
        }
        StoredInventory snapshot = storedInventory;
        SchedulerHelper.runTaskFor(this.plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            PlayerInventory inventory = player.getInventory();
            inventory.clear();
            inventory.setStorageContents(snapshot.storageContents);
            inventory.setArmorContents(snapshot.armorContents);
            inventory.setItemInOffHand(snapshot.offHandItem);
            player.setItemOnCursor(snapshot.cursorItem);
        });
    }

    private void discardStoredInventory(UUID uuid) {
        PlayerMenuSession session = this.sessions.remove(uuid);
        if (session != null) {
            session.storedInventory = null;
        }
    }

    private void openPage(Player player) {
        PlayerMenuSession session = this.sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.refreshing = true;
        String langCode = this.mysql.getPlayerLanguage(player.getUniqueId());
        int itemsPerPage = 45;
        int totalItems = session.currentView.size();
        int totalPages = (int) Math.ceil(
            (double) totalItems / (double) itemsPerPage
        );
        if (totalPages == 0) {
            totalPages = 1;
        }
        if (session.page < 0) {
            session.page = 0;
        }
        if (session.page >= totalPages) {
            session.page = totalPages - 1;
        }
        String title = "Tracks";
        Inventory inv = Bukkit.createInventory(
            (InventoryHolder) new TimeTrialMenuHolder(),
            (int) 54,
            (String) title
        );
        int startIndex = session.page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, totalItems);
        for (int i = startIndex; i < endIndex; ++i) {
            TrackMenuInfo info = session.currentView.get(i);
            inv.setItem(i - startIndex, this.createTrackItem(info, langCode));
        }
        // ---- Bottom control bar (slots 45-53) ----
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.setDisplayName(" ");
        filler.setItemMeta(fillerMeta);
        for (int i = 45; i < 54; ++i) {
            inv.setItem(i, filler);
        }

        // Navigation
        if (session.page > 0) {
            inv.setItem(45, this.createControlItem(
                Material.ARROW,
                "\u00a7a\u00a7l\u25c4 Previous Page",
                Arrays.asList(
                    "\u00a77Page \u00a7e" + session.page + "\u00a77/\u00a7f" + totalPages,
                    "\u00a78Go back one page.",
                    "",
                    "\u00a7eClick to go back"
                )
            ));
        }
        if (session.page < totalPages - 1) {
            inv.setItem(53, this.createControlItem(
                Material.ARROW,
                "\u00a7a\u00a7lNext Page \u25ba",
                Arrays.asList(
                    "\u00a77Page \u00a7e" + (session.page + 2) + "\u00a77/\u00a7f" + totalPages,
                    "\u00a78Advance one page.",
                    "",
                    "\u00a7eClick to continue"
                )
            ));
        }

        // Sort / filter
        List<String> sortLore = Arrays.asList(
            "\u00a77Current: \u00a7e" + session.sort.label,
            "\u00a78Reorders the track list.",
            "",
            "\u00a7eClick to cycle the order"
        );
        inv.setItem(48, this.createControlItem(
            session.sort.icon,
            "\u00a76\u00a7lSorting",
            sortLore
        ));
        List<String> filterLore = Arrays.asList(
            "\u00a77Showing: \u00a7e" + session.filter.label,
            "\u00a78Hides tracks you don't need.",
            "",
            "\u00a7eClick to cycle the filter"
        );
        inv.setItem(50, this.createControlItem(
            session.filter.icon,
            "\u00a76\u00a7lFilter",
            filterLore
        ));

        // Page info (centre)
        int firstShown = totalItems == 0 ? 0 : startIndex + 1;
        inv.setItem(49, this.createControlItem(
            Material.PAPER,
            "\u00a7b\u00a7lTrack Browser",
            Arrays.asList(
                "\u00a77Page: \u00a7e" + (session.page + 1) + "\u00a77/\u00a7f" + totalPages,
                "\u00a77Showing: \u00a7e" + firstShown + "-\u00a7e" + endIndex + "\u00a77/\u00a7f" + totalItems,
                "",
                "\u00a77Sort: \u00a7e" + session.sort.label,
                "\u00a77Filter: \u00a7e" + session.filter.label
            )
        ));

        // Random track
        inv.setItem(47, this.createControlItem(
            Material.ENDER_EYE,
            "\u00a7d\u00a7lRandom Track",
            Arrays.asList(
                "\u00a77Picks a track at random from",
                "\u00a77the current list and starts it.",
                "",
                "\u00a7eClick to play a random track"
            )
        ));

        // Close
        inv.setItem(52, this.createControlItem(
            Material.BARRIER,
            "\u00a7c\u00a7lClose",
            Arrays.asList(
                "\u00a77Close this menu.",
                "",
                "\u00a7eClick to close"
            )
        ));

        player.openInventory(inv);
        session.refreshing = false;
    }

    private void applySortAndFilter(PlayerMenuSession session) {
        // 1. Filtering
        session.currentView = session.allTracksRaw
            .stream()
            .filter(t -> {
                if (session.filter == FilterType.COMPLETED) {
                    return t.playerBestTime != null;
                }
                if (session.filter == FilterType.NOT_PLAYED) {
                    return t.playerBestTime == null;
                }
                return true;
            })
            .collect(Collectors.toList());

        // 2. Sorting (Refactored for clarity and compatibility)
        Comparator<TrackMenuInfo> comparator;

        switch (session.sort.ordinal()) {
            case 1: // Name Z-A
                comparator = (t1, t2) ->
                    t2.trackName.compareToIgnoreCase(t1.trackName);
                break;
            case 2: // Best Personal Time (PB)
                comparator = Comparator.comparingDouble(t ->
                    t.playerBestTime == null
                        ? Double.MAX_VALUE
                        : t.playerBestTime
                );
                break;
            case 3: // World Record (WR)
                comparator = Comparator.comparingDouble(t ->
                    t.worldRecordTime == null
                        ? Double.MAX_VALUE
                        : t.worldRecordTime
                );
                break;
            default: // Name A-Z (Default)
                comparator = (t1, t2) ->
                    t1.trackName.compareToIgnoreCase(t2.trackName);
                break;
        }

        session.currentView.sort(comparator);

        // 3. Reset to the first page after changing filter/sort
        session.page = 0;
    }

    private ItemStack createTrackItem(TrackMenuInfo info, String langCode) {
        List<String> lore = new ArrayList<>();
        String difficulty = info.difficulty == null || info.difficulty.isBlank()
            ? "UNKNOWN"
            : info.difficulty.toUpperCase();
        if (difficulty.equals("EXTREME")) {
            difficulty = "INSANE";
        }
        lore.add(getDifficultyColor(difficulty) + difficulty + " §7▶ §a" + info.timeCount);
        lore.add(
            "§7by §f" + (info.trackData.getOwnerName() == null
                ? "Unknown"
                : info.trackData.getOwnerName())
        );

        if (!info.tags.isEmpty()) {
            lore.add("");
            lore.add(
                info.tags.stream()
                    .map(tag -> "§e" + ChatColor.translateAlternateColorCodes('&', tag))
                    .collect(Collectors.joining("§7, "))
            );
        }

        lore.add("");
        lore.add("§a▶ Click to play!");

        // Name/lore are applied on the same ItemMeta as the icon's block state
        // (e.g. LIGHT level), so the level survives the menu round-trip.
        return info.iconData.toItemStack("§f§l" + info.trackName, lore);
    }

    private String getDifficultyColor(String difficulty) {
        return switch (difficulty) {
            case "EASY" -> "§a";
            case "MEDIUM" -> "§e";
            case "HARD" -> "§c";
            case "INSANE" -> "§5";
            default -> "§7";
        };
    }

    private ItemStack createControlItem(
        Material mat,
        String name,
        List<String> lore
    ) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) {
            meta.setLore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createControlItem(Material mat, String name) {
        return this.createControlItem(mat, name, null);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof TimeTrialMenuHolder) {
            HumanEntity who = e.getPlayer();
            PlayerMenuSession session = this.sessions.get(who.getUniqueId());
            if (session != null && session.refreshing) {
                return;
            }
            if (session != null && session.trackSelected) {
                return;
            }
            this.sessions.remove(who.getUniqueId());
            if (session != null && who instanceof Player player) {
                this.restoreInventory(player, session);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (
            !(event.getInventory().getHolder() instanceof TimeTrialMenuHolder)
        ) {
            return;
        }
        if (
            event.getClickedInventory() == null ||
            event.getClickedInventory() != event.getView().getTopInventory()
        ) {
            return;
        }
        event.setCancelled(true);
        HumanEntity humanEntity = event.getWhoClicked();
        if (!(humanEntity instanceof Player)) {
            return;
        }
        Player player = (Player) humanEntity;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        ItemMeta clickedMeta = clicked.getItemMeta();
        if (clickedMeta == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        PlayerMenuSession session = this.sessions.get(uuid);
        if (session == null) {
            player.closeInventory();
            return;
        }
        int slot = event.getSlot();
        if (slot >= 45) {
            player.playSound(
                player.getLocation(),
                Sound.UI_BUTTON_CLICK,
                1.0f,
                1.0f
            );
            switch (slot) {
                case 45: {
                    if (session.page <= 0) break;
                    --session.page;
                    this.openPage(player);
                    break;
                }
                case 53: {
                    int totalItems = session.currentView.size();
                    int maxPages = (int) Math.ceil((double) totalItems / 45.0);
                    if (session.page >= maxPages - 1) break;
                    ++session.page;
                    this.openPage(player);
                    break;
                }
                case 47: {
                    if (session.currentView.isEmpty()) break;
                    TrackMenuInfo random = session.currentView.get(
                        ThreadLocalRandom.current().nextInt(session.currentView.size())
                    );
                    session.trackSelected = true;
                    player.closeInventory();
                    this.startTrackFromMenu(player, random.trackName, session);
                    break;
                }
                case 48: {
                    session.sort = session.sort.next();
                    this.applySortAndFilter(session);
                    this.openPage(player);
                    break;
                }
                case 50: {
                    session.filter = session.filter.next();
                    this.applySortAndFilter(session);
                    this.openPage(player);
                    break;
                }
                case 52: {
                    player.closeInventory();
                    break;
                }
            }
            return;
        }
        long now = System.currentTimeMillis();
        Long lastClick = this.lastClickTime.get(uuid);
        if (lastClick != null && now - lastClick < 500L) {
            return;
        }
        this.lastClickTime.put(uuid, now);
        session.trackSelected = true;
        String trackName = ChatColor.stripColor(
            (String) clickedMeta.getDisplayName()
        );
        player.closeInventory();
        this.startTrackFromMenu(player, trackName, session);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        this.sessions.remove(uuid);
        this.lastClickTime.remove(uuid);
    }

    private void startTrackFromMenu(
        Player player,
        String trackName,
        PlayerMenuSession session
    ) {
        UUID uuid = player.getUniqueId();

        if (this.plugin.getTimeTrialDuels() != null && this.plugin.getTimeTrialDuels().isPlayerInDuel(uuid)) {
            this.plugin.sendMessage(player, "tt_error_duel_active");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            this.restoreAfterFailedSelection(player, session);
            return;
        }
        if (this.plugin.getQuickRaceManager() != null && this.plugin.getQuickRaceManager().isPlayerInActiveRace(uuid)) {
            this.plugin.sendMessage(player, "tt_error_quickrace");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            this.restoreAfterFailedSelection(player, session);
            return;
        }
        if (this.plugin.getRaceEventManager() != null && this.plugin.getRaceEventManager().getPlayerActiveHeat(uuid).isPresent()) {
            this.plugin.sendMessage(player, "tt_error_event");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            this.restoreAfterFailedSelection(player, session);
            return;
        }

        String lastTrack = this.plugin.getLastTimeTrialTrack(uuid);
        if (lastTrack != null) {
            TimerUtils.PlayerTimerData data = this.timerUtils.getTimerData(
                player,
                lastTrack
            );
            if (data != null) {
                double elapsedTime =
                    this.timerUtils.getPlayerElapsedTimeUntilLastCheckpoint(
                        player,
                        lastTrack
                    );
                int checkpoints = data.getCheckpointsReached();
                if (checkpoints > 0) {
                    this.mysql.savePartialTime(
                        uuid,
                        player.getName(),
                        lastTrack,
                        elapsedTime,
                        checkpoints
                    );
                }
            }
        }

        if (
            this.mysql.trackHaveBoatUtils(trackName) &&
            !FormulaRacing.hasOpenBoatUtilsMod(player)
        ) {
            this.plugin.sendMessage(
                player,
                "obu_mandatory_warning",
                "{track}",
                trackName
            );
            this.restoreAfterFailedSelection(player, session);
            return;
        }

        this.ps.sendBoatSetting(player, 0, new Object[0]);
        this.ps.applyBoatUtilsToPlayer(player, trackName);

        Location loc = this.mysql.getTrackSpawn(trackName);
        if (loc == null) {
            player.sendMessage("\u00a7cSpawn not found.");
            this.restoreAfterFailedSelection(player, session);
            return;
        }

        if (!this.mysql.getTimeTrialEnabled(uuid)) {
            this.mysql.setTimeTrialEnabled(uuid, true);
            this.plugin.sendMessage(player, "tt_auto_enabled", new String[0]);
        }

        this.timerUtils.stopTimer(player);
        if (this.plugin.getTimeTrialController() != null) {
            this.plugin.getTimeTrialController().endSession(player);
        }
        if (this.plugin.getWolfTimingService() != null) {
            this.plugin.getWolfTimingService().prepareTrack(player, trackName);
        }

        this.plugin.setLastTimeTrialTrack(uuid, trackName);
        this.plugin.getDebugManager().logTimeTrialSystem(
            "[TT] Starting track '" +
                trackName +
                "' for player " +
                player.getName()
        );
        this.plugin.sendMessage(
            player,
            "timetrial_teleport",
            new String[] { "{track}", trackName }
        );

        try {
            String ownerName = this.mysql.getTrackOwner(trackName);
            this.stt.setPlayerTrack(player, trackName, ownerName);
        } catch (Exception e) {
            this.plugin.getDebugManager().logTimeTrialSystem("[ERROR] Failed to set player track for scoreboard: " + e.getMessage());
        }

        this.api.recoverPlayerBoatState(player);
        SchedulerHelper.teleportAsync(player, loc).thenAccept(success -> {
            if (Boolean.TRUE.equals(success)) {
                this.api.spawnBoatAt(player, loc, false, false, false);
                this.plugin.getHotbarController().giveTimeTrialHotbar(player);
                this.discardStoredInventory(uuid);
            } else {
                this.restoreAfterFailedSelection(player, session);
            }
        });
    }

    private void restoreAfterFailedSelection(Player player, PlayerMenuSession session) {
        this.sessions.remove(player.getUniqueId());
        this.lastClickTime.remove(player.getUniqueId());
        this.restoreInventory(player, session);
        session.storedInventory = null;
    }

    private static final class StoredInventory {

        private final ItemStack[] storageContents;
        private final ItemStack[] armorContents;
        private final ItemStack offHandItem;
        private final ItemStack cursorItem;

        private StoredInventory(
            PlayerInventory inventory,
            ItemStack cursorItem
        ) {
            this.storageContents = cloneItems(inventory.getStorageContents());
            this.armorContents = cloneItems(inventory.getArmorContents());
            this.offHandItem = inventory.getItemInOffHand() == null
                ? null
                : inventory.getItemInOffHand().clone();
            this.cursorItem = cursorItem == null ? null : cursorItem.clone();
        }

        private static ItemStack[] cloneItems(ItemStack[] items) {
            ItemStack[] cloned = new ItemStack[items.length];
            for (int i = 0; i < items.length; i++) {
                if (items[i] != null) {
                    cloned[i] = items[i].clone();
                }
            }
            return cloned;
        }
    }

    private String formatTime(double time) {
        int minutes = (int) (time / 60.0);
        double seconds = time % 60.0;
        return String.format("%d:%06.3f", minutes, seconds);
    }

    private static class PlayerMenuSession {

        int page = 0;
        SortType sort = SortType.NAME_AZ;
        FilterType filter = FilterType.ALL;
        List<TrackMenuInfo> allTracksRaw = new ArrayList<TrackMenuInfo>();
        List<TrackMenuInfo> currentView = new ArrayList<TrackMenuInfo>();
        // Set while we reopen the inventory to switch pages/sort/filter, so the
        // close event from the old inventory doesn't wipe the session.
        volatile boolean refreshing = false;
        volatile boolean trackSelected = false;
        StoredInventory storedInventory;

        private PlayerMenuSession() {}
    }

    private static class TimeTrialMenuHolder implements InventoryHolder {

        private TimeTrialMenuHolder() {}

        public Inventory getInventory() {
            return null;
        }
    }

    private record TrackMenuInfo(
        String trackName,
        DatabaseManager.TrackData trackData,
        DatabaseManager.TrackIconData iconData,
        Double worldRecordTime,
        Double playerBestTime,
        String difficulty,
        int timeCount,
        List<String> tags
    ) {}

    public static enum SortType {
        NAME_AZ("A-Z", Material.NAME_TAG),
        NAME_ZA("Z-A", Material.NAME_TAG),
        BEST_TIME("Best Time (PB)", Material.CLOCK),
        WORLD_RECORD("World Record (WR)", Material.GOLDEN_APPLE);

        final String label;
        final Material icon;

        private SortType(String label, Material icon) {
            this.label = label;
            this.icon = icon;
        }

        public SortType next() {
            int nextIndex = (this.ordinal() + 1) % SortType.values().length;
            return SortType.values()[nextIndex];
        }
    }

    public static enum FilterType {
        ALL("All", Material.COMPASS),
        COMPLETED("With Time (PB)", Material.WRITTEN_BOOK),
        NOT_PLAYED("No Time", Material.MAP);

        final String label;
        final Material icon;

        private FilterType(String label, Material icon) {
            this.label = label;
            this.icon = icon;
        }

        public FilterType next() {
            int nextIndex = (this.ordinal() + 1) % FilterType.values().length;
            return FilterType.values()[nextIndex];
        }
    }
}

