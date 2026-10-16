/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.core.edit;

/**
 * The bits a placement covers, in world bit coordinates (block coordinate times 16 plus the bit
 * inside the block), between a start and an end.
 */
public final class PlacementShape {
    /** Most bits one placement may cover: four full blocks. */
    public static final int MAX_PIXELS = 4 * 4096;
    /** Longest side of a shape, in bits: eight blocks. */
    public static final int MAX_SPAN = 8 * 16;

    /** Receives one bit of the shape. */
    @FunctionalInterface
    public interface PixelConsumer {
        void accept(int x, int y, int z);
    }

    private PlacementShape() {
    }

    /** Whether the shape between {@code a} and {@code b} is small enough to place. */
    public static boolean fits(PlacementMode mode, int[] a, int[] b, int axis) {
        long count = 1;
        for (int i = 0; i < 3; i++) {
            int span = Math.abs(b[i] - a[i]) + 1;
            if (span > MAX_SPAN) {
                return false;
            }
            if (mode == PlacementMode.BOX || (mode == PlacementMode.PLANE && i != axis)) {
                count *= span;
            }
        }
        if (mode == PlacementMode.LINE) {
            count = Math.max(Math.abs(b[0] - a[0]), Math.max(Math.abs(b[1] - a[1]), Math.abs(b[2] - a[2]))) + 1L;
        }
        return count <= MAX_PIXELS;
    }

    /**
     * Calls {@code consumer} for every bit of the shape. Single and draw cover {@code a} only; a
     * plane keeps the start's coordinate on {@code axis} (the axis of the clicked face).
     */
    public static void forEach(PlacementMode mode, int[] a, int[] b, int axis, PixelConsumer consumer) {
        switch (mode) {
            case SINGLE, DRAW -> consumer.accept(a[0], a[1], a[2]);
            case LINE -> line(a, b, consumer);
            case PLANE -> {
                int[] end = b.clone();
                end[axis] = a[axis];
                box(a, end, consumer);
            }
            case BOX -> box(a, b, consumer);
        }
    }

    private static void line(int[] a, int[] b, PixelConsumer consumer) {
        int dx = b[0] - a[0];
        int dy = b[1] - a[1];
        int dz = b[2] - a[2];
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        if (steps == 0) {
            consumer.accept(a[0], a[1], a[2]);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            consumer.accept(a[0] + Math.round((float) dx * i / steps),
                    a[1] + Math.round((float) dy * i / steps),
                    a[2] + Math.round((float) dz * i / steps));
        }
    }

    private static void box(int[] a, int[] b, PixelConsumer consumer) {
        int x0 = Math.min(a[0], b[0]);
        int y0 = Math.min(a[1], b[1]);
        int z0 = Math.min(a[2], b[2]);
        int x1 = Math.max(a[0], b[0]);
        int y1 = Math.max(a[1], b[1]);
        int z1 = Math.max(a[2], b[2]);
        for (int y = y0; y <= y1; y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    consumer.accept(x, y, z);
                }
            }
        }
    }
}
