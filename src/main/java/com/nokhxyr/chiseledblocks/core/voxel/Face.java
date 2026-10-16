/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.core.voxel;

/**
 * The six faces of a voxel. Declared in the same order as Minecraft's {@code Direction}
 * so that {@link #ordinal()} maps one to one.
 */
public enum Face {
    DOWN(1, false),
    UP(1, true),
    NORTH(2, false),
    SOUTH(2, true),
    WEST(0, false),
    EAST(0, true);

    private static final Face[] BY_AXIS = {WEST, EAST, DOWN, UP, NORTH, SOUTH};

    /** 0 = X, 1 = Y, 2 = Z. */
    public final int axis;
    public final boolean positive;

    Face(int axis, boolean positive) {
        this.axis = axis;
        this.positive = positive;
    }

    public int step() {
        return positive ? 1 : -1;
    }

    public Face opposite() {
        return of(axis, !positive);
    }

    public static Face of(int axis, boolean positive) {
        return BY_AXIS[axis * 2 + (positive ? 1 : 0)];
    }
}
