/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.network;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.core.edit.Symmetry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player's mirror setting, sent when it changes and on joining. */
public record SymmetryPayload(Symmetry symmetry) implements CustomPacketPayload {

    public static final Type<SymmetryPayload> TYPE = new Type<>(Bitsmith.id("symmetry"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SymmetryPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeByte(p.symmetry.ordinal()),
            buf -> new SymmetryPayload(Symmetry.byOrdinal(buf.readByte())));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
