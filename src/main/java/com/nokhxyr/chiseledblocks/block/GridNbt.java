/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.block;

import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    public static CompoundTag write(PixelGrid grid) {
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

    public static PixelGrid read(CompoundTag tag, HolderLookup.Provider registries) {
        HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        ListTag list = tag.getList("palette", Tag.TAG_COMPOUND);
        int[] palette = new int[list.size()];
        for (int i = 0; i < palette.length; i++) {
            // Unknown blocks (a removed mod) read back as air, so the rest of the carving survives.
            palette[i] = Materials.id(NbtUtils.readBlockState(blocks, list.getCompound(i)));
        }
        if (palette.length == 0) {
            return PixelGrid.filled(PixelGrid.AIR);
        }
        Saved saved = new Saved(palette, tag.getInt("bits"), tag.getLongArray("data"));
        PixelGrid grid = DECODED.get(saved);
        if (grid == null) {
            grid = ChiseledBlockEntity.INTERNER.intern(PixelGrid.of(PixelGrid.decode(palette, saved.bits, saved.data)));
            if (DECODED.size() >= DECODED_LIMIT) {
                DECODED.clear();
            }
            DECODED.put(saved, grid);
        }
        return grid;
    }

    /**
     * Grids already read, by their saved form. Builds repeat the same carvings (clones, patterns,
     * copied walls), and a chunk arriving on the client is read on the render thread: each saved
     * grid is decoded once. Keys hold runtime ids, like the grids, so the map stays right when ids
     * change; it is emptied when full rather than evicting one by one.
     */
    private static final Map<Saved, PixelGrid> DECODED = new ConcurrentHashMap<>();
    private static final int DECODED_LIMIT = 8192;

    private static final class Saved {
        private final int[] palette;
        private final int bits;
        private final long[] data;
        private final int hash;

        Saved(int[] palette, int bits, long[] data) {
            this.palette = palette;
            this.bits = bits;
            this.data = data;
            this.hash = 31 * (31 * Arrays.hashCode(palette) + bits) + Arrays.hashCode(data);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Saved saved && saved.hash == hash && saved.bits == bits
                    && Arrays.equals(saved.palette, palette) && Arrays.equals(saved.data, data);
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }
}
