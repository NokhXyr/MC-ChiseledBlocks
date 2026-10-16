/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.item;

import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;

import java.util.List;
import java.util.Locale;

/**
 * A carved block picked up with its carving. Placing it restores the carving through the block
 * entity's implicit components; a stack without a carving cannot be placed. It is named after its
 * main material and its tooltip lists what it is made of.
 */
public class CarvedBlockItem extends BlockItem {
    /** Materials listed one by one in the tooltip; the rest are counted. */
    private static final int LISTED = 6;

    public CarvedBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        BitGrid grid = context.getItemInHand().get(BitsmithComponents.GRID.get());
        if (grid == null || grid.isEmpty()) {
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }

    @Override
    public Component getName(ItemStack stack) {
        BitGrid grid = stack.get(BitsmithComponents.GRID.get());
        if (grid == null || grid.isEmpty()) {
            return super.getName(stack);
        }
        GridSummary summary = GridSummary.of(grid);
        Component main = summary.materials().get(0).material().getBlock().getName();
        int others = summary.materials().size() - 1;
        return others == 0
                ? Component.translatable("block.bitsmith.carved_block.named", main)
                : Component.translatable("block.bitsmith.carved_block.mixed", main, others);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BitGrid grid = stack.get(BitsmithComponents.GRID.get());
        if (grid == null || grid.isEmpty()) {
            return;
        }
        GridSummary summary = GridSummary.of(grid);
        for (int i = 0; i < Math.min(LISTED, summary.materials().size()); i++) {
            GridSummary.Entry entry = summary.materials().get(i);
            int rgb = swatch(entry);
            tooltip.add(Component.literal("■ ")
                    .withStyle(style -> style.withColor(TextColor.fromRgb(rgb)))
                    .append(Component.translatable("bitsmith.carved.material", entry.material().getBlock().getName(),
                            percent(entry.bits()), entry.bits()).withStyle(ChatFormatting.GRAY)));
        }
        if (summary.materials().size() > LISTED) {
            tooltip.add(Component.translatable("bitsmith.carved.more", summary.materials().size() - LISTED)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("bitsmith.carved.volume", summary.filled(), BitGrid.VOLUME,
                percent(summary.filled())).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** The material's map colour, grey when it has none or needs a world to tell. */
    private static int swatch(GridSummary.Entry entry) {
        try {
            MapColor colour = entry.material().getMapColor(null, null);
            return colour == null || colour == MapColor.NONE ? 0xAAAAAA : colour.col;
        } catch (RuntimeException e) {
            // Some modded blocks look at the world for their colour; there is none for an item.
            return 0xAAAAAA;
        }
    }

    private static String percent(int bits) {
        double value = bits * 100.0 / BitGrid.VOLUME;
        return String.format(Locale.ROOT, value < 1 ? "%.1f" : "%.0f", value);
    }
}
