/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.shape;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import net.minecraft.world.phys.shapes.BitSetDiscreteVoxelShape;
import net.minecraft.world.phys.shapes.CubeVoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.concurrent.ExecutionException;

/**
 * One {@link VoxelShape} per distinct grid. The shape is filled straight from the voxels into a
 * 16^3 discrete shape, a single linear pass, instead of OR-ing boxes together.
 *
 * <p>Keys are weak and compared by identity: grids are interned, so identical carvings share
 * the entry, and an entry goes away with the last block that used it.
 */
public final class ShapeCache {
    private static final Cache<BitGrid, VoxelShape> CACHE = CacheBuilder.newBuilder()
            .weakKeys()
            .maximumSize(16_384)
            .build();

    private ShapeCache() {
    }

    public static VoxelShape get(BitGrid grid) {
        if (grid.isEmpty()) {
            return Shapes.empty();
        }
        if (grid.isUniformSolid()) {
            return Shapes.block();
        }
        try {
            return CACHE.get(grid, () -> build(grid));
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to build carved block shape", e.getCause());
        }
    }

    private static VoxelShape build(BitGrid grid) {
        int size = BitGrid.SIZE;
        BitSetDiscreteVoxelShape discrete = new BitSetDiscreteVoxelShape(size, size, size);
        for (int i = 0; i < BitGrid.VOLUME; i++) {
            if (grid.get(i) != BitGrid.AIR) {
                discrete.fill(i & 15, i >> 8, (i >> 4) & 15);
            }
        }
        return new CubeVoxelShape(discrete);
    }
}
