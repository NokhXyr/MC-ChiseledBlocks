/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.network;

import com.nokhxyr.bitsmith.Bitsmith;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player pressed a Bitsmith key while holding a tool. {@code SIZE} cycles
 * the chisel size; {@code MODE} cycles the chisel mode, or the selected material of a bag.
 */
public record CyclePayload(boolean size) implements CustomPacketPayload {

    public static final Type<CyclePayload> TYPE = new Type<>(Bitsmith.id("cycle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CyclePayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBoolean(p.size),
            buf -> new CyclePayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
