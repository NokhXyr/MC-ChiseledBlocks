/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.network;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the voxels of one carved block that changed during one tick.
 * {@code expectedHash} lets the client detect a desync and fall back to a full update.
 */
public record GridDeltaPayload(BlockPos pos, int[] indices, int[] materials, int expectedHash)
        implements CustomPacketPayload {

    public static final Type<GridDeltaPayload> TYPE = new Type<>(Bitsmith.id("grid_delta"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GridDeltaPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBlockPos(p.pos);
                buf.writeVarInt(p.indices.length);
                for (int i = 0; i < p.indices.length; i++) {
                    buf.writeShort(p.indices[i]);
                    buf.writeVarInt(p.materials[i]);
                }
                buf.writeInt(p.expectedHash);
            },
            buf -> {
                BlockPos pos = buf.readBlockPos();
                int count = readCount(buf);
                int[] indices = new int[count];
                int[] materials = new int[count];
                for (int i = 0; i < count; i++) {
                    indices[i] = buf.readUnsignedShort() & (BitGrid.VOLUME - 1);
                    materials[i] = buf.readVarInt();
                }
                return new GridDeltaPayload(pos, indices, materials, buf.readInt());
            });

    private static int readCount(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > BitGrid.VOLUME) {
            throw new IllegalArgumentException("Invalid delta size " + count);
        }
        return count;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
