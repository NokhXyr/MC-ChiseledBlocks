/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.item;

import com.nokhxyr.bitsmith.edit.BitPlacement;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Collects the bits chiseled while it is in the inventory. Held in hand, it places bits of the
 * selected material (cycle with the mode key); sneak and right click in the air to take out a
 * stack of the selected material. In an inventory, right click bits onto it (or it onto bits) to
 * put them back.
 */
public class BitBagItem extends Item {
    /** Materials listed in the tooltip; the menu shows them all, page by page. */
    protected static final int TOOLTIP_LINES = 12;

    public BitBagItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static BagContents contents(ItemStack stack) {
        return stack.getOrDefault(BitsmithComponents.BAG_CONTENTS.get(), BagContents.EMPTY);
    }

    public static void setContents(ItemStack stack, BagContents contents) {
        stack.set(BitsmithComponents.BAG_CONTENTS.get(), contents);
    }

    /**
     * Puts bits into the bag and returns how many it took. The creative bag takes them all: it
     * only remembers the material.
     */
    public int store(ItemStack bag, BlockState material, int count) {
        BagContents contents = contents(bag);
        int room = contents.room(material, count);
        if (room > 0) {
            setContents(bag, contents.add(material, room));
        }
        return room;
    }

    /** Right click with bits on the bag, in an inventory: the bits go in. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack bag, ItemStack other, Slot slot, ClickAction action, Player player,
            SlotAccess access) {
        if (action != ClickAction.SECONDARY || !(other.getItem() instanceof BitItem) || !slot.allowModification(player)) {
            return false;
        }
        int stored = store(bag, BitItem.material(other), other.getCount());
        if (stored > 0) {
            other.shrink(stored);
            player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
        }
        return true;
    }

    /** Right click with the bag on a stack of bits, in an inventory: the bag takes them. */
    @Override
    public boolean overrideStackedOnOther(ItemStack bag, Slot slot, ClickAction action, Player player) {
        ItemStack there = slot.getItem();
        if (action != ClickAction.SECONDARY || !(there.getItem() instanceof BitItem) || !slot.allowModification(player)) {
            return false;
        }
        int stored = store(bag, BitItem.material(there), there.getCount());
        if (stored > 0) {
            there.shrink(stored);
            slot.setChanged();
            player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
        }
        return true;
    }

    /** The action bar message when a material is selected. */
    public Component describeSelection(BagContents.Entry entry) {
        return Component.translatable("bitsmith.bag.selected", entry.material().getBlock().getName(), entry.count());
    }

    /** How the radial menu labels one material of the bag. */
    public Component describeEntry(BagContents.Entry entry) {
        return Component.translatable("bitsmith.bag.entry", entry.material().getBlock().getName(), entry.count());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack bag = context.getItemInHand();
        BagContents.Entry entry = contents(bag).selectedEntry();
        if (entry == null) {
            return InteractionResult.FAIL;
        }
        return BitPlacement.place(context, entry.material(), entry.count(),
                amount -> setContents(bag, contents(bag).remove(entry.material(), amount)));
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
        int shown = Math.min(TOOLTIP_LINES, contents.entries().size());
        for (int i = 0; i < shown; i++) {
            BagContents.Entry entry = contents.entries().get(i);
            Component line = Component.translatable("bitsmith.bag.entry", entry.material().getBlock().getName(), entry.count());
            tooltip.add(line.copy().withStyle(entry == selected ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        }
        if (contents.entries().size() > shown) {
            tooltip.add(Component.translatable("bitsmith.bag.more", contents.entries().size() - shown).withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("bitsmith.bag.keys").withStyle(ChatFormatting.DARK_GRAY));
    }
}
