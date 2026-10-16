/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.item;

import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import com.nokhxyr.chiseledblocks.block.Materials;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.core.voxel.GridTransforms;
import com.nokhxyr.chiseledblocks.edit.BlockSnapshot;
import com.nokhxyr.chiseledblocks.edit.EditHistory;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.function.UnaryOperator;

/**
 * Right click turns a carved block a quarter turn around the axis of the clicked face; sneaking
 * mirrors it across that axis instead. Oriented materials (logs, stairs) follow the horizontal
 * turns and mirrors.
 */
public class WrenchItem extends Item {

    public WrenchItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos pos = context.getClickedPos();
        if (!(context.getLevel().getBlockEntity(pos) instanceof ChiseledBlockEntity carved)) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level)
                || !player.mayBuild() || !level.mayInteract(player, pos)) {
            return InteractionResult.FAIL;
        }

        Direction.Axis axis = context.getClickedFace().getAxis();
        boolean flip = player.isShiftKeyDown();
        PixelGrid grid = carved.effectiveGrid();
        PixelGrid turned = flip
                ? GridTransforms.mirror(grid, axis.ordinal(), remap(state -> state.mirror(mirrorFor(axis))))
                : GridTransforms.rotate(grid, axis.ordinal(),
                        axis == Direction.Axis.Y ? remap(state -> state.rotate(Rotation.CLOCKWISE_90)) : IntUnaryOperator.identity());

        BlockSnapshot before = BlockSnapshot.current(level, pos);
        BlockSnapshot after = BlockSnapshot.of(turned);
        after.apply(level, pos);
        EditHistory.record(player, pos, before, after, new Int2IntOpenHashMap());
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.8F, 1.2F);
        return InteractionResult.CONSUME;
    }

    private static Mirror mirrorFor(Direction.Axis axis) {
        return switch (axis) {
            case X -> Mirror.FRONT_BACK;
            case Z -> Mirror.LEFT_RIGHT;
            case Y -> Mirror.NONE;
        };
    }

    /** Applies a state change to a material, keeping the original when the result cannot be a bit. */
    private static IntUnaryOperator remap(UnaryOperator<BlockState> change) {
        return material -> {
            BlockState changed = change.apply(Materials.state(material));
            return Materials.isValidMaterial(changed) ? Materials.id(changed) : material;
        };
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("chiseledblocks.wrench.help").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Keeps the item in hand when only its data changes (wear, settings, contents): the stack synced
     * back from the server after each stroke is a new object, which would otherwise lower and raise
     * the hand every time.
     */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || !oldStack.is(newStack.getItem());
    }
}
