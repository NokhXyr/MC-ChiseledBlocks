/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.edit;

import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.item.ChiselItem;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.WeakHashMap;

/** Server side, authoritative handling of chisel actions. */
public final class ChiselLogic {
    /** Most chisel actions one player may perform in one tick. */
    private static final int MAX_ACTIONS_PER_TICK = 4;
    private static final Map<ServerPlayer, long[]> BUDGET = new WeakHashMap<>();

    private ChiselLogic() {
    }

    public static void chisel(ServerPlayer player, BlockPos pos, int voxel) {
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos)
                || !player.canInteractWithBlock(pos, 1.0)
                || !player.mayBuild()
                || !level.mayInteract(player, pos)
                || !(player.getMainHandItem().getItem() instanceof ChiselItem)
                || !takeBudget(player, level.getGameTime())) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (level.getBlockEntity(pos) instanceof CarvedBlockEntity carved) {
            if (carved.peek(voxel) != BitGrid.AIR) {
                carved.edit(builder -> builder.set(voxel, BitGrid.AIR));
            }
            return;
        }

        if (!Materials.isCarvable(state, level, pos)) {
            return;
        }

        // Convert the plain block. The client receives the new block and its data through
        // vanilla's block entity sync, already carved.
        BitGrid grid = BitGrid.filled(Materials.id(state)).toBuilder().set(voxel, BitGrid.AIR).build();
        level.setBlock(pos, BitsmithRegistries.CARVED_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof CarvedBlockEntity carved) {
            carved.setGrid(grid);
        }
    }

    private static boolean takeBudget(ServerPlayer player, long gameTime) {
        long[] budget = BUDGET.computeIfAbsent(player, p -> new long[2]);
        if (budget[0] != gameTime) {
            budget[0] = gameTime;
            budget[1] = 0;
        }
        return budget[1]++ < MAX_ACTIONS_PER_TICK;
    }
}
