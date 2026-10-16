/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** What a pattern does when used on a block. */
public enum PatternMode implements StringRepresentable {
    /** Prints the pattern's bits into the empty space of the target. */
    POSITIVE,
    /** Carves the pattern's shape out of the target. */
    NEGATIVE;

    private static final PatternMode[] VALUES = values();

    public static final Codec<PatternMode> CODEC = StringRepresentable.fromEnum(PatternMode::values);
    public static final StreamCodec<ByteBuf, PatternMode> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(PatternMode::byOrdinal, PatternMode::ordinal);

    public static PatternMode byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : POSITIVE;
    }

    public PatternMode next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public Component displayName() {
        return Component.translatable("chiseledblocks.pattern.mode." + getSerializedName());
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
