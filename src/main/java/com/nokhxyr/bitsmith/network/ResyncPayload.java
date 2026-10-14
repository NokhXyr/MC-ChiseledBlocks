/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.network;

import com.nokhxyr.bitsmith.Bitsmith;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the client's copy of a carved block no longer matches; send the whole grid. */
public record ResyncPayload(BlockPos pos) implements CustomPacketPayload {

    public static final Type<ResyncPayload> TYPE = new Type<>(Bitsmith.id("resync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ResyncPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBlockPos(p.pos),
            buf -> new ResyncPayload(buf.readBlockPos()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
