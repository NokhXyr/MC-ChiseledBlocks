/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.nokhxyr.bitsmith.core.edit.ChiselMode;
import com.nokhxyr.bitsmith.core.edit.ChiselShape;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.Locale;

/** Mode and size of a chisel, stored on the item. */
public record ChiselSettings(ChiselMode mode, int size) {
    public static final ChiselSettings DEFAULT = new ChiselSettings(ChiselMode.BIT, 1);

    private static final Codec<ChiselMode> MODE_CODEC = Codec.STRING.comapFlatMap(
            name -> {
                try {
                    return DataResult.success(ChiselMode.valueOf(name.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown chisel mode " + name);
                }
            },
            mode -> mode.name().toLowerCase(Locale.ROOT));

    public static final Codec<ChiselSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MODE_CODEC.optionalFieldOf("mode", ChiselMode.BIT).forGetter(ChiselSettings::mode),
            Codec.INT.optionalFieldOf("size", 1).forGetter(ChiselSettings::size)
    ).apply(instance, ChiselSettings::new));

    public static final StreamCodec<ByteBuf, ChiselSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(ChiselMode::byOrdinal, ChiselMode::ordinal), ChiselSettings::mode,
            ByteBufCodecs.VAR_INT, ChiselSettings::size,
            ChiselSettings::new);

    public ChiselSettings {
        size = ChiselShape.validSize(size);
    }

    public ChiselSettings withNextMode() {
        return new ChiselSettings(mode.next(), size);
    }

    public ChiselSettings withNextSize() {
        return new ChiselSettings(mode, ChiselShape.nextSize(size));
    }

    public Component describe() {
        Component modeName = Component.translatable("bitsmith.mode." + mode.name().toLowerCase(Locale.ROOT));
        if (mode == ChiselMode.BIT) {
            return Component.translatable("bitsmith.chisel.settings.bit", modeName);
        }
        return Component.translatable("bitsmith.chisel.settings", modeName, size);
    }
}
