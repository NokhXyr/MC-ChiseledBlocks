/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.item;

import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.edit.PatternLogic;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksComponents;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A recorded carving. Right click prints it (positive mode) or carves its shape (negative mode);
 * V or the radial menu switches the mode; sneak and right click in the air wipes it back to a
 * blank pattern.
 */
public class PatternItem extends Item {

    public PatternItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack of(PixelGrid grid, PatternMode mode) {
        ItemStack stack = new ItemStack(ChiseledBlocksRegistries.PATTERN.get());
        stack.set(ChiseledBlocksComponents.GRID.get(), grid);
        stack.set(ChiseledBlocksComponents.PATTERN_MODE.get(), mode);
        return stack;
    }

    public static @Nullable PixelGrid grid(ItemStack stack) {
        return stack.get(ChiseledBlocksComponents.GRID.get());
    }

    public static PatternMode mode(ItemStack stack) {
        return stack.getOrDefault(ChiseledBlocksComponents.PATTERN_MODE.get(), PatternMode.POSITIVE);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        PixelGrid grid = grid(context.getItemInHand());
        if (grid == null || grid.isEmpty()) {
            return InteractionResult.FAIL;
        }
        return PatternLogic.use(context, grid, mode(context.getItemInHand()));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        ItemStack blank = new ItemStack(ChiseledBlocksRegistries.BLANK_PATTERN.get());
        return InteractionResultHolder.sidedSuccess(blank, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("chiseledblocks.pattern.mode", mode(stack).displayName()).withStyle(ChatFormatting.GRAY));
        PixelGrid grid = grid(stack);
        if (grid != null) {
            tooltip.add(Component.translatable("chiseledblocks.pattern.pixels", grid.filledCount()).withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("chiseledblocks.pattern.keys").withStyle(ChatFormatting.DARK_GRAY));
    }
}
