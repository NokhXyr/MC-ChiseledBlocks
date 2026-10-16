/*
 * Chiseled Blocks: Create
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.create;

import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A piece of a block between the machines of the pixel line: a slice (16 x 16 x 1 bits) or a rod
 * (16 x 1 x 1 bits) of one material, drawn in 3D in that material.
 */
public final class PieceItem extends Item {
    private final String nameKey;

    public PieceItem(Properties properties, String nameKey) {
        super(properties);
        this.nameKey = nameKey;
    }

    public static ItemStack of(Item item, BlockState material, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(ChiseledBlocksComponents.PIXEL_MATERIAL.get(), material);
        return stack;
    }

    public static BlockState material(ItemStack stack) {
        return stack.getOrDefault(ChiseledBlocksComponents.PIXEL_MATERIAL.get(), Blocks.AIR.defaultBlockState());
    }

    @Override
    public Component getName(ItemStack stack) {
        BlockState material = material(stack);
        return material.isAir() ? super.getName(stack) : Component.translatable(nameKey, material.getBlock().getName());
    }
}
