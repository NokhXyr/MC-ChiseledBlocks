/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.core.voxel;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Covers the selected voxels of a grid with few axis aligned boxes (greedy: grow along X, then Z,
 * then Y). Used to build collision and outline shapes in a single linear pass, instead of
 * OR-ing thousands of one voxel shapes together.
 */
public final class BoxMerger {

    /** A box in voxel units, min inclusive, max exclusive. */
    public record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public int volume() {
            return (maxX - minX) * (maxY - minY) * (maxZ - minZ);
        }
    }

    private BoxMerger() {
    }

    public static List<Box> merge(PixelGrid grid, IntPredicate include) {
        List<Box> boxes = new ArrayList<>();
        if (grid.isEmpty()) {
            return boxes;
        }
        if (grid.paletteSize() == 1) {
            if (include.test(grid.paletteEntry(0))) {
                boxes.add(new Box(0, 0, 0, PixelGrid.SIZE, PixelGrid.SIZE, PixelGrid.SIZE));
            }
            return boxes;
        }

        int size = PixelGrid.SIZE;
        BitSet open = new BitSet(PixelGrid.VOLUME);
        for (int i = 0; i < PixelGrid.VOLUME; i++) {
            int material = grid.get(i);
            if (material != PixelGrid.AIR && include.test(material)) {
                open.set(i);
            }
        }

        for (int i = open.nextSetBit(0); i >= 0; i = open.nextSetBit(i + 1)) {
            int x0 = i & 15;
            int z0 = (i >> 4) & 15;
            int y0 = i >> 8;

            int x1 = x0 + 1;
            while (x1 < size && open.get(PixelGrid.index(x1, y0, z0))) {
                x1++;
            }

            int z1 = z0 + 1;
            while (z1 < size && rowOpen(open, x0, x1, y0, z1)) {
                z1++;
            }

            int y1 = y0 + 1;
            while (y1 < size && layerOpen(open, x0, x1, y1, z0, z1)) {
                y1++;
            }

            for (int y = y0; y < y1; y++) {
                for (int z = z0; z < z1; z++) {
                    open.clear(PixelGrid.index(x0, y, z), PixelGrid.index(x1 - 1, y, z) + 1);
                }
            }
            boxes.add(new Box(x0, y0, z0, x1, y1, z1));
        }
        return boxes;
    }

    private static boolean rowOpen(BitSet open, int x0, int x1, int y, int z) {
        int from = PixelGrid.index(x0, y, z);
        int to = PixelGrid.index(x1 - 1, y, z) + 1;
        return open.nextClearBit(from) >= to;
    }

    private static boolean layerOpen(BitSet open, int x0, int x1, int y, int z0, int z1) {
        for (int z = z0; z < z1; z++) {
            if (!rowOpen(open, x0, x1, y, z)) {
                return false;
            }
        }
        return true;
    }
}
