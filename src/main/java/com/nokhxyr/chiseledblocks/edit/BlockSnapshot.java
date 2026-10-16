/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.edit;

import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import com.nokhxyr.chiseledblocks.block.Materials;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksRegistries;
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
public record BlockSnapshot(@Nullable BlockState plain, @Nullable PixelGrid grid) {

    public static BlockSnapshot of(BlockState plain) {
        return new BlockSnapshot(plain, null);
    }

    /** Normalises a grid the way {@link EditBatcher} does: empty is air, uniform is the plain block. */
    public static BlockSnapshot of(PixelGrid grid) {
        if (grid.isEmpty()) {
            return of(Blocks.AIR.defaultBlockState());
        }
        if (grid.isUniformSolid()) {
            return of(Materials.state(grid.paletteEntry(0)));
        }
        return new BlockSnapshot(null, ChiseledBlockEntity.INTERNER.intern(grid));
    }

    /** The current content of a position, pending edits of this tick included. */
    public static BlockSnapshot current(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ChiseledBlockEntity carved) {
            return of(carved.effectiveGrid());
        }
        return of(level.getBlockState(pos));
    }

    /** Puts this content back at {@code pos}, through the normal edit pipeline. */
    public void apply(ServerLevel level, BlockPos pos) {
        if (grid != null) {
            if (level.getBlockEntity(pos) instanceof ChiseledBlockEntity carved) {
                carved.edit(builder -> {
                    for (int i = 0; i < PixelGrid.VOLUME; i++) {
                        builder.set(i, grid.get(i));
                    }
                });
            } else {
                level.setBlock(pos, ChiseledBlocksRegistries.CHISELED_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
                if (level.getBlockEntity(pos) instanceof ChiseledBlockEntity carved) {
                    carved.setGrid(grid);
                }
            }
            return;
        }
        if (level.getBlockEntity(pos) instanceof ChiseledBlockEntity carved) {
            carved.takePending();
        }
        level.setBlock(pos, plain, Block.UPDATE_ALL);
    }
}
