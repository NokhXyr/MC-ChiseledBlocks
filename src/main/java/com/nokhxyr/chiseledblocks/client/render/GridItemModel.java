/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client.render;

import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Item model of one grid, built from the same cached mesh as the block. Rendered in one pass per
 * render layer so that cutout and translucent bits keep their look in hand and in slots.
 */
final class GridItemModel implements BakedModel {
    private final ChiseledMesh mesh;
    private final BakedModel base;
    private final List<BakedModel> passes;

    GridItemModel(PixelGrid grid, BakedModel base) {
        this.mesh = MeshCache.get(grid, base.getParticleIcon());
        this.base = base;
        List<BakedModel> layers = new ArrayList<>();
        for (RenderType renderType : mesh.renderTypes()) {
            layers.add(new Layer(renderType));
        }
        this.passes = List.copyOf(layers);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
        return mesh.quads(null, side);
    }

    @Override
    public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        return passes;
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
        return mesh.particle();
    }

    @Override
    public ItemTransforms getTransforms() {
        return base.getTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    /** The quads of one render layer, drawn with the matching entity render type. */
    private final class Layer implements BakedModel {
        private final RenderType chunkType;
        private final List<RenderType> itemTypes;

        private Layer(RenderType chunkType) {
            this.chunkType = chunkType;
            this.itemTypes = List.of(RenderTypeHelper.getEntityRenderType(chunkType, true));
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
            return mesh.quads(chunkType, side);
        }

        @Override
        public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return itemTypes;
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
            return mesh.particle();
        }

        @Override
        public ItemTransforms getTransforms() {
            return base.getTransforms();
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }
}
