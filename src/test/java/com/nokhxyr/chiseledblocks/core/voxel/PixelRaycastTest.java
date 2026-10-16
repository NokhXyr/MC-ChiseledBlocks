/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.core.voxel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PixelRaycastTest {

    @Test
    void hitsTopOfSlabFromAbove() {
        PixelGrid slab = new PixelGridBuilder().fill(0, 0, 0, 16, 8, 16, 1).build();
        PixelRaycast.Hit hit = PixelRaycast.cast(slab, 3.5, 30, 12.5, 0, -1, 0, 100, m -> true);
        assertNotNull(hit);
        assertEquals(3, hit.x());
        assertEquals(7, hit.y());
        assertEquals(12, hit.z());
        assertEquals(Face.UP, hit.face());
        assertEquals(22, hit.distance(), 1e-9);
    }

    @Test
    void entersThroughWestFaceWhenMovingEast() {
        PixelGrid grid = new PixelGridBuilder().set(9, 4, 4, 1).build();
        PixelRaycast.Hit hit = PixelRaycast.cast(grid, -5, 4.5, 4.5, 1, 0, 0, 100, m -> true);
        assertNotNull(hit);
        assertEquals(9, hit.x());
        assertEquals(Face.WEST, hit.face());
    }

    @Test
    void diagonalRayFindsTheSingleVoxel() {
        PixelGrid grid = new PixelGridBuilder().set(10, 10, 10, 1).build();
        double n = 1 / Math.sqrt(3);
        PixelRaycast.Hit hit = PixelRaycast.cast(grid, 0.5, 0.5, 0.5, n, n, n, 100, m -> true);
        assertNotNull(hit);
        assertEquals(10, hit.x());
        assertEquals(10, hit.y());
        assertEquals(10, hit.z());
    }

    @Test
    void missesWhenRayPassesBeside() {
        PixelGrid grid = new PixelGridBuilder().set(1, 1, 1, 1).build();
        assertNull(PixelRaycast.cast(grid, 8.5, 30, 8.5, 0, -1, 0, 100, m -> true));
        assertNull(PixelRaycast.cast(grid, 40, 40, 40, 1, 0, 0, 100, m -> true));
    }

    @Test
    void respectsMaxDistance() {
        PixelGrid slab = new PixelGridBuilder().fill(0, 0, 0, 16, 8, 16, 1).build();
        assertNull(PixelRaycast.cast(slab, 3.5, 30, 3.5, 0, -1, 0, 10, m -> true));
    }

    @Test
    void ignoresNonSolidMaterials() {
        PixelGrid grid = new PixelGridBuilder().fill(0, 0, 0, 16, 16, 8, 2).set(4, 4, 12, 1).build();
        PixelRaycast.Hit hit = PixelRaycast.cast(grid, 4.5, 4.5, -3, 0, 0, 1, 100, m -> m == 1);
        assertNotNull(hit);
        assertEquals(12, hit.z());
        assertEquals(Face.NORTH, hit.face());
    }
}
