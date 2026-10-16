/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.core.edit;

/** How bits are placed with bits or a bit bag in hand. */
public enum PlacementMode {
    /** One bit per click. */
    SINGLE,
    /** Hold the use key and paint on the layer of the first bit, like a brush. */
    DRAW,
    /** First click sets the start, second click fills the straight line to the end. */
    LINE,
    /** First click sets a corner, second click fills the rectangle on the layer of the first. */
    PLANE,
    /** First click sets a corner, second click fills the whole box. */
    BOX;

    private static final PlacementMode[] VALUES = values();

    public static PlacementMode byOrdinal(int ordinal) {
        return VALUES[Math.floorMod(ordinal, VALUES.length)];
    }

    /** Whether the mode takes a start and an end. */
    public boolean twoPoints() {
        return this == LINE || this == PLANE || this == BOX;
    }
}
