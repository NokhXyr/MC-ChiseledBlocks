/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.core.voxel;

import java.util.Arrays;
import java.util.BitSet;

/**
 * Mutable working copy used while editing. Edits are cheap array writes; {@link #build()} packs
 * the result into an immutable {@link BitGrid} once per batch. The builder remembers which
 * voxels changed, so a batch can be sent over the network as a delta.
 */
public final class BitGridBuilder {
    private final int[] voxels;
    private final BitSet changed = new BitSet(BitGrid.VOLUME);

    public BitGridBuilder() {
        this(new int[BitGrid.VOLUME]);
    }

    BitGridBuilder(int[] voxels) {
        this.voxels = voxels;
    }

    public int get(int x, int y, int z) {
        return voxels[BitGrid.index(x, y, z)];
    }

    public int get(int index) {
        return voxels[index];
    }

    public BitGridBuilder set(int x, int y, int z, int material) {
        return set(BitGrid.index(x, y, z), material);
    }

    public BitGridBuilder set(int index, int material) {
        if (voxels[index] != material) {
            voxels[index] = material;
            changed.set(index);
        }
        return this;
    }

    /** Fills the box from (minX, minY, minZ) inclusive to (maxX, maxY, maxZ) exclusive. */
    public BitGridBuilder fill(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int material) {
        for (int y = minY; y < maxY; y++) {
            for (int z = minZ; z < maxZ; z++) {
                for (int x = minX; x < maxX; x++) {
                    set(x, y, z, material);
                }
            }
        }
        return this;
    }

    public BitGridBuilder fillAll(int material) {
        for (int i = 0; i < BitGrid.VOLUME; i++) {
            set(i, material);
        }
        return this;
    }

    /** Number of voxels whose value changed since this builder was created. */
    public int changes() {
        return changed.cardinality();
    }

    /** Indices of the voxels changed since this builder was created, in ascending order. */
    public int[] changedIndices() {
        return changed.stream().toArray();
    }

    public BitGrid build() {
        return BitGrid.of(Arrays.copyOf(voxels, voxels.length));
    }
}
