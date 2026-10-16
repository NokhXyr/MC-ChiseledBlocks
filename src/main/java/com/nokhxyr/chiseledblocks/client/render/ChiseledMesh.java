/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client.render;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Baked quads of one grid, sorted by render layer and by cull face. Slot 6 holds the inner quads,
 * which are never culled by neighbours. Immutable once built, shared by every block with the
 * same grid.
 */
public final class ChiseledMesh {
    private static final int INNER = 6;

    private final Map<RenderType, List<BakedQuad>[]> quads;
    private final ChunkRenderTypeSet renderTypes;
    private final TextureAtlasSprite particle;

    private ChiseledMesh(Map<RenderType, List<BakedQuad>[]> quads, TextureAtlasSprite particle) {
        this.quads = quads;
        this.renderTypes = ChunkRenderTypeSet.of(quads.keySet());
        this.particle = particle;
    }

    public List<BakedQuad> quads(@Nullable RenderType renderType, @Nullable Direction side) {
        int slot = side == null ? INNER : side.ordinal();
        if (renderType != null) {
            List<BakedQuad>[] lists = quads.get(renderType);
            return lists == null ? List.of() : lists[slot];
        }
        List<BakedQuad> all = new ArrayList<>();
        for (List<BakedQuad>[] lists : quads.values()) {
            all.addAll(lists[slot]);
        }
        return all;
    }

    public ChunkRenderTypeSet renderTypes() {
        return renderTypes;
    }

    public TextureAtlasSprite particle() {
        return particle;
    }

    static final class Builder {
        private final Map<RenderType, List<BakedQuad>[]> quads = new IdentityHashMap<>();
        private TextureAtlasSprite particle;

        @SuppressWarnings("unchecked")
        void add(RenderType renderType, @Nullable Direction cullFace, BakedQuad quad) {
            List<BakedQuad>[] lists = quads.computeIfAbsent(renderType, rt -> {
                List<BakedQuad>[] created = new List[7];
                for (int i = 0; i < created.length; i++) {
                    created[i] = new ArrayList<>();
                }
                return created;
            });
            lists[cullFace == null ? INNER : cullFace.ordinal()].add(quad);
        }

        void particle(TextureAtlasSprite sprite) {
            if (particle == null) {
                particle = sprite;
            }
        }

        ChiseledMesh build(TextureAtlasSprite fallbackParticle) {
            for (List<BakedQuad>[] lists : quads.values()) {
                for (int i = 0; i < lists.length; i++) {
                    lists[i] = List.copyOf(lists[i]);
                }
            }
            return new ChiseledMesh(quads, particle != null ? particle : fallbackParticle);
        }
    }
}
