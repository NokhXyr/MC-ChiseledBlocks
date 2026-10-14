/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.edit;

import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * What one block position holds, in the normalised form the edit pipeline leaves behind: a
 * plain block state, or a carving that is neither empty nor a single full material.
 */
public record BlockSnapshot(@Nullable BlockState plain, @Nullable BitGrid grid) {

    public static BlockSnapshot of(BlockState plain) {
        return new BlockSnapshot(plain, null);
    }

    /** Normalises a grid the way {@link EditBatcher} does: empty is air, uniform is the plain block. */
    public static BlockSnapshot of(BitGrid grid) {
        if (grid.isEmpty()) {
            return of(Blocks.AIR.defaultBlockState());
        }
        if (grid.isUniformSolid()) {
            return of(Materials.state(grid.paletteEntry(0)));
        }
        return new BlockSnapshot(null, CarvedBlockEntity.INTERNER.intern(grid));
    }

    /** The current content of a position, pending edits of this tick included. */
    public static BlockSnapshot current(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CarvedBlockEntity carved) {
            return of(carved.effectiveGrid());
        }
        return of(level.getBlockState(pos));
    }

    /** Puts this content back at {@code pos}, through the normal edit pipeline. */
    public void apply(ServerLevel level, BlockPos pos) {
        if (grid != null) {
            if (level.getBlockEntity(pos) instanceof CarvedBlockEntity carved) {
                carved.edit(builder -> {
                    for (int i = 0; i < BitGrid.VOLUME; i++) {
                        builder.set(i, grid.get(i));
                    }
                });
            } else {
                level.setBlock(pos, BitsmithRegistries.CARVED_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
                if (level.getBlockEntity(pos) instanceof CarvedBlockEntity carved) {
                    carved.setGrid(grid);
                }
            }
            return;
        }
        if (level.getBlockEntity(pos) instanceof CarvedBlockEntity carved) {
            carved.takePending();
        }
        level.setBlock(pos, plain, Block.UPDATE_ALL);
    }
}
