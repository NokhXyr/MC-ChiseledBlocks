/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.item;

import com.nokhxyr.bitsmith.edit.BitPlacement;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** One bit of a material. Right click places it next to the clicked bit. */
public class BitItem extends Item {

    public BitItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(BlockState material, int count) {
        ItemStack stack = new ItemStack(BitsmithRegistries.BIT.get(), count);
        stack.set(BitsmithComponents.BIT_MATERIAL.get(), material);
        return stack;
    }

    public static BlockState material(ItemStack stack) {
        return stack.getOrDefault(BitsmithComponents.BIT_MATERIAL.get(), Blocks.AIR.defaultBlockState());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockState material = material(context.getItemInHand());
        if (material.isAir()) {
            return InteractionResult.FAIL;
        }
        return BitPlacement.place(context, material, () -> context.getItemInHand().shrink(1));
    }

    @Override
    public Component getName(ItemStack stack) {
        BlockState material = material(stack);
        if (material.isAir()) {
            return super.getName(stack);
        }
        return Component.translatable("item.bitsmith.bit.named", material.getBlock().getName());
    }
}
