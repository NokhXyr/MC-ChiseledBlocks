/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.core.voxel;

import java.util.function.IntUnaryOperator;

/**
 * Quarter turns and mirrors of a whole grid. Each one is a pure permutation of the voxels, so the
 * number of bits never changes; materials can be remapped at the same time (a log turned on its
 * side changes its axis).
 */
public final class GridTransforms {
    private static final int MAX = BitGrid.SIZE - 1;

    private GridTransforms() {
    }

    /**
     * Turns the grid a quarter turn around {@code axis} (0 = X, 1 = Y, 2 = Z). Around Y the turn is
     * clockwise seen from above (north goes east), like vanilla {@code Rotation.CLOCKWISE_90}.
     */
    public static BitGrid rotate(BitGrid grid, int axis, IntUnaryOperator material) {
        int[] source = grid.toArray(new int[BitGrid.VOLUME]);
        int[] result = new int[BitGrid.VOLUME];
        int[] p = new int[3];
        // In-plane axes, ordered so that around Y the turn is clockwise seen from above.
        int u = axis == 1 ? 0 : (axis + 1) % 3;
        int v = axis == 1 ? 2 : (axis + 2) % 3;
        for (int i = 0; i < BitGrid.VOLUME; i++) {
            p[0] = i & 15;
            p[2] = (i >> 4) & 15;
            p[1] = i >> 8;
            int a = p[u];
            int b = p[v];
            p[u] = MAX - b;
            p[v] = a;
            result[BitGrid.index(p[0], p[1], p[2])] = remap(source[i], material);
        }
        return BitGrid.of(result);
    }

    /** Mirrors the grid across the plane through the centre perpendicular to {@code axis}. */
    public static BitGrid mirror(BitGrid grid, int axis, IntUnaryOperator material) {
        int[] source = grid.toArray(new int[BitGrid.VOLUME]);
        int[] result = new int[BitGrid.VOLUME];
        int[] p = new int[3];
        for (int i = 0; i < BitGrid.VOLUME; i++) {
            p[0] = i & 15;
            p[2] = (i >> 4) & 15;
            p[1] = i >> 8;
            p[axis] = MAX - p[axis];
            result[BitGrid.index(p[0], p[1], p[2])] = remap(source[i], material);
        }
        return BitGrid.of(result);
    }

    private static int remap(int value, IntUnaryOperator material) {
        return value == BitGrid.AIR ? BitGrid.AIR : material.applyAsInt(value);
    }
}
