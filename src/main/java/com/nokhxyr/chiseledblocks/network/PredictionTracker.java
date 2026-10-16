/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.network;

import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;

/**
 * Client side memory of where the player predicted a stroke recently, so that the expected
 * mismatch between a prediction and an older server update is not taken for a desync.
 */
public final class PredictionTracker {
    /** Ticks during which a block counts as recently predicted. */
    private static final long WINDOW = 10;
    private static final Long2LongOpenHashMap LAST = new Long2LongOpenHashMap();

    private PredictionTracker() {
    }

    public static void record(BlockPos pos, long gameTime) {
        if (LAST.size() > 256) {
            LAST.long2LongEntrySet().removeIf(e -> gameTime - e.getLongValue() > WINDOW);
        }
        LAST.put(pos.asLong(), gameTime);
    }

    public static boolean isRecent(BlockPos pos, long gameTime) {
        long last = LAST.getOrDefault(pos.asLong(), Long.MIN_VALUE);
        return last != Long.MIN_VALUE && gameTime - last <= WINDOW;
    }
}
