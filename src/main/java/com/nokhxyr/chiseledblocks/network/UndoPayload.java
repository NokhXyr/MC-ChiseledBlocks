/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.network;

import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: undo (or redo) the player's last carving action. */
public record UndoPayload(boolean redo) implements CustomPacketPayload {

    public static final Type<UndoPayload> TYPE = new Type<>(ChiseledBlocks.id("undo"));

    public static final StreamCodec<RegistryFriendlyByteBuf, UndoPayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBoolean(p.redo),
            buf -> new UndoPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
