/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.network;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.Face;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: one chisel stroke at {@code voxel} (a {@link BitGrid#index} value) of the
 * block, through {@code face}. Mode and size are read from the held chisel on the server.
 */
public record ChiselPayload(BlockPos pos, int voxel, Face face) implements CustomPacketPayload {

    public static final Type<ChiselPayload> TYPE = new Type<>(Bitsmith.id("chisel"));
    private static final Face[] FACES = Face.values();

    public static final StreamCodec<RegistryFriendlyByteBuf, ChiselPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos);
                buf.writeShort(p.voxel);
                buf.writeByte(p.face.ordinal());
            },
            buf -> new ChiselPayload(
                    buf.readBlockPos(),
                    buf.readUnsignedShort() & (BitGrid.VOLUME - 1),
                    FACES[Math.floorMod(buf.readByte(), FACES.length)]));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
