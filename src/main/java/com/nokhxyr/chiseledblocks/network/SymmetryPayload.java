/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.network;

import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.core.edit.Symmetry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player's mirror setting, sent when it changes and on joining. */
public record SymmetryPayload(Symmetry symmetry) implements CustomPacketPayload {

    public static final Type<SymmetryPayload> TYPE = new Type<>(ChiseledBlocks.id("symmetry"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SymmetryPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeByte(p.symmetry.ordinal()),
            buf -> new SymmetryPayload(Symmetry.byOrdinal(buf.readByte())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
