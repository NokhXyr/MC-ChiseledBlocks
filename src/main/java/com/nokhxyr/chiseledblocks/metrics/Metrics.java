/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.metrics;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Counters for the performance runs, off unless {@code -Dchiseledblocks.metrics=true}. When off, every
 * call site is a single static boolean test.
 */
public final class Metrics {
    public static final boolean ENABLED = Boolean.getBoolean("chiseledblocks.metrics");

    /** Server: time spent handling chisel strokes, and how many. */
    public static final AtomicLong CHISEL_NANOS = new AtomicLong();
    public static final AtomicLong CHISEL_STROKES = new AtomicLong();
    /** Server: time spent applying the batched edits at the end of ticks, and over how many ticks. */
    public static final AtomicLong FLUSH_NANOS = new AtomicLong();
    public static final AtomicLong FLUSH_TICKS = new AtomicLong();
    /** Client: meshes built (cache misses) and the time spent building them. */
    public static final AtomicLong MESH_BUILDS = new AtomicLong();
    public static final AtomicLong MESH_NANOS = new AtomicLong();
    /** Client: section rebuilds asked for after a grid change. */
    public static final AtomicLong SECTION_DIRTY_MARKS = new AtomicLong();
    /** Client: full grid requests after a desync. */
    public static final AtomicLong RESYNC_REQUESTS = new AtomicLong();

    private Metrics() {
    }

    public static void reset() {
        for (AtomicLong counter : new AtomicLong[]{CHISEL_NANOS, CHISEL_STROKES, FLUSH_NANOS, FLUSH_TICKS,
                MESH_BUILDS, MESH_NANOS, SECTION_DIRTY_MARKS, RESYNC_REQUESTS}) {
            counter.set(0);
        }
    }
}
