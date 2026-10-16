/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.core.voxel;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PixelGridTest {

    @Test
    void uniformGridHasNoPackedData() {
        PixelGrid grid = PixelGrid.filled(7);
        assertEquals(0, grid.bitsPerEntry());
        assertTrue(grid.isUniformSolid());
        assertEquals(7, grid.get(3, 9, 15));
        assertEquals(PixelGrid.VOLUME, grid.filledCount());
    }

    @Test
    void twoMaterialsUseOneBitPerVoxel() {
        PixelGrid grid = new PixelGridBuilder().fill(0, 0, 0, 16, 8, 16, 5).build();
        assertEquals(1, grid.bitsPerEntry());
        assertEquals(512 + 2 * Integer.BYTES, grid.payloadBytes());
        assertEquals(5, grid.get(0, 7, 0));
        assertEquals(PixelGrid.AIR, grid.get(0, 8, 0));
        assertEquals(PixelGrid.VOLUME / 2, grid.filledCount());
    }

    @Test
    void roundTripPreservesEveryVoxel() {
        Random random = new Random(42);
        int[] voxels = new int[PixelGrid.VOLUME];
        for (int i = 0; i < voxels.length; i++) {
            voxels[i] = random.nextInt(37);
        }
        PixelGrid grid = PixelGrid.of(voxels.clone());
        assertArrayEquals(voxels, grid.toArray(new int[PixelGrid.VOLUME]));
        assertEquals(6, grid.bitsPerEntry());
    }

    @Test
    void encodingIsCanonicalWhateverTheEditOrder() {
        PixelGrid a = new PixelGridBuilder().set(1, 1, 1, 9).set(2, 2, 2, 4).build();
        PixelGrid b = new PixelGridBuilder().set(2, 2, 2, 4).set(1, 1, 1, 9).build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void decodeRestoresSerializedGridWithRemappedPalette() {
        PixelGrid grid = new PixelGridBuilder().fill(0, 0, 0, 16, 4, 16, 5).set(3, 9, 3, 8).build();
        int[] remapped = new int[grid.paletteSize()];
        for (int i = 0; i < remapped.length; i++) {
            remapped[i] = grid.paletteEntry(i) == PixelGrid.AIR ? PixelGrid.AIR : grid.paletteEntry(i) + 100;
        }
        PixelGrid restored = PixelGrid.of(PixelGrid.decode(remapped, grid.bitsPerEntry(), grid.packedData()));
        assertEquals(105, restored.get(0, 0, 0));
        assertEquals(108, restored.get(3, 9, 3));
        assertEquals(PixelGrid.AIR, restored.get(3, 10, 3));
    }

    @Test
    void decodeNeverThrowsOnCorruptData() {
        int[] voxels = PixelGrid.decode(new int[]{0, 4}, 3, new long[]{-1L});
        assertEquals(PixelGrid.AIR, voxels[0], "palette index out of range becomes air");
        assertEquals(PixelGrid.AIR, voxels[PixelGrid.VOLUME - 1], "missing words become air");
    }

    @Test
    void builderTracksChangedIndices() {
        PixelGridBuilder builder = PixelGrid.filled(2).toBuilder();
        builder.set(0, 0, 0, 2).set(1, 0, 0, PixelGrid.AIR).set(0, 1, 0, PixelGrid.AIR);
        assertArrayEquals(new int[]{1, PixelGrid.index(0, 1, 0)}, builder.changedIndices());
    }

    @Test
    void internerSharesIdenticalGrids() {
        PixelGridInterner interner = new PixelGridInterner();
        PixelGrid a = new PixelGridBuilder().fill(0, 0, 0, 4, 4, 4, 3).build();
        PixelGrid b = new PixelGridBuilder().fill(0, 0, 0, 4, 4, 4, 3).build();
        assertNotSame(a, b);
        assertSame(interner.intern(a), interner.intern(b));
    }
}
