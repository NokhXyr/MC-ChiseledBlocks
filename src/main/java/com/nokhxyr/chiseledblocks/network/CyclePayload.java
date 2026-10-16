/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.network;

import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the player pressed a Chiseled Blocks key while holding a tool. {@code SIZE} cycles
 * the chisel size; {@code MODE} cycles the chisel mode, or the selected material of a bag.
 */
public record CyclePayload(boolean size) implements CustomPacketPayload {

    public static final Type<CyclePayload> TYPE = new Type<>(ChiseledBlocks.id("cycle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CyclePayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBoolean(p.size),
            buf -> new CyclePayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
