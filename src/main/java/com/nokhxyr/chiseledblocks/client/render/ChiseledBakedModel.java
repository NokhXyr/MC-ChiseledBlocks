/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client.render;

import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
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
public final class ChiseledBakedModel implements IDynamicBakedModel {
    private final BakedModel fallback;

    public ChiseledBakedModel(BakedModel fallback) {
        this.fallback = fallback;
    }

    private @Nullable ChiseledMesh mesh(ModelData data) {
        PixelGrid grid = data.get(ChiseledBlockEntity.GRID);
        return grid == null || grid.isEmpty() ? null : MeshCache.get(grid, fallback.getParticleIcon());
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData data, @Nullable RenderType renderType) {
        ChiseledMesh mesh = mesh(data);
        return mesh == null ? List.of() : mesh.quads(renderType, side);
    }

    /**
     * Normally the grid arrives through NeoForge's model data manager. Some mods draw blocks
     * outside that pipeline (Create contraptions, schematic previews) but still keep the block
     * entity in their own level: read the grid from it then.
     */
    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        if (modelData.get(ChiseledBlockEntity.GRID) != null) {
            return modelData;
        }
        if (level.getBlockEntity(pos) instanceof ChiseledBlockEntity carved) {
            return carved.getModelData();
        }
        return modelData;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        ChiseledMesh mesh = mesh(data);
        return mesh == null ? ChunkRenderTypeSet.none() : mesh.renderTypes();
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        ChiseledMesh mesh = mesh(data);
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
