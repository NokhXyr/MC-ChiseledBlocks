/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.core.edit;

/** What a chisel stroke removes around the targeted bit. */
public enum ChiselMode {
    /** One bit, whatever the size. */
    PIXEL,
    /** A cube of the tool size, aligned on the size grid. */
    CUBE,
    /** A column through the whole block along the clicked face's normal. */
    LINE,
    /** A layer of the tool size thickness, parallel to the clicked face, across the whole block. */
    PLANE,
    /** A ball of radius equal to the tool size, centred on the targeted bit. */
    SPHERE;

    private static final ChiselMode[] VALUES = values();

    public ChiselMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public static ChiselMode byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : PIXEL;
    }
}
