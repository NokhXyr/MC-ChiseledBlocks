/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.item;

import com.nokhxyr.bitsmith.edit.BitPlacement;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Collects the bits chiseled while it is in the inventory. Held in hand, it places bits of the
 * selected material (cycle with the mode key); sneak and right click in the air to take out a
 * stack of the selected material.
 */
public class BitBagItem extends Item {

    public BitBagItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static BagContents contents(ItemStack stack) {
        return stack.getOrDefault(BitsmithComponents.BAG_CONTENTS.get(), BagContents.EMPTY);
    }

    public static void setContents(ItemStack stack, BagContents contents) {
        stack.set(BitsmithComponents.BAG_CONTENTS.get(), contents);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack bag = context.getItemInHand();
        BagContents.Entry entry = contents(bag).selectedEntry();
        if (entry == null) {
            return InteractionResult.FAIL;
        }
        return BitPlacement.place(context, entry.material(),
                () -> setContents(bag, contents(bag).remove(entry.material(), 1)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bag = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(bag);
        }
        BagContents contents = contents(bag);
        BagContents.Entry entry = contents.selectedEntry();
        if (entry == null) {
            return InteractionResultHolder.fail(bag);
        }
        if (!level.isClientSide) {
            int amount = Math.min(64, entry.count());
            setContents(bag, contents.remove(entry.material(), amount));
            ItemStack bits = BitItem.of(entry.material(), amount);
            if (!player.getInventory().add(bits)) {
                player.drop(bits, false);
            }
        }
        return InteractionResultHolder.sidedSuccess(bag, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        BagContents contents = contents(stack);
        if (contents.isEmpty()) {
            tooltip.add(Component.translatable("bitsmith.bag.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        BagContents.Entry selected = contents.selectedEntry();
        for (BagContents.Entry entry : contents.entries()) {
            Component line = Component.translatable("bitsmith.bag.entry", entry.material().getBlock().getName(), entry.count());
            tooltip.add(line.copy().withStyle(entry == selected ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("bitsmith.bag.keys").withStyle(ChatFormatting.DARK_GRAY));
    }
}
