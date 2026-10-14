/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client.render;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ExecutionException;
import java.util.function.Function;

/**
 * Picks the item model of a stack from the grid it carries. Models are cached per grid, weakly,
 * so a stack seen every frame never rebuilds anything.
 */
public final class GridItemOverrides extends ItemOverrides {
    private final Function<ItemStack, BitGrid> gridOf;
    private final BakedModel base;
    private final Cache<BitGrid, BakedModel> models = CacheBuilder.newBuilder().weakKeys().maximumSize(2048).build();

    private GridItemOverrides(Function<ItemStack, BitGrid> gridOf, BakedModel base) {
        this.gridOf = gridOf;
        this.base = base;
    }

    /** Wraps a json item model so that it resolves to the grid model of each stack. */
    public static BakedModel wrap(BakedModel json, Function<ItemStack, BitGrid> gridOf) {
        GridItemOverrides overrides = new GridItemOverrides(gridOf, json);
        return new BakedModelWrapper<>(json) {
            @Override
            public ItemOverrides getOverrides() {
                return overrides;
            }
        };
    }

    @Override
    public @Nullable BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                        @Nullable LivingEntity entity, int seed) {
        BitGrid grid = gridOf.apply(stack);
        if (grid == null || grid.isEmpty()) {
            return model;
        }
        try {
            return models.get(grid, () -> new GridItemModel(grid, base));
        } catch (ExecutionException e) {
            return model;
        }
    }
}
