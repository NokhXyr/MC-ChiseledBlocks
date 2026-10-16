/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.item;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.nokhxyr.chiseledblocks.block.Materials;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * What a carving is made of: each material with its number of bits, most used first. Item names
 * and tooltips ask for it every frame, so it is computed once per grid and cached.
 */
public record GridSummary(List<Entry> materials, int filled) {
    private static final Cache<PixelGrid, GridSummary> CACHE = CacheBuilder.newBuilder()
            .weakKeys()
            .maximumSize(4096)
            .build();

    public record Entry(BlockState material, int bits) {
    }

    public static GridSummary of(PixelGrid grid) {
        try {
            return CACHE.get(grid, () -> compute(grid));
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to summarise a carving", e.getCause());
        }
    }

    private static GridSummary compute(PixelGrid grid) {
        int[] counts = new int[grid.paletteSize()];
        int[] palette = grid.palette();
        for (int i = 0; i < PixelGrid.VOLUME; i++) {
            int material = grid.get(i);
            for (int p = 0; p < palette.length; p++) {
                if (palette[p] == material) {
                    counts[p]++;
                    break;
                }
            }
        }
        List<Entry> entries = new ArrayList<>();
        int filled = 0;
        for (int p = 0; p < palette.length; p++) {
            if (palette[p] != PixelGrid.AIR && counts[p] > 0) {
                entries.add(new Entry(Materials.state(palette[p]), counts[p]));
                filled += counts[p];
            }
        }
        entries.sort(Comparator.comparingInt(Entry::bits).reversed());
        return new GridSummary(List.copyOf(entries), filled);
    }

    public boolean isEmpty() {
        return materials.isEmpty();
    }
}
