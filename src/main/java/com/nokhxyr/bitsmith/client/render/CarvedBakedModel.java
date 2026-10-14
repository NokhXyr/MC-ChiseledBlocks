/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client.render;

import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Model of the carved block. Reads the grid from the model data captured for the section being
 * compiled and serves the cached mesh. Nothing here runs per frame.
 */
public final class CarvedBakedModel implements IDynamicBakedModel {
    private final BakedModel fallback;

    public CarvedBakedModel(BakedModel fallback) {
        this.fallback = fallback;
    }

    private @Nullable CarvedMesh mesh(ModelData data) {
        BitGrid grid = data.get(CarvedBlockEntity.GRID);
        return grid == null || grid.isEmpty() ? null : MeshCache.get(grid, fallback.getParticleIcon());
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        CarvedMesh mesh = mesh(data);
        return mesh == null ? List.of() : mesh.quads(renderType, side);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        CarvedMesh mesh = mesh(data);
        return mesh == null ? ChunkRenderTypeSet.none() : mesh.renderTypes();
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        CarvedMesh mesh = mesh(data);
        return mesh == null ? fallback.getParticleIcon() : mesh.particle();
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return fallback.getParticleIcon();
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
