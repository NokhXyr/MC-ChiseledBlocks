/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.core.edit;

import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.Face;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChiselShapeTest {

    private static List<Integer> voxels(ChiselMode mode, int size, int x, int y, int z, Face face) {
        List<Integer> out = new ArrayList<>();
        ChiselShape.forEach(mode, size, x, y, z, face, out::add);
        return out;
    }

    @Test
    void bitIgnoresSize() {
        assertEquals(List.of(BitGrid.index(3, 4, 5)), voxels(ChiselMode.BIT, 8, 3, 4, 5, Face.UP));
    }

    @Test
    void cubeIsAlignedOnTheSizeGrid() {
        List<Integer> cube = voxels(ChiselMode.CUBE, 4, 5, 6, 7, Face.UP);
        assertEquals(64, cube.size());
        assertTrue(cube.contains(BitGrid.index(4, 4, 4)));
        assertTrue(cube.contains(BitGrid.index(7, 7, 7)));
        assertFalse(cube.contains(BitGrid.index(8, 7, 7)));
    }

    @Test
    void lineGoesThroughTheBlockAlongTheFaceNormal() {
        List<Integer> line = voxels(ChiselMode.LINE, 1, 3, 15, 9, Face.UP);
        assertEquals(16, line.size());
        for (int y = 0; y < 16; y++) {
            assertTrue(line.contains(BitGrid.index(3, y, 9)));
        }
    }

    @Test
    void planeCoversTheWholeFaceWithSizeThickness() {
        List<Integer> plane = voxels(ChiselMode.PLANE, 2, 3, 9, 9, Face.NORTH);
        assertEquals(16 * 16 * 2, plane.size());
        assertTrue(plane.contains(BitGrid.index(0, 0, 8)));
        assertTrue(plane.contains(BitGrid.index(15, 15, 9)));
    }

    @Test
    void sphereIsClippedToTheBlock() {
        assertEquals(7, voxels(ChiselMode.SPHERE, 1, 8, 8, 8, Face.UP).size());
        List<Integer> corner = voxels(ChiselMode.SPHERE, 2, 0, 0, 0, Face.UP);
        assertTrue(corner.stream().allMatch(i -> i >= 0 && i < BitGrid.VOLUME));
        assertEquals(11, corner.size());
    }

    @Test
    void sizesCycleAndClamp() {
        assertEquals(2, ChiselShape.nextSize(1));
        assertEquals(1, ChiselShape.nextSize(8));
        assertEquals(4, ChiselShape.validSize(5));
        assertEquals(1, ChiselShape.validSize(0));
    }
}
