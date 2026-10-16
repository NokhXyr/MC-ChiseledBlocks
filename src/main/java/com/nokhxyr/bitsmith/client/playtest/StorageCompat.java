/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client.playtest;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.StorageCell;
import appeng.core.definitions.AEItems;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.StorageImpl;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.refinedmods.refinedstorage.common.support.resource.ResourceCodecs;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Puts Bitsmith items through the storage of AE2 and Refined Storage and back, the way their
 * networks keep items: an AE2 storage cell saved to its item, Refined Storage's resource saved
 * with its own codec. The item must come back identical, carving and contents included. Only
 * called when those mods are loaded.
 */
final class StorageCompat {
    private StorageCompat() {
    }

    /** Stores, saves, reopens and extracts; returns null when the stack came back identical. */
    static String throughAe2(ItemStack original) {
        ItemStack cell = AEItems.ITEM_CELL_1K.stack();
        StorageCell storage = StorageCells.getCellInventory(cell, null);
        if (storage == null) {
            return "no AE2 cell inventory";
        }
        AEItemKey key = AEItemKey.of(original);
        long stored = storage.insert(key, original.getCount(), Actionable.MODULATE, IActionSource.empty());
        storage.persist();
        // Reopened from the cell item, as after a save: what AE2 wrote is all there is.
        StorageCell reopened = StorageCells.getCellInventory(cell, null);
        long kept = reopened.getAvailableStacks().get(key);
        long taken = reopened.extract(key, original.getCount(), Actionable.MODULATE, IActionSource.empty());
        ItemStack back = key.toStack((int) taken);
        if (stored != original.getCount() || kept != original.getCount()) {
            return "AE2 stored " + stored + ", kept " + kept + " of " + original.getCount();
        }
        return ItemStack.matches(back, original) ? null : "AE2 gave back " + back.getComponentsPatch();
    }

    /** The same through Refined Storage's storage and its save codec. */
    static String throughRefinedStorage(ItemStack original, HolderLookup.Provider registries) {
        StorageImpl storage = new StorageImpl();
        ItemResource resource = ItemResource.ofItemStack(original);
        long stored = storage.insert(resource, original.getCount(), Action.EXECUTE, Actor.EMPTY);
        ResourceAmount amount = storage.getAll().iterator().next();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        Tag saved = ResourceCodecs.AMOUNT_CODEC.encodeStart(ops, amount).getOrThrow();
        ResourceAmount loaded = ResourceCodecs.AMOUNT_CODEC.parse(ops, saved).getOrThrow();
        if (stored != original.getCount() || loaded.amount() != original.getCount()) {
            return "Refined Storage stored " + stored + ", loaded " + loaded.amount();
        }
        ItemStack back = ((ItemResource) loaded.resource()).toItemStack(loaded.amount());
        return ItemStack.matches(back, original) ? null : "Refined Storage gave back " + back.getComponentsPatch();
    }
}
