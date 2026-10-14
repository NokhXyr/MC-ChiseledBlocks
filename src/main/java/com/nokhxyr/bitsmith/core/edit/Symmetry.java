/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.core.edit;

import com.nokhxyr.bitsmith.core.voxel.BitGrid;

import java.util.function.IntConsumer;

/**
 * Mirror planes through the centre of the block. An edit on one voxel is repeated on each of its
 * mirror images, so one side of a carving can be shaped and the other follows.
 */
public enum Symmetry {
    NONE(false, false, false),
    X(true, false, false),
    Y(false, true, false),
    Z(false, false, true),
    XZ(true, false, true);

    private static final Symmetry[] VALUES = values();

    private final boolean mirrorX;
    private final boolean mirrorY;
    private final boolean mirrorZ;

    Symmetry(boolean mirrorX, boolean mirrorY, boolean mirrorZ) {
        this.mirrorX = mirrorX;
        this.mirrorY = mirrorY;
        this.mirrorZ = mirrorZ;
    }

    public static Symmetry byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : NONE;
    }

    public Symmetry next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public boolean mirrorsX() {
        return mirrorX;
    }

    public boolean mirrorsY() {
        return mirrorY;
    }

    public boolean mirrorsZ() {
        return mirrorZ;
    }

    /**
     * Calls {@code out} with {@code index} and each of its distinct mirror images. The voxel
     * itself always comes first.
     */
    public void forEachImage(int index, IntConsumer out) {
        out.accept(index);
        if (this == NONE) {
            return;
        }
        int x = index & 15;
        int z = (index >> 4) & 15;
        int y = index >> 8;
        int[] xs = mirrorX ? new int[]{x, 15 - x} : new int[]{x};
        int[] ys = mirrorY ? new int[]{y, 15 - y} : new int[]{y};
        int[] zs = mirrorZ ? new int[]{z, 15 - z} : new int[]{z};
        for (int ix = 0; ix < xs.length; ix++) {
            for (int iy = 0; iy < ys.length; iy++) {
                for (int iz = 0; iz < zs.length; iz++) {
                    if (ix == 0 && iy == 0 && iz == 0) {
                        continue;
                    }
                    int image = BitGrid.index(xs[ix], ys[iy], zs[iz]);
                    if (image != index) {
                        out.accept(image);
                    }
                }
            }
        }
    }
}
