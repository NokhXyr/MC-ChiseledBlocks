/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.item;

import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/** Right click a carved block to record its carving into a pattern. */
public class BlankPatternItem extends Item {

    public BlankPatternItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof ChiseledBlockEntity carved)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        PixelGrid grid = carved.effectiveGrid();
        Player player = context.getPlayer();
        ItemStack blank = context.getItemInHand();
        ItemStack pattern = PatternItem.of(grid, PatternMode.POSITIVE);
        if (blank.getCount() == 1 && player != null) {
            player.setItemInHand(context.getHand(), pattern);
        } else {
            blank.shrink(1);
            if (player != null && !player.getInventory().add(pattern)) {
                player.drop(pattern, false);
            }
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("chiseledblocks.blank_pattern.help").withStyle(ChatFormatting.GRAY));
    }
}
