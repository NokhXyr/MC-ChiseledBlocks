/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Codecs of a grid stored on an item: block states on disk, runtime ids on the wire. */
public final class GridCodecs {
    private static final int MAX_PALETTE = PixelGrid.VOLUME;
    private static final int MAX_WORDS = PixelGrid.VOLUME;

    public static final Codec<PixelGrid> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockState.CODEC.listOf().fieldOf("palette").forGetter(GridCodecs::paletteStates),
            Codec.INT.fieldOf("bits").forGetter(PixelGrid::bitsPerEntry),
            Codec.LONG_STREAM.fieldOf("data").forGetter(grid -> Arrays.stream(grid.packedData()))
    ).apply(instance, (palette, bits, data) -> decode(
            palette.stream().mapToInt(Materials::id).toArray(), bits, data.toArray())));

    public static final StreamCodec<RegistryFriendlyByteBuf, PixelGrid> STREAM_CODEC = StreamCodec.of(
            (buf, grid) -> {
                buf.writeVarIntArray(grid.palette());
                buf.writeByte(grid.bitsPerEntry());
                buf.writeLongArray(grid.packedData());
            },
            buf -> decode(buf.readVarIntArray(MAX_PALETTE), buf.readUnsignedByte(), buf.readLongArray(null, MAX_WORDS)));

    private GridCodecs() {
    }

    private static List<BlockState> paletteStates(PixelGrid grid) {
        List<BlockState> states = new ArrayList<>(grid.paletteSize());
        for (int i = 0; i < grid.paletteSize(); i++) {
            states.add(Materials.state(grid.paletteEntry(i)));
        }
        return states;
    }

    private static PixelGrid decode(int[] palette, int bits, long[] data) {
        PixelGrid grid = palette.length == 0 ? PixelGrid.filled(PixelGrid.AIR) : PixelGrid.of(PixelGrid.decode(palette, bits, data));
        return ChiseledBlockEntity.INTERNER.intern(grid);
    }
}
