package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.APIFormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import dev.EfraGroup.formulaRacing.PacketSender;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
        SchedulerHelper.runAsync(plugin, () -> {
            try {
                // Fixed: Defining types for the Map returned by MySQL
                Map<String, DatabaseManager.TrackData> tracksData =
                    mysql.getAllTracksWithData();
                List<TrackMenuInfo> loadedTracks = new ArrayList<>();

                // Now the loop can iterate correctly with defined types
                for (Map.Entry<
                    String,
                    DatabaseManager.TrackData
                > entry : tracksData.entrySet()) {
                    String trackName = entry.getKey();

                    // Skip tracks that are not open
                    if (!this.mysql.isTrackOpen(trackName)) continue;

                    DatabaseManager.TrackData data = entry.getValue();
                    String icon = data.getIconName();
                    Double wr = mysql.getBestTime(trackName);

                    // Fetch the player's Personal Best (PB)
                    Object[] pbData = this.mysql.getPlayerBestTime(
                        player.getName(),
                        trackName
                    );
                    Double pb = (pbData != null) ? (Double) pbData[0] : null;

                    int pos = -1; // Default position (can be calculated later in sort)

                    loadedTracks.add(
                        new TrackMenuInfo(trackName, data, icon, wr, pb, pos)
                    );
                }

                // Menu session setup
                PlayerMenuSession session = new PlayerMenuSession();
                session.allTracksRaw = loadedTracks;
                this.applySortAndFilter(session);

                // Back to the main thread (Sync) to open the inventory
                SchedulerHelper.runTask(this.plugin, () -> {
                    this.sessions.put(player.getUniqueId(), session);
                    this.openPage(player);
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
        String title =
            "Time Trial (" + (session.page + 1) + "/" + totalPages + ")";
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
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);
        for (int i = 45; i < 54; ++i) {
            inv.setItem(i, glass);
        }
        if (session.page > 0) {
            inv.setItem(
                45,
                this.createControlItem(
                    Material.ARROW,
                    "\u00a7a\u25c4 Previous Page"
                )
            );
        }
        List<String> sortLore = Arrays.asList(
            "\u00a77Current: \u00a7e" + session.sort.label,
            "",
            "\u00a7eClick to change!"
        );
        inv.setItem(
            48,
            this.createControlItem(
                session.sort.icon,
                "\u00a76Sorting",
                sortLore
            )
        );
        List<String> filterLore = Arrays.asList(
            "\u00a77Showing: \u00a7e" + session.filter.label,
            "",
            "\u00a7eClick to change!"
        );
        inv.setItem(
            50,
            this.createControlItem(
                session.filter.icon,
                "\u00a7bFilter",
                filterLore
            )
        );
        if (session.page < totalPages - 1) {
            inv.setItem(
                53,
                this.createControlItem(
                    Material.ARROW,
                    "\u00a7aNext Page \u25ba"
                )
            );
        }
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
        Material mat;
        try {
            String icon = info.iconName;
            if (icon == null || icon.isBlank() || "N/A".equalsIgnoreCase(icon)) {
                mat = Material.PAPER;
            } else {
                mat = Material.valueOf(icon.toUpperCase());
            }
        } catch (Exception e) {
            mat = Material.PAPER;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§f§l" + info.trackName);

            // FIX: Using List<String> instead of ArrayList<Object>
            List<String> lore = new ArrayList<>();

            String owner = info.trackData.getOwnerName();
            lore.add("§7Owner: §e" + (owner != null ? owner : "Unknown"));
            lore.add("");

            String pb = (info.playerBestTime == null)
                ? "§c---"
                : "§a" + this.formatTime(info.playerBestTime);
            lore.add("§fMeu Tempo: " + pb);

            String wr = (info.worldRecordTime == null)
                ? "§c---"
                : "§6" + this.formatTime(info.worldRecordTime);
            lore.add("§fWorld Record: " + wr);

            lore.add("");
            lore.add("§eClick to race!");

            // Now the compiler accepts the lore correctly
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
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
            // Ignore the close event fired when we reopen to change pages/sort/filter.
            if (session != null && session.refreshing) {
                return;
            }
            this.sessions.remove(who.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (
            !(event.getInventory().getHolder() instanceof TimeTrialMenuHolder)
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
        String trackName = ChatColor.stripColor(
            (String) clickedMeta.getDisplayName()
        );
        player.closeInventory();
        this.startTrackFromMenu(player, trackName);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        this.sessions.remove(uuid);
        this.lastClickTime.remove(uuid);
    }

    private void startTrackFromMenu(Player player, String trackName) {
        UUID uuid = player.getUniqueId();

        if (this.plugin.getTimeTrialDuels() != null && this.plugin.getTimeTrialDuels().isPlayerInDuel(uuid)) {
            this.plugin.sendMessage(player, "tt_error_duel_active");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            return;
        }
        if (this.plugin.getQuickRaceManager() != null && this.plugin.getQuickRaceManager().isPlayerInActiveRace(uuid)) {
            this.plugin.sendMessage(player, "tt_error_quickrace");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
            return;
        }
        if (this.plugin.getRaceEventManager() != null && this.plugin.getRaceEventManager().getPlayerActiveHeat(uuid).isPresent()) {
            this.plugin.sendMessage(player, "tt_error_event");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
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
            return;
        }

        this.ps.sendBoatSetting(player, 0, new Object[0]);
        this.ps.applyBoatUtilsToPlayer(player, trackName);

        Location loc = this.mysql.getTrackSpawn(trackName);
        if (loc == null) {
            player.sendMessage("\u00a7cSpawn not found.");
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
            }
        });
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
        String iconName,
        Double worldRecordTime,
        Double playerBestTime,
        int playerPos
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

