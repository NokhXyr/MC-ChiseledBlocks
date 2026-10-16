/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.network;

import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: a whole grid, in its packed form, with runtime state ids as palette. */
public record GridFullPayload(BlockPos pos, int[] palette, int bits, long[] data) implements CustomPacketPayload {

    public static final Type<GridFullPayload> TYPE = new Type<>(ChiseledBlocks.id("grid_full"));
    private static final int MAX_PALETTE = PixelGrid.VOLUME;
    private static final int MAX_WORDS = PixelGrid.VOLUME;

    public static final StreamCodec<RegistryFriendlyByteBuf, GridFullPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos);
                buf.writeVarIntArray(p.palette);
                buf.writeByte(p.bits);
                buf.writeLongArray(p.data);
            },
            buf -> new GridFullPayload(
                    buf.readBlockPos(),
                    buf.readVarIntArray(MAX_PALETTE),
                    buf.readUnsignedByte(),
                    buf.readLongArray(null, MAX_WORDS)));

    public static GridFullPayload of(BlockPos pos, PixelGrid grid) {
        return new GridFullPayload(pos, grid.palette(), grid.bitsPerEntry(), grid.packedData());
    }

    public PixelGrid toGrid() {
        if (palette.length == 0) {
            return PixelGrid.filled(PixelGrid.AIR);
        }
        return PixelGrid.of(PixelGrid.decode(palette, bits, data));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
