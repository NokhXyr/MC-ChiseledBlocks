/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client.render;

import com.nokhxyr.bitsmith.block.Materials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How one material looks: the texture and tint of each face, and its render layer, read once
 * from the material's own block model.
 *
 * <p>The texture variant is fixed (seed 42) instead of following the position, so that every
 * carving of a material shares the same mesh. Only the first quad of each face is used: overlay
 * layers (grass side) are not reproduced yet.
 */
public final class MaterialAppearance {
    private static final ConcurrentHashMap<Integer, MaterialAppearance> CACHE = new ConcurrentHashMap<>();
    private static final long SEED = 42L;

    private final TextureAtlasSprite[] sprites = new TextureAtlasSprite[6];
    private final int[] tints = new int[6];
    private final RenderType renderType;
    private final TextureAtlasSprite particle;

    private MaterialAppearance(BlockState state) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(state);
        RandomSource random = RandomSource.create(SEED);
        particle = model.getParticleIcon(ModelData.EMPTY);

        for (Direction dir : Direction.values()) {
            BakedQuad quad = firstQuad(model, state, dir, random);
            sprites[dir.ordinal()] = quad != null ? quad.getSprite() : particle;
            tints[dir.ordinal()] = quad != null ? quad.getTintIndex() : -1;
        }

        random.setSeed(SEED);
        ChunkRenderTypeSet types = model.getRenderTypes(state, random, ModelData.EMPTY);
        Iterator<RenderType> it = types.iterator();
        renderType = it.hasNext() ? it.next() : RenderType.solid();
    }

    private static BakedQuad firstQuad(BakedModel model, BlockState state, Direction dir, RandomSource random) {
        random.setSeed(SEED);
        List<BakedQuad> culled = model.getQuads(state, dir, random, ModelData.EMPTY, null);
        if (!culled.isEmpty()) {
            return culled.get(0);
        }
        random.setSeed(SEED);
        for (BakedQuad quad : model.getQuads(state, null, random, ModelData.EMPTY, null)) {
            if (quad.getDirection() == dir) {
                return quad;
            }
        }
        return null;
    }

    public static MaterialAppearance of(int material) {
        return CACHE.computeIfAbsent(material, id -> new MaterialAppearance(Materials.state(id)));
    }

    public static void clear() {
        CACHE.clear();
    }

    public TextureAtlasSprite sprite(Direction dir) {
        return sprites[dir.ordinal()];
    }

    /** The material's own tint index for this face, or -1. */
    public int tint(Direction dir) {
        return tints[dir.ordinal()];
    }

    public RenderType renderType() {
        return renderType;
    }

    public TextureAtlasSprite particle() {
        return particle;
    }
}
