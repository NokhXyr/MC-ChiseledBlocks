/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.core.voxel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BoxMergerTest {

    @Test
    void fullBlockIsOneBox() {
        List<BoxMerger.Box> boxes = BoxMerger.merge(BitGrid.filled(1), m -> true);
        assertEquals(List.of(new BoxMerger.Box(0, 0, 0, 16, 16, 16)), boxes);
    }

    @Test
    void stairShapeIsTwoBoxes() {
        BitGrid grid = new BitGridBuilder()
                .fill(0, 0, 0, 16, 8, 16, 1)
                .fill(0, 8, 8, 16, 16, 16, 1)
                .build();
        assertEquals(2, BoxMerger.merge(grid, m -> true).size());
    }

    @Test
    void boxesCoverExactlyTheIncludedVoxels() {
        Random random = new Random(7);
        int[] voxels = new int[BitGrid.VOLUME];
        for (int i = 0; i < voxels.length; i++) {
            voxels[i] = random.nextInt(4);
        }
        BitGrid grid = BitGrid.of(voxels);
        List<BoxMerger.Box> boxes = BoxMerger.merge(grid, m -> m != 3);

        int[] cover = new int[BitGrid.VOLUME];
        for (BoxMerger.Box b : boxes) {
            for (int y = b.minY(); y < b.maxY(); y++) {
                for (int z = b.minZ(); z < b.maxZ(); z++) {
                    for (int x = b.minX(); x < b.maxX(); x++) {
                        cover[BitGrid.index(x, y, z)]++;
                    }
                }
            }
        }
        for (int i = 0; i < BitGrid.VOLUME; i++) {
            int m = grid.get(i);
            int expected = m != BitGrid.AIR && m != 3 ? 1 : 0;
            assertEquals(expected, cover[i], "voxel " + i);
        }
    }
}
