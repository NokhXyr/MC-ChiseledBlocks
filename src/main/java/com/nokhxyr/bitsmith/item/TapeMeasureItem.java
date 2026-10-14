/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Measures between two bits. Entirely client side: the clicks are handled by
 * {@code TapeMeasureClient}, nothing is sent to the server.
 */
public class TapeMeasureItem extends Item {

    public TapeMeasureItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("bitsmith.tape.help").withStyle(ChatFormatting.GRAY));
    }
}
