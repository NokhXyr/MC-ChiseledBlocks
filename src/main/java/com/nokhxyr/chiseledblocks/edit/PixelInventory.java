/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.edit;

import com.nokhxyr.chiseledblocks.block.Materials;
import com.nokhxyr.chiseledblocks.item.BagContents;
import com.nokhxyr.chiseledblocks.item.PixelBagItem;
import com.nokhxyr.chiseledblocks.item.PixelItem;
import com.nokhxyr.chiseledblocks.item.CreativePixelBagItem;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Hands chiseled bits to a player: bit bags first, then the inventory, then the ground. A
 * creative bit bag anywhere in the inventory swallows them instead, and pays for anything.
 */
public final class PixelInventory {
    private static final int STACK = 64;

    private PixelInventory() {
    }

    public static void give(Player player, Int2IntMap bitsByMaterial) {
        for (Int2IntMap.Entry entry : bitsByMaterial.int2IntEntrySet()) {
            give(player, Materials.state(entry.getIntKey()), entry.getIntValue());
        }
    }

    public static void give(Player player, BlockState material, int count) {
        ItemStack creative = creativeBag(player.getInventory());
        if (creative != null) {
            // Nothing to carry: the material is only remembered so it can be picked later.
            CreativePixelBagItem.remember(creative, material);
            return;
        }
        int left = intoBags(player.getInventory(), material, count);
        while (left > 0) {
            int amount = Math.min(STACK, left);
            left -= amount;
            ItemStack bits = PixelItem.of(material, amount);
            if (!player.getInventory().add(bits) && !bits.isEmpty()) {
                player.drop(bits, false);
            }
        }
    }

    /** Bits of a material the player holds, loose and in bags; without limit with a creative bag. */
    public static int count(Player player, BlockState material) {
        Inventory inventory = player.getInventory();
        if (creativeBag(inventory) != null) {
            return CreativePixelBagItem.INFINITE;
        }
        int total = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof PixelItem && PixelItem.material(stack) == material) {
                total += stack.getCount();
            } else if (stack.getItem() instanceof PixelBagItem) {
                total += PixelBagItem.contents(stack).count(material);
            }
        }
        return total;
    }

    /**
     * Removes up to {@code count} bits, loose stacks first, then bags. Returns how many were taken.
     * A creative bag pays for everything without anything being removed.
     */
    public static int take(Player player, BlockState material, int count) {
        Inventory inventory = player.getInventory();
        if (creativeBag(inventory) != null) {
            return count;
        }
        int left = count;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof PixelItem && PixelItem.material(stack) == material) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof PixelBagItem) {
                BagContents contents = PixelBagItem.contents(stack);
                int taken = Math.min(left, contents.count(material));
                if (taken > 0) {
                    PixelBagItem.setContents(stack, contents.remove(material, taken));
                    left -= taken;
                }
            }
        }
        return count - left;
    }

    /** The first creative bit bag in the inventory, if any. */
    public static @Nullable ItemStack creativeBag(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof CreativePixelBagItem) {
                return stack;
            }
        }
        return null;
    }

    /**
     * Bits picked up from the ground go into the player's bags first, like chiseled ones. A
     * creative bag does not swallow them: it is meant for building, not for clearing the floor.
     */
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        Player player = event.getPlayer();
        ItemEntity entity = event.getItemEntity();
        ItemStack stack = entity.getItem();
        if (player.level().isClientSide || !(stack.getItem() instanceof PixelItem) || entity.hasPickUpDelay()
                || creativeBag(player.getInventory()) != null) {
            return;
        }
        int count = stack.getCount();
        int left = intoBags(player.getInventory(), PixelItem.material(stack), count);
        if (left == count) {
            return;
        }
        if (left == 0) {
            player.take(entity, count);
            entity.discard();
            event.setCanPickup(TriState.FALSE);
        } else {
            stack.setCount(left);
        }
    }

    private static int intoBags(Inventory inventory, BlockState material, int count) {
        int left = count;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof PixelBagItem) {
                BagContents contents = PixelBagItem.contents(stack);
                int room = contents.room(material, left);
                if (room > 0) {
                    PixelBagItem.setContents(stack, contents.add(material, room));
                    left -= room;
                }
            }
        }
        return left;
    }
}
