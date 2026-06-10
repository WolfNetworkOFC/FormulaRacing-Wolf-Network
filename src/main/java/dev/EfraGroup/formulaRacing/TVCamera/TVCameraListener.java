package dev.EfraGroup.formulaRacing.TVCamera;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;

import java.util.Map;

public class TVCameraListener implements Listener {

    private final FormulaRacing plugin;
    private final TVCameraController controller;

    public TVCameraListener(FormulaRacing plugin, TVCameraController controller) {
        this.plugin = plugin;
        this.controller = controller;
        SchedulerHelper.runTaskTimer(plugin, () -> controller.updateFollowers(), 0L, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        TVCamPlayer tvp = TVCameraController.getPlayer(player.getUniqueId());

        if (!tvp.getFollowers().isEmpty() && player.isInsideVehicle() && player.getVehicle() instanceof Boat) {
            TVCamera best = controller.findBestCamera(player);
            if (best != null) {
                for (Player follower : tvp.getFollowers()) {
                    TVCamPlayer fp = TVCameraController.getPlayer(follower.getUniqueId());
                    if (!best.equals(fp.getCurrentCamera()) && !fp.isCameraDisabled(best.getId())) {
                        best.tpPlayer(follower);
                        fp.setCurrentCamera(best);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        TVCamPlayer tvp = TVCameraController.getPlayer(player.getUniqueId());

        tvp.stopFollowing();
        for (Player follower : tvp.getFollowers()) {
            TVCameraController.getPlayer(follower.getUniqueId()).stopFollowing();
        }
        TVCameraController.removePlayer(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        TVCameraController.getPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRightClickBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getItem() == null || event.getItem().getType() != Material.STICK) return;

        Player player = event.getPlayer();
        TVCamPlayer tvp = TVCameraController.getPlayer(player.getUniqueId());
        if (!tvp.isEditing()) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        tvp.setSelection2(block.getLocation().add(0.5, 1, 0.5));
        player.sendMessage("§aSet position 2");
        event.setCancelled(true);
    }

    @EventHandler
    public void onLeftClickBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getItem() == null || event.getItem().getType() != Material.STICK) return;

        Player player = event.getPlayer();
        TVCamPlayer tvp = TVCameraController.getPlayer(player.getUniqueId());
        if (!tvp.isEditing()) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        tvp.setSelection1(block.getLocation().add(0.5, -1, 0.5));
        player.sendMessage("§aSet position 1");
        event.setCancelled(true);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        TVCamPlayer tvp = TVCameraController.getPlayer(player.getUniqueId());
        if (tvp.isEditing() && player.getInventory().getItemInMainHand().getType() == Material.STICK) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        TVCamPlayer tvp = TVCameraController.getPlayer(player.getUniqueId());
        if (!tvp.isInventoryOpen()) return;

        event.setCancelled(true);
        Map<Integer, TVCamera> items = tvp.getCameraItems();

        if (event.getClick() == ClickType.LEFT && items.containsKey(event.getSlot())) {
            TVCamera cam = items.get(event.getSlot());
            if (!tvp.isCameraDisabled(cam.getId())) {
                cam.tpPlayer(player);
                player.closeInventory();
            }
        }

        if (event.getClick() == ClickType.RIGHT && items.containsKey(event.getSlot())) {
            TVCamera cam = items.get(event.getSlot());
            if (tvp.isCameraDisabled(cam.getId())) {
                tvp.enableCamera(cam.getId());
            } else {
                tvp.disableCamera(cam.getId());
            }
            player.closeInventory();
            openCameraMenu(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Player player = (Player) event.getWhoClicked();
        TVCamPlayer tvp = TVCameraController.getPlayer(player.getUniqueId());
        if (tvp.isInventoryOpen()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        TVCamPlayer tvp = TVCameraController.getPlayer(player.getUniqueId());
        if (event.getInventory().getType() == InventoryType.CHEST && tvp.isInventoryOpen()) {
            tvp.setInventoryOpen(false);
        }
    }

    public void openCameraMenu(Player player) {
        TVCamPlayer tvp = TVCameraController.getPlayer(player.getUniqueId());
        String trackName = controller.getNearestTrackName(player);
        if (trackName == null) {
            player.sendMessage("§cNo track found nearby.");
            return;
        }

        java.util.List<TVCamera> trackCameras = controller.getCamerasForTrack(trackName.replaceAll("\\s+", ""));
        Inventory inv = org.bukkit.Bukkit.createInventory(player, 54, "§b§lCamera Menu");
        Map<Integer, TVCamera> items = new java.util.HashMap<>();

        int slot = 10;
        for (TVCamera cam : trackCameras) {
            if (slot % 9 == 0) slot += 2;
            if (slot > 43) break;

            String displayName = cam.getLabel() != null ? cam.getLabel() : "§bCamera " + cam.getCamIndex();
            java.util.List<String> lore = new java.util.ArrayList<>();
            if (tvp.isCameraDisabled(cam.getId())) {
                lore.add("§cThis camera is disabled!");
                lore.add("§7Right-click to enable");
            } else {
                lore.add("§7Click to teleport!");
                lore.add("§7Right-click to disable");
            }

            org.bukkit.inventory.ItemStack item = new org.bukkit.inventory.ItemStack(Material.PLAYER_HEAD, Math.max(1, Math.min(cam.getCamIndex(), 64)));
            org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(displayName);
                meta.setLore(lore);
                item.setItemMeta(meta);
            }

            inv.setItem(slot, item);
            items.put(slot, cam);
            slot++;
        }

        tvp.setCameraItems(items);
        tvp.setInventoryOpen(true);
        player.openInventory(inv);
    }
}
