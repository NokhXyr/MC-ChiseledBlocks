/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client;

import com.nokhxyr.bitsmith.Bitsmith;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

/** Tooltips of Bitsmith's items in the colours of its admin panel: dark, with a blue edge. */
@EventBusSubscriber(modid = Bitsmith.MOD_ID, value = Dist.CLIENT)
public final class TooltipStyle {
    private static final int BACKGROUND = 0xF01B2028;
    private static final int BORDER_TOP = 0xFF4FC3F7;
    private static final int BORDER_BOTTOM = 0xFF2B6F8F;

    private TooltipStyle() {
    }

    @SubscribeEvent
    public static void onTooltipColour(RenderTooltipEvent.Color event) {
        ItemStack stack = event.getItemStack();
        if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(Bitsmith.MOD_ID)) {
            event.setBackground(BACKGROUND);
            event.setBorderStart(BORDER_TOP);
            event.setBorderEnd(BORDER_BOTTOM);
        }
    }
}
