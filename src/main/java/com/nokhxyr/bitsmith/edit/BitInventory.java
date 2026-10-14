/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.edit;

import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.item.BagContents;
import com.nokhxyr.bitsmith.item.BitBagItem;
import com.nokhxyr.bitsmith.item.BitItem;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Hands chiseled bits to a player: bit bags first, then the inventory, then the ground. */
public final class BitInventory {
    private static final int STACK = 64;

    private BitInventory() {
    }

    public static void give(Player player, Int2IntMap bitsByMaterial) {
        for (Int2IntMap.Entry entry : bitsByMaterial.int2IntEntrySet()) {
            give(player, Materials.state(entry.getIntKey()), entry.getIntValue());
        }
    }

    public static void give(Player player, BlockState material, int count) {
        int left = intoBags(player.getInventory(), material, count);
        while (left > 0) {
            int amount = Math.min(STACK, left);
            left -= amount;
            ItemStack bits = BitItem.of(material, amount);
            if (!player.getInventory().add(bits) && !bits.isEmpty()) {
                player.drop(bits, false);
            }
        }
    }

    private static int intoBags(Inventory inventory, BlockState material, int count) {
        int left = count;
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof BitBagItem) {
                BagContents contents = BitBagItem.contents(stack);
                int room = contents.room(material, left);
                if (room > 0) {
                    BitBagItem.setContents(stack, contents.add(material, room));
                    left -= room;
                }
            }
        }
        return left;
    }
}
