/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.machine;

import com.mojang.serialization.MapCodec;
import com.nokhxyr.bitsmith.item.BitBagItem;
import com.nokhxyr.bitsmith.item.CreativeBitBagItem;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The bit mill: blocks in, bits out. Right click with blocks to load it, with a bit bag to fill
 * the bag, with an empty hand to take a stack of bits (sneak: take the waiting blocks back).
 * Comparators read how full its bit store is.
 */
public final class BitMillBlock extends BaseEntityBlock {
    public static final MapCodec<BitMillBlock> CODEC = simpleCodec(BitMillBlock::new);

    public BitMillBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BitMillBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, BitsmithRegistries.BIT_MILL_ENTITY.get(), BitMillBlockEntity::serverTick);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BitMillBlockEntity mill)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.getItem() instanceof BitBagItem bag && !(bag instanceof CreativeBitBagItem)) {
            if (!level.isClientSide && mill.bits() > 0) {
                int stored = bag.store(stack, mill.material(), mill.bits());
                int left = stored;
                while (left > 0) {
                    left -= mill.takeBits(left).getCount();
                }
                if (stored == 0) {
                    player.displayClientMessage(Component.translatable("bitsmith.mill.bag_full"), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (BitMillBlockEntity.materialOf(stack) != null) {
            if (!level.isClientSide) {
                ItemStack left = mill.insert(stack);
                player.setItemInHand(hand, left);
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BitMillBlockEntity mill)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack taken = player.isShiftKeyDown() ? mill.takeInput() : mill.takeBits(64);
            if (taken.isEmpty()) {
                player.displayClientMessage(Component.translatable("bitsmith.mill.status", mill.input().getCount(),
                        mill.bits(), mill.bits() == 0 ? Component.literal("-") : mill.material().getBlock().getName()), true);
            } else if (!player.getInventory().add(taken)) {
                player.drop(taken, false);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof BitMillBlockEntity mill ? mill.signal() : 0;
    }
}
