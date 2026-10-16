/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.item;

import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import com.nokhxyr.chiseledblocks.block.Materials;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGridBuilder;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksComponents;
import com.nokhxyr.chiseledblocks.registry.ChiseledItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A carved block picked up with its carving. Placing it restores the carving through the block
 * entity's implicit components. It is named after its main material and its tooltip lists what
 * it is made of.
 *
 * <p>There is one such item per carvable material ({@link ChiseledItems}), plus the generic
 * {@code chiseledblocks:chiseled_block}. A material item without a carving (from a command, a recipe or
 * an item list) shows and places an example shape in its material.
 */
public class ChiseledBlockItem extends BlockItem {
    /** Materials listed one by one in the tooltip; the rest are counted. */
    private static final int LISTED = 6;

    private final @Nullable Block material;
    private volatile @Nullable PixelGrid example;

    /** The generic item, for carvings whose main material has no item of its own. */
    public ChiseledBlockItem(Block block, Properties properties) {
        this(block, properties, null);
    }

    public ChiseledBlockItem(Block block, Properties properties, @Nullable Block material) {
        super(block, properties);
        this.material = material;
    }

    /** The material this item stands for, or null for the generic item. */
    public @Nullable Block material() {
        return material;
    }

    @Override
    public void registerBlocks(Map<Block, Item> blockToItem, Item item) {
        // Only the generic item is "the" item of the carved block; the material items share it.
        if (material == null) {
            super.registerBlocks(blockToItem, item);
        }
    }

    @Override
    public void removeFromBlockToItemMap(Map<Block, Item> blockToItem, Item item) {
        if (material == null) {
            super.removeFromBlockToItemMap(blockToItem, item);
        }
    }

    /** The carving the stack holds, or for a material item without one, its example shape. */
    public @Nullable PixelGrid displayGrid(ItemStack stack) {
        PixelGrid grid = stack.get(ChiseledBlocksComponents.GRID.get());
        if (grid != null || material == null) {
            return grid;
        }
        PixelGrid shape = example;
        if (shape == null) {
            // A step: full lower half, and the back of the upper half.
            int id = Materials.id(material.defaultBlockState());
            shape = ChiseledBlockEntity.INTERNER.intern(new PixelGridBuilder()
                    .fill(0, 0, 0, 16, 8, 16, id)
                    .fill(0, 8, 8, 16, 16, 16, id)
                    .build());
            example = shape;
        }
        return shape;
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        ItemStack stack = context.getItemInHand();
        PixelGrid grid = displayGrid(stack);
        if (grid == null || grid.isEmpty()) {
            return InteractionResult.FAIL;
        }
        if (!stack.has(ChiseledBlocksComponents.GRID.get())) {
            stack.set(ChiseledBlocksComponents.GRID.get(), grid);
        }
        return super.place(context);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        // A carving held as the generic item (older stacks, other mods) becomes its material's item.
        if (material == null && !level.isClientSide && entity instanceof Player player
                && slot < player.getInventory().items.size() && player.getInventory().items.get(slot) == stack) {
            ItemStack converted = ChiseledItems.withMaterialItem(stack);
            if (converted != stack) {
                player.getInventory().items.set(slot, converted);
            }
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        PixelGrid grid = displayGrid(stack);
        if (grid == null || grid.isEmpty()) {
            return super.getName(stack);
        }
        GridSummary summary = GridSummary.of(grid);
        Component main = summary.materials().get(0).material().getBlock().getName();
        int others = summary.materials().size() - 1;
        return others == 0
                ? Component.translatable("block.chiseledblocks.chiseled_block.named", main)
                : Component.translatable("block.chiseledblocks.chiseled_block.mixed", main, others);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        PixelGrid grid = displayGrid(stack);
        if (grid == null || grid.isEmpty()) {
            return;
        }
        if (!stack.has(ChiseledBlocksComponents.GRID.get())) {
            tooltip.add(Component.translatable("chiseledblocks.chiseled.example").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        GridSummary summary = GridSummary.of(grid);
        for (int i = 0; i < Math.min(LISTED, summary.materials().size()); i++) {
            GridSummary.Entry entry = summary.materials().get(i);
            int rgb = swatch(entry);
            tooltip.add(Component.literal("■ ")
                    .withStyle(style -> style.withColor(TextColor.fromRgb(rgb)))
                    .append(Component.translatable("chiseledblocks.chiseled.material", entry.material().getBlock().getName(),
                            percent(entry.bits()), entry.bits()).withStyle(ChatFormatting.GRAY)));
        }
        if (summary.materials().size() > LISTED) {
            tooltip.add(Component.translatable("chiseledblocks.chiseled.more", summary.materials().size() - LISTED)
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("chiseledblocks.chiseled.volume", summary.filled(), PixelGrid.VOLUME,
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
        double value = bits * 100.0 / PixelGrid.VOLUME;
        return String.format(Locale.ROOT, value < 1 ? "%.1f" : "%.0f", value);
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
