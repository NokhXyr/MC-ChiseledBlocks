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

/** Client to server: remove the bit at {@code voxel} (a {@link BitGrid#index} value) of the block. */
public record ChiselPayload(BlockPos pos, int voxel) implements CustomPacketPayload {

    public static final Type<ChiselPayload> TYPE = new Type<>(Bitsmith.id("chisel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ChiselPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos);
                buf.writeShort(p.voxel);
            },
            buf -> new ChiselPayload(buf.readBlockPos(), buf.readUnsignedShort() & (BitGrid.VOLUME - 1)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
