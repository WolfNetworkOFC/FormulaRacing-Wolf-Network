package dev.EfraGroup.formulaRacing.Heat;

import dev.EfraGroup.formulaRacing.FormulaRacing;
import dev.EfraGroup.formulaRacing.Utils.SchedulerHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

/**
 * The block side of the gimmick system, written directly against the Bukkit API
 * so it does not need WorldEdit at runtime.
 *
 * <p>Parsing the saved build happens off the main thread; the world is only
 * touched on the region that owns the gimmick, in chunks of
 * {@link #BLOCKS_PER_TICK} blocks per tick, so a big gimmick becomes a small
 * spread of work instead of one freeze.</p>
 */
public final class GimmickBlocks {

    /** How many blocks one tick is allowed to touch. 4000 blocks costs roughly 1-2 ms. */
    public static final int BLOCKS_PER_TICK = 4000;

    private GimmickBlocks() {
    }

    /** A block that is going to be written, already parsed and in world coordinates. */
    public record Placement(int x, int y, int z, BlockData data) {
    }

    /**
     * Turns the stored entries (relative to the anchor) into world placements.
     * Runs off the main thread: it only parses block data, never touches a world.
     *
     * @param ignoreAir when true the air of the saved build is dropped, leaving the
     *                  blocks that were already there untouched
     */
    public static List<Placement> toPlacements(List<GimmickFile.Entry> entries, Location anchor, boolean ignoreAir) {
        List<Placement> placements = new ArrayList<>(entries.size());
        int baseX = (int) Math.floor(anchor.getX());
        int baseY = (int) Math.floor(anchor.getY());
        int baseZ = (int) Math.floor(anchor.getZ());

        for (GimmickFile.Entry entry : entries) {
            BlockData data;
            try {
                data = Bukkit.createBlockData(entry.data());
            } catch (IllegalArgumentException e) {
                continue; // block from a newer version / unknown id: skip it
            }
            if (ignoreAir && data.getMaterial().isAir()) continue;
            placements.add(
                new Placement(baseX + entry.x(), baseY + entry.y(), baseZ + entry.z(), data)
            );
        }
        return placements;
    }

    /** Same as {@link #toPlacements} but for a backup file, whose coordinates are already absolute. */
    public static List<Placement> backupToPlacements(List<GimmickFile.Entry> entries) {
        List<Placement> placements = new ArrayList<>(entries.size());
        for (GimmickFile.Entry entry : entries) {
            try {
                placements.add(
                    new Placement(entry.x(), entry.y(), entry.z(), Bukkit.createBlockData(entry.data()))
                );
            } catch (IllegalArgumentException ignored) {
                // unknown block: nothing to put back
            }
        }
        return placements;
    }

    /**
     * Reads what is currently in the world where {@code placements} will be written.
     * Replaces the previous implementation's WorldEdit clipboard snapshot, and is
     * what makes the undo survive a server crash (the caller writes it to disk).
     *
     * @param onDone on the region thread, with the previous state of every block that
     *               the paste will actually change
     */
    public static void capture(
        FormulaRacing plugin,
        Location anchor,
        List<Placement> placements,
        Consumer<List<GimmickFile.Entry>> onDone
    ) {
        World world = anchor.getWorld();
        if (world == null) return;

        List<GimmickFile.Entry> previous = new ArrayList<>();

        new BatchedJob(plugin, anchor, placements.size(), index -> {
            Placement placement = placements.get(index);
            String current = world.getBlockAt(placement.x(), placement.y(), placement.z()).getBlockData().getAsString();
            // Blocks that will not change are not worth storing.
            if (current.equals(placement.data().getAsString())) return;
            previous.add(
                new GimmickFile.Entry(placement.x(), placement.y(), placement.z(), current)
            );
        }, () -> onDone.accept(previous)).start();
    }

    /**
     * Writes the placements into the world, {@link #BLOCKS_PER_TICK} blocks per tick.
     *
     * @param onDone on the region thread, once every block was written
     */
    public static void apply(
        FormulaRacing plugin,
        Location anchor,
        List<Placement> placements,
        Runnable onDone
    ) {
        World world = anchor.getWorld();
        if (world == null) {
            onDone.run();
            return;
        }

        final int[] failed = {0};
        new BatchedJob(plugin, anchor, placements.size(), index -> {
            Placement placement = placements.get(index);
            try {
                world.getBlockAt(placement.x(), placement.y(), placement.z()).setBlockData(placement.data(), false);
            } catch (Exception e) {
                failed[0]++;
            }
        }, () -> {
            if (failed[0] > 0) {
                plugin.getLogger().warning(
                    "[Gimmick] " + failed[0] + " bloco(s) não puderam ser colocados."
                );
            }
            onDone.run();
        }).start();
    }

    /**
     * Runs {@code step} for every index, spreading the work over ticks. Each chunk is
     * scheduled at the gimmick location, so on Folia it always runs on the region that
     * owns those blocks.
     */
    private static final class BatchedJob implements Runnable {

        private final FormulaRacing plugin;
        private final Location anchor;
        private final int total;
        private final IntConsumer step;
        private final Runnable onDone;
        private int index = 0;
        private boolean started = false;

        BatchedJob(FormulaRacing plugin, Location anchor, int total, IntConsumer step, Runnable onDone) {
            this.plugin = plugin;
            this.anchor = anchor;
            this.total = total;
            this.step = step;
            this.onDone = onDone;
        }

        void start() {
            if (started) return;
            started = true;
            // Scheduled at the gimmick location instead of running right here: the caller
            // may be an async thread (loading a build or restoring a backup), and on Folia
            // the calling thread does not own the region of those blocks.
            SchedulerHelper.runTaskAtLocation(plugin, anchor, this);
        }

        @Override
        public void run() {
            int end = Math.min(index + BLOCKS_PER_TICK, total);
            for (; index < end; index++) {
                try {
                    step.accept(index);
                } catch (Exception e) {
                    plugin.getLogger().warning("[Gimmick] Erro ao processar bloco: " + e.getMessage());
                }
            }

            if (index >= total) {
                onDone.run();
                return;
            }

            SchedulerHelper.runDelayedTaskAtLocation(plugin, anchor, this, 1L);
        }
    }
}
