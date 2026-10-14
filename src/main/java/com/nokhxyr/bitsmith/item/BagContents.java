/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Immutable contents of a bit bag: up to {@link #MAX_TYPES} materials, each up to
 * {@link #MAX_PER_TYPE} bits, plus the material selected for placing.
 */
public record BagContents(List<Entry> entries, int selected) {
    public static final int MAX_TYPES = 16;
    /** Eight full blocks of one material. */
    public static final int MAX_PER_TYPE = 8 * 4096;
    public static final BagContents EMPTY = new BagContents(List.of(), 0);

    public record Entry(BlockState material, int count) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockState.CODEC.fieldOf("material").forGetter(Entry::material),
                Codec.intRange(1, MAX_PER_TYPE).fieldOf("count").forGetter(Entry::count)
        ).apply(instance, Entry::new));

        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY), Entry::material,
                ByteBufCodecs.VAR_INT, Entry::count,
                Entry::new);
    }

    public static final Codec<BagContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Entry.CODEC.listOf(0, MAX_TYPES).fieldOf("entries").forGetter(BagContents::entries),
            Codec.INT.optionalFieldOf("selected", 0).forGetter(BagContents::selected)
    ).apply(instance, BagContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BagContents> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TYPES)), BagContents::entries,
            ByteBufCodecs.VAR_INT, BagContents::selected,
            BagContents::new);

    public BagContents {
        entries = List.copyOf(entries);
        selected = entries.isEmpty() ? 0 : Math.floorMod(selected, entries.size());
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public Entry selectedEntry() {
        return entries.isEmpty() ? null : entries.get(selected);
    }

    public int count(BlockState material) {
        for (Entry entry : entries) {
            if (entry.material == material) {
                return entry.count;
            }
        }
        return 0;
    }

    /** How many of {@code count} bits of {@code material} this bag can still take. */
    public int room(BlockState material, int count) {
        int existing = count(material);
        if (existing == 0 && entries.size() >= MAX_TYPES) {
            return 0;
        }
        return Math.min(count, MAX_PER_TYPE - existing);
    }

    /** Adds bits; the caller checks {@link #room} first. */
    public BagContents add(BlockState material, int count) {
        if (count <= 0) {
            return this;
        }
        List<Entry> copy = new ArrayList<>(entries);
        for (int i = 0; i < copy.size(); i++) {
            if (copy.get(i).material == material) {
                copy.set(i, new Entry(material, copy.get(i).count + count));
                return new BagContents(copy, selected);
            }
        }
        copy.add(new Entry(material, count));
        return new BagContents(copy, selected);
    }

    /** Removes up to {@code count} bits; entries reaching zero disappear. */
    public BagContents remove(BlockState material, int count) {
        List<Entry> copy = new ArrayList<>(entries);
        for (int i = 0; i < copy.size(); i++) {
            Entry entry = copy.get(i);
            if (entry.material == material) {
                int left = entry.count - count;
                int newSelected = selected;
                if (left > 0) {
                    copy.set(i, new Entry(material, left));
                } else {
                    copy.remove(i);
                    if (i < selected) {
                        newSelected--;
                    }
                }
                return new BagContents(copy, newSelected);
            }
        }
        return this;
    }

    public BagContents selectNext() {
        return new BagContents(entries, selected + 1);
    }
}
