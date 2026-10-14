/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.network;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: a whole grid, in its packed form, with runtime state ids as palette. */
public record GridFullPayload(BlockPos pos, int[] palette, int bits, long[] data) implements CustomPacketPayload {

    public static final Type<GridFullPayload> TYPE = new Type<>(Bitsmith.id("grid_full"));
    private static final int MAX_PALETTE = BitGrid.VOLUME;
    private static final int MAX_WORDS = BitGrid.VOLUME;

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

    public static GridFullPayload of(BlockPos pos, BitGrid grid) {
        return new GridFullPayload(pos, grid.palette(), grid.bitsPerEntry(), grid.packedData());
    }

    public BitGrid toGrid() {
        if (palette.length == 0) {
            return BitGrid.filled(BitGrid.AIR);
        }
        return BitGrid.of(BitGrid.decode(palette, bits, data));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
