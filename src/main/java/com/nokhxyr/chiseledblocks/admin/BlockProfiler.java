/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.admin;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Server time spent by Chiseled Blocks, per carved block, over a sampling window started by
 * {@code /chiseledblocks perf}. When no window is running, every call site is a single test of a
 * static boolean and nothing is measured.
 */
public final class BlockProfiler {
    /** What the time was spent on. */
    public enum Kind {
        /** Handling chisel strokes aimed at the block. */
        CHISEL,
        /** Applying the tick's batched edits and sending them to players. */
        APPLY,
        /** Shape queries (collisions, raytraces) on the server. */
        SHAPE,
        /** Writing the block for saving or for a player that starts tracking it. */
        SAVE,
        /** Reading the block when its chunk loads. */
        LOAD
    }

    private static final int KINDS = Kind.values().length;

    public static volatile boolean active;

    private static final Map<Key, long[]> COSTS = new ConcurrentHashMap<>();
    private static int ticks;
    private static int durationTicks;
    private static @Nullable Consumer<Report> onFinish;

    private BlockProfiler() {
    }

    private record Key(ResourceKey<Level> dimension, long pos) {
    }

    /** Time and number of calls per kind for one block. */
    public record BlockCost(ResourceKey<Level> dimension, BlockPos pos, long[] nanos, long[] calls) {
        public long totalNanos() {
            long total = 0;
            for (long value : nanos) {
                total += value;
            }
            return total;
        }
    }

    public record Report(int ticks, List<BlockCost> blocks) {
        public long totalNanos() {
            long total = 0;
            for (BlockCost block : blocks) {
                total += block.totalNanos();
            }
            return total;
        }

        /** Microseconds per tick, over the whole window. */
        public double perTick(long nanos) {
            return ticks == 0 ? 0 : nanos / 1000.0 / ticks;
        }
    }

    public static void record(Level level, BlockPos pos, Kind kind, long nanos) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        long[] cost = COSTS.computeIfAbsent(new Key(level.dimension(), pos.asLong()), key -> new long[KINDS * 2]);
        synchronized (cost) {
            cost[kind.ordinal()] += nanos;
            cost[KINDS + kind.ordinal()]++;
        }
    }

    /** Starts a window of the given length; the report goes to the callback when it ends. */
    public static void start(int lengthTicks, Consumer<Report> callback) {
        COSTS.clear();
        ticks = 0;
        durationTicks = lengthTicks;
        onFinish = callback;
        active = true;
    }

    /** Ends the window now and returns its report, or null when none was running. */
    public static @Nullable Report stop() {
        if (!active) {
            return null;
        }
        active = false;
        onFinish = null;
        List<BlockCost> blocks = new ArrayList<>(COSTS.size());
        for (Map.Entry<Key, long[]> entry : COSTS.entrySet()) {
            long[] cost = entry.getValue();
            long[] nanos = new long[KINDS];
            long[] calls = new long[KINDS];
            synchronized (cost) {
                System.arraycopy(cost, 0, nanos, 0, KINDS);
                System.arraycopy(cost, KINDS, calls, 0, KINDS);
            }
            blocks.add(new BlockCost(entry.getKey().dimension(), BlockPos.of(entry.getKey().pos()), nanos, calls));
        }
        COSTS.clear();
        blocks.sort(Comparator.comparingLong(BlockCost::totalNanos).reversed());
        return new Report(ticks, blocks);
    }

    public static int ticksLeft() {
        return Math.max(0, durationTicks - ticks);
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (!active) {
            return;
        }
        if (++ticks >= durationTicks) {
            Consumer<Report> callback = onFinish;
            Report report = stop();
            if (callback != null && report != null) {
                callback.accept(report);
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        active = false;
        onFinish = null;
        COSTS.clear();
    }
}
