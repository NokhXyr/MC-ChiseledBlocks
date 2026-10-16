/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.create;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What a bit mill holds: the blocks waiting to be cut, and the bits already cut, of one material.
 * Kept on the item when the mill is broken, so nothing spills.
 */
public record MillContents(ItemStack input, BlockState material, int bits) {
    public static final MillContents EMPTY = new MillContents(ItemStack.EMPTY, Blocks.AIR.defaultBlockState(), 0);

    public static final Codec<MillContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("input", ItemStack.EMPTY).forGetter(MillContents::input),
            BlockState.CODEC.optionalFieldOf("material", Blocks.AIR.defaultBlockState()).forGetter(MillContents::material),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("bits", 0).forGetter(MillContents::bits)
    ).apply(instance, MillContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, MillContents> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, MillContents::input,
            ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), MillContents::material,
            ByteBufCodecs.VAR_INT, MillContents::bits,
            MillContents::new);

    public boolean isEmpty() {
        return input.isEmpty() && bits == 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MillContents that && bits == that.bits && material == that.material
                && ItemStack.matches(input, that.input);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * ItemStack.hashItemAndComponents(input) + input.getCount()) + material.hashCode() + bits;
    }
}
