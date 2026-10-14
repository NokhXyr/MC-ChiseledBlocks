/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.network;

import com.nokhxyr.bitsmith.Bitsmith;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: an option picked in the radial menu for the tool in the main hand. */
public record ToolOptionPayload(Option option, int value) implements CustomPacketPayload {

    public enum Option {
        CHISEL_MODE,
        CHISEL_SIZE,
        BAG_MATERIAL,
        PATTERN_MODE;

        private static final Option[] VALUES = values();
    }

    public static final Type<ToolOptionPayload> TYPE = new Type<>(Bitsmith.id("tool_option"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToolOptionPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.option.ordinal());
                buf.writeVarInt(p.value);
            },
            buf -> new ToolOptionPayload(
                    Option.VALUES[Math.floorMod(buf.readByte(), Option.VALUES.length)],
                    buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
