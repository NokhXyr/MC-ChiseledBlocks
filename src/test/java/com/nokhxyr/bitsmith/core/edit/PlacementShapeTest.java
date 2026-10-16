/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.core.edit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PlacementShapeTest {

    private static List<List<Integer>> bits(PlacementMode mode, int[] a, int[] b, int axis) {
        List<List<Integer>> out = new ArrayList<>();
        PlacementShape.forEach(mode, a, b, axis, (x, y, z) -> out.add(List.of(x, y, z)));
        return out;
    }

    @Test
    void singleAndDrawCoverTheStartOnly() {
        assertEquals(List.of(List.of(5, 6, 7)), bits(PlacementMode.SINGLE, new int[]{5, 6, 7}, new int[]{40, 6, 7}, 1));
        assertEquals(List.of(List.of(5, 6, 7)), bits(PlacementMode.DRAW, new int[]{5, 6, 7}, new int[]{40, 6, 7}, 1));
    }

    @Test
    void lineIsContinuousAndEndsOnBothPoints() {
        List<List<Integer>> line = bits(PlacementMode.LINE, new int[]{0, 0, 0}, new int[]{10, 4, -3}, 1);
        assertEquals(11, line.size(), "one bit per step of the longest axis");
        assertEquals(List.of(0, 0, 0), line.get(0));
        assertEquals(List.of(10, 4, -3), line.get(line.size() - 1));
        for (int i = 1; i < line.size(); i++) {
            for (int c = 0; c < 3; c++) {
                assertTrue(Math.abs(line.get(i).get(c) - line.get(i - 1).get(c)) <= 1, "no gap in the line");
            }
        }
    }

    @Test
    void planeStaysOnTheStartLayer() {
        List<List<Integer>> plane = bits(PlacementMode.PLANE, new int[]{0, 5, 0}, new int[]{3, 9, 2}, 1);
        assertEquals(4 * 3, plane.size());
        assertTrue(plane.stream().allMatch(bit -> bit.get(1) == 5), "flattened on the y layer of the start");
    }

    @Test
    void boxFillsEveryBitOnce() {
        List<List<Integer>> box = bits(PlacementMode.BOX, new int[]{2, 2, 2}, new int[]{-1, 4, 3}, 0);
        assertEquals(4 * 3 * 2, box.size());
        Set<List<Integer>> unique = new HashSet<>(box);
        assertEquals(box.size(), unique.size());
    }

    @Test
    void tooLargeShapesDoNotFit() {
        assertTrue(PlacementShape.fits(PlacementMode.BOX, new int[]{0, 0, 0}, new int[]{15, 15, 15}, 1), "one block fits");
        assertFalse(PlacementShape.fits(PlacementMode.BOX, new int[]{0, 0, 0}, new int[]{63, 63, 63}, 1), "64 blocks do not");
        assertFalse(PlacementShape.fits(PlacementMode.LINE, new int[]{0, 0, 0}, new int[]{500, 0, 0}, 1), "longer than eight blocks");
        assertTrue(PlacementShape.fits(PlacementMode.PLANE, new int[]{0, 0, 0}, new int[]{100, 40, 100}, 1),
                "a plane only counts its two sides");
    }
}
