/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.core.voxel;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class BoxMergerTest {

    @Test
    void fullBlockIsOneBox() {
        List<BoxMerger.Box> boxes = BoxMerger.merge(PixelGrid.filled(1), m -> true);
        assertEquals(List.of(new BoxMerger.Box(0, 0, 0, 16, 16, 16)), boxes);
    }

    @Test
    void stairShapeIsTwoBoxes() {
        PixelGrid grid = new PixelGridBuilder()
                .fill(0, 0, 0, 16, 8, 16, 1)
                .fill(0, 8, 8, 16, 16, 16, 1)
                .build();
        assertEquals(2, BoxMerger.merge(grid, m -> true).size());
    }

    @Test
    void boxesCoverExactlyTheIncludedVoxels() {
        Random random = new Random(7);
        int[] voxels = new int[PixelGrid.VOLUME];
        for (int i = 0; i < voxels.length; i++) {
            voxels[i] = random.nextInt(4);
        }
        PixelGrid grid = PixelGrid.of(voxels);
        List<BoxMerger.Box> boxes = BoxMerger.merge(grid, m -> m != 3);

        int[] cover = new int[PixelGrid.VOLUME];
        for (BoxMerger.Box b : boxes) {
            for (int y = b.minY(); y < b.maxY(); y++) {
                for (int z = b.minZ(); z < b.maxZ(); z++) {
                    for (int x = b.minX(); x < b.maxX(); x++) {
                        cover[PixelGrid.index(x, y, z)]++;
                    }
                }
            }
        }
        for (int i = 0; i < PixelGrid.VOLUME; i++) {
            int m = grid.get(i);
            int expected = m != PixelGrid.AIR && m != 3 ? 1 : 0;
            assertEquals(expected, cover[i], "voxel " + i);
        }
    }
}
