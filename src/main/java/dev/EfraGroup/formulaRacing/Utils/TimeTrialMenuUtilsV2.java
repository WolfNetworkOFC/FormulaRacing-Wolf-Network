/*
 * Decompiled with CFR 0.153-SNAPSHOT (d6f6758-dirty).
 *
 * Could not load the following classes:
 *  dev.EfraGroup.formulaRacing.Database.DatabaseManager
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.Sound
 *  org.bukkit.entity.HumanEntity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.plugin.Plugin
 */
package dev.EfraGroup.formulaRacing.Utils;

import dev.EfraGroup.formulaRacing.APIFormulaRacing;
import dev.EfraGroup.formulaRacing.Database.DatabaseManager;
import dev.EfraGroup.formulaRacing.FormulaRacing;
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
                // Corrigido: Definindo os tipos para o Map retornado pelo MySQL
                Map<String, DatabaseManager.TrackData> tracksData =
                    mysql.getAllTracksWithData();
                List<TrackMenuInfo> loadedTracks = new ArrayList<>();

                // Agora o loop consegue iterar corretamente com tipos definidos
                for (Map.Entry<
                    String,
                    DatabaseManager.TrackData
                > entry : tracksData.entrySet()) {
                    String trackName = entry.getKey();

                    // Pula pistas que não estão abertas
                    if (!this.mysql.isTrackOpen(trackName)) continue;

                    DatabaseManager.TrackData data = entry.getValue();
                    String icon = mysql.getIcon(trackName);
                    Double wr = mysql.getBestTime(trackName);

                    // Busca o Personal Best (PB) do jogador
                    Object[] pbData = this.mysql.getPlayerBestTime(
                        player.getName(),
                        trackName
                    );
                    Double pb = (pbData != null) ? (Double) pbData[0] : null;

                    int pos = -1; // Posição padrão (pode ser calculada depois no sort)

                    loadedTracks.add(
                        new TrackMenuInfo(trackName, data, icon, wr, pb, pos)
                    );
                }

                // Configuração da sessão do menu
                PlayerMenuSession session = new PlayerMenuSession();
                session.allTracksRaw = loadedTracks;
                this.applySortAndFilter(session);

                // Volta para a Thread Principal (Sync) para abrir o inventário
                SchedulerHelper.runTask(this.plugin, () -> {
                    this.sessions.put(player.getUniqueId(), session);
                    this.openPage(player);
                });
            } catch (Exception e) {
                this.plugin.getDebugManager().logRaceSystem(
                    "Erro ao carregar menu para " +
                        player.getName() +
                        ": " +
                        e.getMessage()
                );
                player.sendMessage("§cErro ao carregar os dados das pistas.");
            }
        });
    }

    private void openPage(Player player) {
        PlayerMenuSession session = this.sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
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
                    "\u00a7a\u25c4 P\u00e1gina Anterior"
                )
            );
        }
        List<String> sortLore = Arrays.asList(
            "\u00a77Atual: \u00a7e" + session.sort.label,
            "",
            "\u00a7eClique para alterar!"
        );
        inv.setItem(
            48,
            this.createControlItem(
                session.sort.icon,
                "\u00a76Ordena\u00e7\u00e3o",
                sortLore
            )
        );
        List<String> filterLore = Arrays.asList(
            "\u00a77Mostrando: \u00a7e" + session.filter.label,
            "",
            "\u00a7eClique para alterar!"
        );
        inv.setItem(
            50,
            this.createControlItem(
                session.filter.icon,
                "\u00a7bFiltro",
                filterLore
            )
        );
        if (session.page < totalPages - 1) {
            inv.setItem(
                53,
                this.createControlItem(
                    Material.ARROW,
                    "\u00a7aPr\u00f3xima P\u00e1gina \u25ba"
                )
            );
        }
        player.openInventory(inv);
    }

    private void applySortAndFilter(PlayerMenuSession session) {
        // 1. Filtragem
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

        // 2. Ordenação (Refatorada para clareza e compatibilidade)
        Comparator<TrackMenuInfo> comparator;

        switch (session.sort.ordinal()) {
            case 1: // Nome Z-A
                comparator = (t1, t2) ->
                    t2.trackName.compareToIgnoreCase(t1.trackName);
                break;
            case 2: // Melhor Tempo Pessoal (PB)
                comparator = Comparator.comparingDouble(t ->
                    t.playerBestTime == null
                        ? Double.MAX_VALUE
                        : t.playerBestTime
                );
                break;
            case 3: // Recorde Mundial (WR)
                comparator = Comparator.comparingDouble(t ->
                    t.worldRecordTime == null
                        ? Double.MAX_VALUE
                        : t.worldRecordTime
                );
                break;
            default: // Nome A-Z (Padrão)
                comparator = (t1, t2) ->
                    t1.trackName.compareToIgnoreCase(t2.trackName);
                break;
        }

        session.currentView.sort(comparator);

        // 3. Resetar para a primeira página após mudar o filtro/sort
        session.page = 0;
    }

    private ItemStack createTrackItem(TrackMenuInfo info, String langCode) {
        Material mat;
        try {
            // Removido o cast (String) desnecessário
            mat = Material.valueOf(info.iconName.toUpperCase());
        } catch (Exception e) {
            mat = Material.PAPER;
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§f§l" + info.trackName);

            // CORREÇÃO: Usando List<String> em vez de ArrayList<Object>
            List<String> lore = new ArrayList<>();

            lore.add("§7Dono: §e" + info.trackData.getOwnerName());
            lore.add("");

            String pb = (info.playerBestTime == null)
                ? "§c---"
                : "§a" + this.formatTime(info.playerBestTime);
            lore.add("§fMeu Tempo: " + pb);

            String wr = (info.worldRecordTime == null)
                ? "§c---"
                : "§6" + this.formatTime(info.worldRecordTime);
            lore.add("§fRecorde Mundial: " + wr);

            lore.add("");
            lore.add("§eClique para correr!");

            // Agora o compilador aceita o lore corretamente
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
            this.sessions.remove(e.getPlayer().getUniqueId());
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
            (String) clicked.getItemMeta().getDisplayName()
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

        Location loc = this.mysql.getTrackSpawn(trackName);
        if (loc == null) {
            player.sendMessage("\u00a7cSpawn n\u00e3o encontrado.");
            return;
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

        if (!this.mysql.getTimeTrialEnabled(uuid)) {
            this.mysql.setTimeTrialEnabled(uuid, true);
            this.plugin.sendMessage(player, "tt_auto_enabled", new String[0]);
        }

        this.timerUtils.stopTimer(player);
        if (this.plugin.getTimeTrialController() != null) {
            this.plugin.getTimeTrialController().endSession(player);
        }

        player.teleport(loc);
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

        String ownerName = this.mysql.getTrackOwner(trackName);
        this.stt.setPlayerTrack(player, trackName, ownerName);

        this.api.spawnBoat(player, false, false, false);
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
        BEST_TIME("Melhor Tempo (PB)", Material.CLOCK),
        WORLD_RECORD("Recorde Mundial (WR)", Material.GOLDEN_APPLE);

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
        ALL("Todas", Material.COMPASS),
        COMPLETED("Com Tempo (PB)", Material.WRITTEN_BOOK),
        NOT_PLAYED("Sem Tempo", Material.MAP);

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
