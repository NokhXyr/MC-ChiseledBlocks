/*
 * Bitsmith: Create
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.create;

import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A piece of a block between the machines of the bit line: a slice (16 x 16 x 1 bits) or a rod
 * (16 x 1 x 1 bits) of one material, drawn in 3D in that material.
 */
public final class BitPieceItem extends Item {
    private final String nameKey;

    public BitPieceItem(Properties properties, String nameKey) {
        super(properties);
        this.nameKey = nameKey;
    }

    public static ItemStack of(Item item, BlockState material, int count) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(BitsmithComponents.BIT_MATERIAL.get(), material);
        return stack;
    }

    public static BlockState material(ItemStack stack) {
        return stack.getOrDefault(BitsmithComponents.BIT_MATERIAL.get(), Blocks.AIR.defaultBlockState());
    }

    @Override
    public Component getName(ItemStack stack) {
        BlockState material = material(stack);
        return material.isAir() ? super.getName(stack) : Component.translatable(nameKey, material.getBlock().getName());
    }
}
