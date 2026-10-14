/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.block;

import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.IntPredicate;

/**
 * Bridge between voxel material ids and block states. A material id is the runtime block state
 * id, which the server and its clients share; it is never written to disk (see {@link GridNbt}).
 */
public final class Materials {
    /** Materials that hide what lies behind them, for face culling inside a grid. */
    public static final IntPredicate OPAQUE = id -> id != BitGrid.AIR && state(id).canOcclude();

    private Materials() {
    }

    public static int id(BlockState state) {
        return Block.getId(state);
    }

    public static BlockState state(int id) {
        return Block.stateById(id);
    }

    /** Called once at startup: the voxel core assumes air is material 0. */
    public static void checkAirIsZero() {
        if (Block.getId(net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()) != BitGrid.AIR) {
            throw new IllegalStateException("Air block state id is not 0, the voxel encoding cannot work");
        }
    }

    /** Whether a plain block may be turned into a carved block made of it. */
    public static boolean isCarvable(BlockState state, BlockGetter level, BlockPos pos) {
        return isValidMaterial(state)
                && state.getDestroySpeed(level, pos) >= 0
                && state.isCollisionShapeFullBlock(level, pos);
    }

    /** Whether a block state may exist as bits at all. */
    public static boolean isValidMaterial(BlockState state) {
        return !state.isAir()
                && !(state.getBlock() instanceof CarvedBlock)
                && !state.hasBlockEntity()
                && state.getRenderShape() == RenderShape.MODEL
                && state.getFluidState().isEmpty();
    }
}
