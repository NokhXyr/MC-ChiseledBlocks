/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.item;

import com.nokhxyr.chiseledblocks.edit.PixelPlacement;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksComponents;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** One bit of a material. Right click places it next to the clicked bit. */
public class PixelItem extends Item {

    public PixelItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(BlockState material, int count) {
        ItemStack stack = new ItemStack(ChiseledBlocksRegistries.PIXEL.get(), count);
        stack.set(ChiseledBlocksComponents.PIXEL_MATERIAL.get(), material);
        return stack;
    }

    public static BlockState material(ItemStack stack) {
        return stack.getOrDefault(ChiseledBlocksComponents.PIXEL_MATERIAL.get(), Blocks.AIR.defaultBlockState());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState material = material(context.getItemInHand());
        if (material.isAir()) {
            return InteractionResult.FAIL;
        }
        ItemStack bits = context.getItemInHand();
        return PixelPlacement.place(context, material, bits.getCount(), bits::shrink);
    }

    @Override
    public Component getName(ItemStack stack) {
        BlockState material = material(stack);
        if (material.isAir()) {
            return super.getName(stack);
        }
        return Component.translatable("item.chiseledblocks.pixel.named", material.getBlock().getName());
    }
}
