/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.block;

import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;

/**
 * Disk format of a grid. Runtime state ids change between game sessions and mod sets, so the
 * palette is stored as block states and remapped on load.
 *
 * <pre>
 * { v: 1, palette: [blockstate...], bits: int, data: long[] }
 * </pre>
 */
public final class GridNbt {
    private static final int VERSION = 1;

    private GridNbt() {
    }

    public static CompoundTag write(BitGrid grid) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("v", VERSION);
        ListTag palette = new ListTag();
        for (int i = 0; i < grid.paletteSize(); i++) {
            palette.add(NbtUtils.writeBlockState(Materials.state(grid.paletteEntry(i))));
        }
        tag.put("palette", palette);
        tag.putInt("bits", grid.bitsPerEntry());
        tag.putLongArray("data", grid.packedData());
        return tag;
    }

    public static BitGrid read(CompoundTag tag, HolderLookup.Provider registries) {
        HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        ListTag list = tag.getList("palette", Tag.TAG_COMPOUND);
        int[] palette = new int[list.size()];
        for (int i = 0; i < palette.length; i++) {
            // Unknown blocks (a removed mod) read back as air, so the rest of the carving survives.
            palette[i] = Materials.id(NbtUtils.readBlockState(blocks, list.getCompound(i)));
        }
        if (palette.length == 0) {
            return BitGrid.filled(BitGrid.AIR);
        }
        return BitGrid.of(BitGrid.decode(palette, tag.getInt("bits"), tag.getLongArray("data")));
    }
}
