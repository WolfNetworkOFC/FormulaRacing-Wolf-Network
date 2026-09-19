package dev.EfraGroup.formulaRacing.Heat;

import com.sk89q.worldedit.EmptyClipboardException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.session.SessionOwner;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

/**
 * The only place where the gimmick system still talks to WorldEdit: reading the
 * clipboard the admin filled with {@code //copy} when running
 * {@code /gimmick save}. Everything else (loading, pasting, backing up) is done by
 * {@link GimmickBlocks} straight from the Bukkit API.
 *
 * <p>This is also where the old implementation broke: it drove WordEdit through
 * reflection with calls that do not exist in the API, so no gimmick was ever
 * pasted.</p>
 */
public final class GimmickSchematics {

    private GimmickSchematics() {
    }

    /**
     * Reads the player's WorldEdit clipboard as entries relative to the clipboard
     * origin, which is the exact spot {@code //paste} anchors the build on. The
     * entries therefore paste back in place when the anchor is the saved position.
     */
    public static List<GimmickFile.Entry> readClipboard(Player player) {
        ClipboardHolder holder = holder(player);
        Clipboard clipboard = holder.getClipboard();
        BlockVector3 origin = clipboard.getOrigin();
        Region region = clipboard.getRegion();

        List<GimmickFile.Entry> entries = new ArrayList<>();
        for (BlockVector3 position : region) {
            BlockData data;
            try {
                data = BukkitAdapter.adapt(clipboard.getBlock(position));
            } catch (Exception e) {
                continue; // block this server version cannot represent
            }
            entries.add(
                new GimmickFile.Entry(
                    position.x() - origin.x(),
                    position.y() - origin.y(),
                    position.z() - origin.z(),
                    data.getAsString()
                )
            );
        }
        return entries;
    }

    /**
     * True when the clipboard has a rotation/flip applied after //copy. The saved
     * build only keeps the blocks, so the admin is warned instead of silently
     * getting an unrotated gimmick.
     */
    public static boolean hasPendingTransform(Player player) {
        try {
            ClipboardHolder holder = holder(player);
            return holder.getTransform() != null && !holder.getTransform().isIdentity();
        } catch (GimmickException e) {
            return false;
        }
    }

    private static ClipboardHolder holder(Player player) {
        try {
            LocalSession session = WorldEdit.getInstance()
                .getSessionManager()
                .get((SessionOwner) BukkitAdapter.adapt(player));
            return session.getClipboard();
        } catch (EmptyClipboardException e) {
            throw new GimmickException("Você não tem nada copiado! Use //copy na área primeiro.");
        } catch (Exception e) {
            throw new GimmickException("Não foi possível ler o clipboard do WorldEdit: " + e.getMessage(), e);
        }
    }
}
