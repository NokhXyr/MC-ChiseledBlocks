/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.core.voxel;

import java.util.Arrays;

/**
 * Mutable working copy used while editing. Edits are cheap array writes; {@link #build()} packs
 * the result into an immutable {@link BitGrid} once per batch.
 */
public final class BitGridBuilder {
    private final int[] voxels;
    private int changes;

    public BitGridBuilder() {
        this(new int[BitGrid.VOLUME]);
    }

    BitGridBuilder(int[] voxels) {
        this.voxels = voxels;
    }

    public int get(int x, int y, int z) {
        return voxels[BitGrid.index(x, y, z)];
    }

    public BitGridBuilder set(int x, int y, int z, int material) {
        int i = BitGrid.index(x, y, z);
        if (voxels[i] != material) {
            voxels[i] = material;
            changes++;
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
        Arrays.fill(voxels, material);
        changes++;
        return this;
    }

    /** Number of voxel writes that changed a value since this builder was created. */
    public int changes() {
        return changes;
    }

    public BitGrid build() {
        return BitGrid.of(voxels.clone());
    }
}
