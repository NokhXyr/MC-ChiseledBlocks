/*
 * Bitsmith: Create
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.create;

import com.nokhxyr.bitsmith.item.BitBagItem;
import com.nokhxyr.bitsmith.item.CreativeBitBagItem;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Mechanical Bit Mill block: a shaft runs through it along its axis. Right click with blocks
 * to load it, with a bit bag to fill the bag, empty handed to take bits (sneak: take the waiting
 * blocks back). Comparators read how full it is.
 */
public final class MechanicalBitMillBlock extends RotatedPillarKineticBlock implements IBE<MechanicalBitMillBlockEntity> {

    public MechanicalBitMillBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(AXIS);
    }

    @Override
    public Class<MechanicalBitMillBlockEntity> getBlockEntityClass() {
        return MechanicalBitMillBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MechanicalBitMillBlockEntity> getBlockEntityType() {
        return BitsmithCreate.MILL_ENTITY.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MechanicalBitMillBlockEntity mill)) {
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
                    player.displayClientMessage(Component.translatable("bitsmith_create.mill.bag_full"), true);
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (MechanicalBitMillBlockEntity.materialOf(stack) != null) {
            if (!level.isClientSide) {
                player.setItemInHand(hand, mill.insert(stack));
                level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof MechanicalBitMillBlockEntity mill)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack taken = player.isShiftKeyDown() ? mill.takeInput() : mill.takeBits(64);
            if (taken.isEmpty()) {
                player.displayClientMessage(Component.translatable("bitsmith_create.mill.status", mill.input().getCount(),
                        mill.bits(), mill.bits() == 0 ? Component.literal("-") : mill.material().getBlock().getName(),
                        Math.round(Math.abs(mill.getSpeed()))), true);
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
        return level.getBlockEntity(pos) instanceof MechanicalBitMillBlockEntity mill ? mill.signal() : 0;
    }
}
