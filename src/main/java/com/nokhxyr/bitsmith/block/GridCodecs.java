/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Codecs of a grid stored on an item: block states on disk, runtime ids on the wire. */
public final class GridCodecs {
    private static final int MAX_PALETTE = BitGrid.VOLUME;
    private static final int MAX_WORDS = BitGrid.VOLUME;

    public static final Codec<BitGrid> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockState.CODEC.listOf().fieldOf("palette").forGetter(GridCodecs::paletteStates),
            Codec.INT.fieldOf("bits").forGetter(BitGrid::bitsPerEntry),
            Codec.LONG_STREAM.fieldOf("data").forGetter(grid -> Arrays.stream(grid.packedData()))
    ).apply(instance, (palette, bits, data) -> decode(
            palette.stream().mapToInt(Materials::id).toArray(), bits, data.toArray())));

    public static final StreamCodec<RegistryFriendlyByteBuf, BitGrid> STREAM_CODEC = StreamCodec.of(
            (buf, grid) -> {
                buf.writeVarIntArray(grid.palette());
                buf.writeByte(grid.bitsPerEntry());
                buf.writeLongArray(grid.packedData());
            },
            buf -> decode(buf.readVarIntArray(MAX_PALETTE), buf.readUnsignedByte(), buf.readLongArray(null, MAX_WORDS)));

    private GridCodecs() {
    }

    private static List<BlockState> paletteStates(BitGrid grid) {
        List<BlockState> states = new ArrayList<>(grid.paletteSize());
        for (int i = 0; i < grid.paletteSize(); i++) {
            states.add(Materials.state(grid.paletteEntry(i)));
        }
        return states;
    }

    private static BitGrid decode(int[] palette, int bits, long[] data) {
        BitGrid grid = palette.length == 0 ? BitGrid.filled(BitGrid.AIR) : BitGrid.of(BitGrid.decode(palette, bits, data));
        return CarvedBlockEntity.INTERNER.intern(grid);
    }
}
