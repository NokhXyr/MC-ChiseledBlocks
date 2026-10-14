/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.compat.cnb;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import net.jpountz.lz4.LZ4FrameInputStream;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.ByteArrayTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.BitSet;

/**
 * Reads the block entity data Chisels &amp; Bits 1.21.1 saves for a chiseled block, so that worlds
 * can be opened with Bitsmith instead. Written from the saved format, documented in
 * {@code docs/CNB_FORMAT.md}:
 *
 * <ul>
 *   <li>{@code data.payload} (or {@code payload}): an LZ4 frame holding an unnamed NBT compound
 *       {@code {storage, statistics}} (type id, then the compound).</li>
 *   <li>{@code storage.palette.entries}: a list of {@code {state: {state: <block state>}}} (a block
 *       information around the block state).</li>
 *   <li>{@code storage.data}: a bit set (long array, or byte array in older saves); entry {@code i}
 *       is the palette index stored in bits {@code [i*w, i*w+w)}, lowest bit first, with
 *       {@code w = ceil(log2(palette size))}.</li>
 *   <li>Entry index for a voxel: {@code x*256 + y*16 + z}.</li>
 * </ul>
 *
 * The older {@code data} key (an LZ4 frame holding {@code {chiseledData: {palette, data}}}) is read
 * the same way. Anything unreadable gives {@code null}, never an exception.
 */
public final class ChiselsAndBitsImport {
    public static final String MOD_ID = "chiselsandbits";
    /** Largest compressed payload accepted, far above a real block (a few kilobytes). */
    private static final int MAX_COMPRESSED = 1 << 20;
    private static final long MAX_NBT_BYTES = 8L << 20;
    private static final int SIZE = BitGrid.SIZE;

    private ChiselsAndBitsImport() {
    }

    /** Whether a block entity tag looks like one written by Chisels & Bits. */
    public static boolean isChiselsAndBitsData(CompoundTag tag) {
        return tag.contains("payload", Tag.TAG_BYTE_ARRAY)
                || tag.contains("data", Tag.TAG_BYTE_ARRAY)
                || tag.getCompound("data").contains("payload", Tag.TAG_BYTE_ARRAY);
    }

    public static @Nullable BitGrid read(CompoundTag tag, HolderLookup.Provider registries) {
        try {
            CompoundTag storage;
            ListTag entries;
            // Chisels & Bits 1.21.1 wraps the payload in its storage engine's "data" compound.
            CompoundTag holder = tag.contains("data", Tag.TAG_COMPOUND) ? tag.getCompound("data") : tag;
            if (holder.contains("payload", Tag.TAG_BYTE_ARRAY)) {
                storage = decompress(holder.getByteArray("payload")).getCompound("storage");
                entries = storage.getCompound("palette").getList("entries", Tag.TAG_COMPOUND);
            } else if (tag.contains("data", Tag.TAG_BYTE_ARRAY)) {
                storage = decompress(tag.getByteArray("data")).getCompound("chiseledData");
                entries = storage.getList("palette", Tag.TAG_COMPOUND);
            } else {
                return null;
            }
            if (entries.isEmpty()) {
                return null;
            }
            return decode(storage.get("data"), palette(entries, registries));
        } catch (IOException | RuntimeException e) {
            Bitsmith.LOGGER.warn("Could not read a Chisels & Bits block, it will be empty", e);
            return null;
        }
    }

    /** The decompressed data of a Chisels & Bits block as text, for diagnostics. */
    public static String describe(CompoundTag tag) {
        try {
            CompoundTag holder = tag.contains("data", Tag.TAG_COMPOUND) ? tag.getCompound("data") : tag;
            return decompress(holder.getByteArray("payload")).toString();
        } catch (IOException | RuntimeException e) {
            return "unreadable: " + e;
        }
    }

    private static CompoundTag decompress(byte[] bytes) throws IOException {
        if (bytes.length > MAX_COMPRESSED) {
            throw new IOException("Chisels & Bits payload too large: " + bytes.length + " bytes");
        }
        try (DataInputStream in = new DataInputStream(new LZ4FrameInputStream(new ByteArrayInputStream(bytes)))) {
            // The root is written without a name: type id, then the compound.
            if (NbtIo.readAnyTag(in, NbtAccounter.create(MAX_NBT_BYTES)) instanceof CompoundTag compound) {
                return compound;
            }
            throw new IOException("Chisels & Bits payload is not a compound");
        }
    }

    /** Palette entries as Bitsmith material ids. Unknown or unusable states become air. */
    private static int[] palette(ListTag entries, HolderLookup.Provider registries) {
        HolderGetter<Block> blocks = registries.lookupOrThrow(Registries.BLOCK);
        int[] palette = new int[entries.size()];
        for (int i = 0; i < palette.length; i++) {
            // An entry holds a block information, which holds the block state: {state:{state:{Name:...}}}.
            CompoundTag stateTag = entries.getCompound(i);
            while (!stateTag.contains("Name", Tag.TAG_STRING) && stateTag.contains("state", Tag.TAG_COMPOUND)) {
                stateTag = stateTag.getCompound("state");
            }
            BlockState state = NbtUtils.readBlockState(blocks, stateTag);
            palette[i] = state.isAir() || !Materials.isValidMaterial(state) ? BitGrid.AIR : Materials.id(state);
        }
        return palette;
    }

    private static BitGrid decode(@Nullable Tag data, int[] palette) {
        BitSet bits;
        if (data instanceof LongArrayTag longs) {
            bits = BitSet.valueOf(longs.getAsLongArray());
        } else if (data instanceof ByteArrayTag bytes) {
            bits = BitSet.valueOf(bytes.getAsByteArray());
        } else {
            bits = new BitSet();
        }
        int width = palette.length <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(palette.length - 1);

        int[] voxels = new int[BitGrid.VOLUME];
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    int offset = (x * SIZE * SIZE + y * SIZE + z) * width;
                    int paletteIndex = 0;
                    for (int b = 0; b < width; b++) {
                        if (bits.get(offset + b)) {
                            paletteIndex |= 1 << b;
                        }
                    }
                    voxels[BitGrid.index(x, y, z)] = paletteIndex < palette.length ? palette[paletteIndex] : BitGrid.AIR;
                }
            }
        }
        return BitGrid.of(voxels);
    }
}
