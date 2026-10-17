/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.edit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.GameMasterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Asks protection mods (land claims, spawn protection, region plugins) whether a player may change a
 * block. They listen to the block break and place events, which a carving does not go through by
 * itself: a chisel stroke arrives as a message of this mod, and pixels can be added to a block next
 * to the one clicked. Each edit asks here first, as breaking or placing that block would.
 */
public final class EditPermission {

    private EditPermission() {
    }

    /** Whether {@code player} may take material away from the block at {@code pos} (chisel, saw, pattern carving, undo). */
    public static boolean mayRemove(ServerPlayer player, BlockPos pos) {
        // Posted here rather than through CommonHooks.fireBlockBreak: that one also refuses when the
        // held item cannot attack blocks, which the chisel declines on purpose so it never breaks a
        // whole block.
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, pos, state, player);
        event.setCanceled(player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())
                || (state.getBlock() instanceof GameMasterBlock && !player.canUseGameMasterBlocks()));
        return !NeoForge.EVENT_BUS.post(event).isCanceled();
    }

    /** Whether {@code player} may add material to the block at {@code pos} (pixels, shapes, printed patterns). */
    public static boolean mayAdd(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        return !EventHooks.onBlockPlace(player, net.neoforged.neoforge.common.util.BlockSnapshot.create(level.dimension(), level, pos),
                Direction.UP);
    }
}
