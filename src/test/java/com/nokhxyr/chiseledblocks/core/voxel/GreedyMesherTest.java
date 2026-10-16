/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.core.voxel;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;

class GreedyMesherTest {
    private static final int STONE = 1;
    private static final int GLASS = 2;
    private static final IntPredicate OPAQUE = m -> m != GLASS;

    private record Quad(Face face, int material, int plane, int u0, int v0, int u1, int v1, boolean outer) {
        int area() {
            return (u1 - u0) * (v1 - v0);
        }
    }

    private static List<Quad> mesh(PixelGrid grid) {
        List<Quad> quads = new ArrayList<>();
        new GreedyMesher().mesh(grid, OPAQUE, (f, m, p, u0, v0, u1, v1, o) -> quads.add(new Quad(f, m, p, u0, v0, u1, v1, o)));
        return quads;
    }

    @Test
    void fullBlockIsSixOuterQuads() {
        List<Quad> quads = mesh(PixelGrid.filled(STONE));
        assertEquals(6, quads.size());
        assertTrue(quads.stream().allMatch(Quad::outer));
    }

    @Test
    void emptyGridHasNoQuads() {
        assertTrue(mesh(PixelGrid.filled(PixelGrid.AIR)).isEmpty());
    }

    @Test
    void singleVoxelIsSixUnitQuads() {
        List<Quad> quads = mesh(new PixelGridBuilder().set(5, 5, 5, STONE).build());
        assertEquals(6, quads.size());
        assertTrue(quads.stream().allMatch(q -> q.area() == 1 && !q.outer()));
    }

    @Test
    void halfSlabMergesEachSideIntoOneQuad() {
        List<Quad> quads = mesh(new PixelGridBuilder().fill(0, 0, 0, 16, 8, 16, STONE).build());
        assertEquals(6, quads.size());
        Quad top = quads.stream().filter(q -> q.face() == Face.UP).findFirst().orElseThrow();
        assertEquals(8, top.plane());
        assertFalse(top.outer());
        assertEquals(256, top.area());
    }

    @Test
    void touchingOpaqueMaterialsHideTheirSharedFace() {
        PixelGrid grid = new PixelGridBuilder()
                .fill(0, 0, 0, 16, 8, 16, STONE)
                .fill(0, 8, 0, 16, 16, 16, 3)
                .build();
        List<Quad> quads = mesh(grid);
        assertTrue(quads.stream().allMatch(Quad::outer), "no inner face between two opaque materials");
    }

    @Test
    void stoneBehindGlassStaysVisible() {
        PixelGrid grid = new PixelGridBuilder()
                .fill(0, 0, 0, 16, 8, 16, STONE)
                .fill(0, 8, 0, 16, 16, 16, GLASS)
                .build();
        List<Quad> quads = mesh(grid);
        assertTrue(quads.stream().anyMatch(q -> q.face() == Face.UP && q.material() == STONE && !q.outer()));
        assertTrue(quads.stream().noneMatch(q -> q.face() == Face.DOWN && q.material() == GLASS && !q.outer()),
                "glass face against opaque stone is hidden");
    }

    @Test
    void visibleAreaMatchesNaiveFaceCount() {
        PixelGrid grid = new PixelGridBuilder()
                .fill(2, 0, 3, 11, 9, 14, STONE)
                .fill(4, 4, 4, 8, 16, 8, PixelGrid.AIR)
                .set(15, 15, 15, STONE)
                .build();
        int area = mesh(grid).stream().mapToInt(Quad::area).sum();
        assertEquals(naiveVisibleFaces(grid), area);
    }

    private static int naiveVisibleFaces(PixelGrid grid) {
        int faces = 0;
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (grid.get(x, y, z) == PixelGrid.AIR) {
                        continue;
                    }
                    int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                    for (int[] d : dirs) {
                        int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                        boolean outside = nx < 0 || ny < 0 || nz < 0 || nx > 15 || ny > 15 || nz > 15;
                        if (outside || grid.get(nx, ny, nz) == PixelGrid.AIR) {
                            faces++;
                        }
                    }
                }
            }
        }
        return faces;
    }
}
