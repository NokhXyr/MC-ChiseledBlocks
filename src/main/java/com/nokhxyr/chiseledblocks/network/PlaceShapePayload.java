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
 * Client to server: place the held material on a shape, from {@code start} to {@code end} in
 * world bit coordinates. {@code axis} is the axis of the face clicked first (a plane stays on it).
 */
public record PlaceShapePayload(int mode, int startX, int startY, int startZ, int endX, int endY, int endZ, int axis)
        implements CustomPacketPayload {

    public static final Type<PlaceShapePayload> TYPE = new Type<>(ChiseledBlocks.id("place_shape"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaceShapePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.mode);
                buf.writeVarInt(p.startX);
                buf.writeVarInt(p.startY);
                buf.writeVarInt(p.startZ);
                buf.writeVarInt(p.endX);
                buf.writeVarInt(p.endY);
                buf.writeVarInt(p.endZ);
                buf.writeVarInt(p.axis);
            },
            buf -> new PlaceShapePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

    public int[] start() {
        return new int[]{startX, startY, startZ};
    }

    public int[] end() {
        return new int[]{endX, endY, endZ};
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
