/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.core.voxel;

import com.nokhxyr.chiseledblocks.core.edit.Symmetry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.IntUnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

class GridTransformsTest {
    private static final IntUnaryOperator SAME = IntUnaryOperator.identity();

    private static PixelGrid randomGrid() {
        Random random = new Random(3);
        int[] voxels = new int[PixelGrid.VOLUME];
        for (int i = 0; i < voxels.length; i++) {
            voxels[i] = random.nextInt(4);
        }
        return PixelGrid.of(voxels);
    }

    @Test
    void fourQuarterTurnsGiveTheSameGrid() {
        PixelGrid grid = randomGrid();
        for (int axis = 0; axis < 3; axis++) {
            PixelGrid turned = grid;
            for (int i = 0; i < 4; i++) {
                turned = GridTransforms.rotate(turned, axis, SAME);
            }
            assertEquals(grid, turned, "axis " + axis);
        }
    }

    @Test
    void aQuarterTurnKeepsEveryBit() {
        PixelGrid grid = randomGrid();
        for (int axis = 0; axis < 3; axis++) {
            PixelGrid turned = GridTransforms.rotate(grid, axis, SAME);
            assertEquals(grid.filledCount(), turned.filledCount());
            assertNotEquals(grid, turned);
        }
    }

    @Test
    void turningAroundYMovesNorthToEast() {
        PixelGrid north = new PixelGridBuilder().set(8, 3, 0, 1).build();
        PixelGrid turned = GridTransforms.rotate(north, 1, SAME);
        assertEquals(1, turned.get(15, 3, 8), "a bit on the north edge ends on the east edge");
    }

    @Test
    void mirroringTwiceGivesTheSameGrid() {
        PixelGrid grid = randomGrid();
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(grid, GridTransforms.mirror(GridTransforms.mirror(grid, axis, SAME), axis, SAME));
        }
        PixelGrid one = new PixelGridBuilder().set(2, 5, 9, 7).build();
        assertEquals(7, GridTransforms.mirror(one, 0, SAME).get(13, 5, 9));
    }

    @Test
    void materialsAreRemapped() {
        PixelGrid grid = new PixelGridBuilder().set(1, 1, 1, 4).build();
        PixelGrid turned = GridTransforms.rotate(grid, 1, m -> m + 10);
        assertTrue(turned.contains(14));
        assertFalse(turned.contains(4));
    }

    @Test
    void symmetryListsDistinctImagesWithTheVoxelFirst() {
        List<Integer> images = new ArrayList<>();
        Symmetry.XZ.forEachImage(PixelGrid.index(2, 4, 3), images::add);
        assertEquals(List.of(PixelGrid.index(2, 4, 3), PixelGrid.index(2, 4, 12), PixelGrid.index(13, 4, 3), PixelGrid.index(13, 4, 12)), images);

        images.clear();
        Symmetry.NONE.forEachImage(7, images::add);
        assertEquals(List.of(7), images);
    }
}
