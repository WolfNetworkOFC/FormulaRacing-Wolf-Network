package dev.EfraGroup.formulaRacing.TVCamera;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.*;

public class TVCamPlayer {

    private final UUID uuid;
    private String editingTrack;
    private Player following;
    private final Set<Player> followers = new HashSet<>();
    private Location selection1;
    private Location selection2;
    private TVCamera currentCamera;
    private final List<Integer> disabledCameras = new ArrayList<>();
    private boolean inventoryOpen = false;
    private final Map<Integer, TVCamera> cameraItems = new HashMap<>();

    public TVCamPlayer(UUID uuid) {
        this.uuid = uuid;
    }

    public TVCamPlayer(UUID uuid, List<Integer> disabledCameras) {
        this.uuid = uuid;
        this.disabledCameras.addAll(disabledCameras);
    }

    public UUID getUniqueId() { return uuid; }

    public boolean isEditing() { return editingTrack != null; }
    public void startEditing(String trackNameWS) { this.editingTrack = trackNameWS; }
    public void stopEditing() { this.editingTrack = null; }
    public String getEditingTrack() { return editingTrack; }

    public void setSelection1(Location loc) { this.selection1 = loc; }
    public void setSelection2(Location loc) { this.selection2 = loc; }
    public Location getSelection1() { return selection1; }
    public Location getSelection2() { return selection2; }

    public void startFollowing(Player target) {
        stopFollowing();
        this.following = target;
    }

    public void stopFollowing() {
        if (following != null) {
            TVCamPlayer tp = TVCameraController.getPlayer(following.getUniqueId());
            if (tp != null) tp.followers.remove(this.uuid);
            following = null;
        }
        currentCamera = null;
    }

    public boolean isFollowing() { return following != null; }
    public Player getFollowing() { return following; }

    public void addFollower(Player follower) {
        followers.add(follower);
    }

    public void removeFollower(UUID uuid) {
        followers.removeIf(p -> p.getUniqueId().equals(uuid));
    }

    public Set<Player> getFollowers() { return followers; }

    public void setCurrentCamera(TVCamera camera) { this.currentCamera = camera; }
    public TVCamera getCurrentCamera() { return currentCamera; }

    public boolean isCameraDisabled(int id) { return disabledCameras.contains(id); }
    public void disableCamera(int id) { if (!disabledCameras.contains(id)) disabledCameras.add(id); }
    public void enableCamera(int id) { disabledCameras.remove((Integer) id); }
    public List<Integer> getDisabledCameras() { return disabledCameras; }

    public boolean isInventoryOpen() { return inventoryOpen; }
    public void setInventoryOpen(boolean open) {
        this.inventoryOpen = open;
        if (!open) cameraItems.clear();
    }

    public Map<Integer, TVCamera> getCameraItems() { return cameraItems; }
    public void setCameraItems(Map<Integer, TVCamera> items) {
        cameraItems.clear();
        cameraItems.putAll(items);
    }
}
