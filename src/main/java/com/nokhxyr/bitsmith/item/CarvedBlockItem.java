/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.item;

import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import com.nokhxyr.bitsmith.registry.CarvedItems;
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
 * <p>There is one such item per carvable material ({@link CarvedItems}), plus the generic
 * {@code bitsmith:carved_block}. A material item without a carving (from a command, a recipe or
 * an item list) shows and places an example shape in its material.
 */
public class CarvedBlockItem extends BlockItem {
    /** Materials listed one by one in the tooltip; the rest are counted. */
    private static final int LISTED = 6;

    private final @Nullable Block material;
    private volatile @Nullable BitGrid example;

    /** The generic item, for carvings whose main material has no item of its own. */
    public CarvedBlockItem(Block block, Properties properties) {
        this(block, properties, null);
    }

    public CarvedBlockItem(Block block, Properties properties, @Nullable Block material) {
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
    public @Nullable BitGrid displayGrid(ItemStack stack) {
        BitGrid grid = stack.get(BitsmithComponents.GRID.get());
        if (grid != null || material == null) {
            return grid;
        }
        BitGrid shape = example;
        if (shape == null) {
            // A step: full lower half, and the back of the upper half.
            int id = Materials.id(material.defaultBlockState());
            shape = CarvedBlockEntity.INTERNER.intern(new BitGridBuilder()
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
        BitGrid grid = displayGrid(stack);
        if (grid == null || grid.isEmpty()) {
            return InteractionResult.FAIL;
        }
        if (!stack.has(BitsmithComponents.GRID.get())) {
            stack.set(BitsmithComponents.GRID.get(), grid);
        }
        return super.place(context);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        // A carving held as the generic item (older stacks, other mods) becomes its material's item.
        if (material == null && !level.isClientSide && entity instanceof Player player
                && slot < player.getInventory().items.size() && player.getInventory().items.get(slot) == stack) {
            ItemStack converted = CarvedItems.withMaterialItem(stack);
            if (converted != stack) {
                player.getInventory().items.set(slot, converted);
            }
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        BitGrid grid = displayGrid(stack);
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
        BitGrid grid = displayGrid(stack);
        if (grid == null || grid.isEmpty()) {
            return;
        }
        if (!stack.has(BitsmithComponents.GRID.get())) {
            tooltip.add(Component.translatable("bitsmith.carved.example").withStyle(ChatFormatting.DARK_GRAY));
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
