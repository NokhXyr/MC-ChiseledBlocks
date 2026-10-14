/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client.render;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.Face;
import com.nokhxyr.bitsmith.core.voxel.GreedyMesher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.BlockElementFace;
import net.minecraft.client.renderer.block.model.BlockFaceUV;
import net.minecraft.client.renderer.block.model.FaceBakery;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

import java.util.concurrent.ExecutionException;

/**
 * Builds and keeps the mesh of each distinct grid. Called from the chunk section compile
 * threads, so a miss costs a fraction of a millisecond on a worker thread, never on the render
 * thread. Keys are weak and compared by identity (grids are interned).
 */
public final class MeshCache {
    private static final Cache<BitGrid, CarvedMesh> CACHE = CacheBuilder.newBuilder()
            .weakKeys()
            .maximumSize(16_384)
            .build();
    private static final ThreadLocal<GreedyMesher> MESHER = ThreadLocal.withInitial(GreedyMesher::new);
    private static final FaceBakery BAKERY = new FaceBakery();
    private static final Direction[] DIRECTIONS = Direction.values();

    private MeshCache() {
    }

    public static CarvedMesh get(BitGrid grid, TextureAtlasSprite fallbackParticle) {
        try {
            return CACHE.get(grid, () -> build(grid, fallbackParticle));
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to mesh carved block", e.getCause());
        }
    }

    /** Drops every mesh and appearance, after textures or models were reloaded. */
    public static void clear() {
        CACHE.invalidateAll();
        MaterialAppearance.clear();
    }

    private static CarvedMesh build(BitGrid grid, TextureAtlasSprite fallbackParticle) {
        CarvedMesh.Builder builder = new CarvedMesh.Builder();
        MESHER.get().mesh(grid, Materials.OPAQUE, (face, material, plane, u0, v0, u1, v1, outer) -> {
            MaterialAppearance appearance = MaterialAppearance.of(material);
            Direction dir = DIRECTIONS[face.ordinal()];
            float[] min = new float[3];
            float[] max = new float[3];
            int u = GreedyMesher.uAxis(face.axis);
            int v = GreedyMesher.vAxis(face.axis);
            min[face.axis] = max[face.axis] = plane;
            min[u] = u0;
            max[u] = u1;
            min[v] = v0;
            max[v] = v1;
            builder.add(appearance.renderType(), outer ? dir : null,
                    bake(appearance, material, face, dir, min, max, outer));
            builder.particle(appearance.particle());
        });
        return builder.build(fallbackParticle);
    }

    private static BakedQuad bake(MaterialAppearance appearance, int material, Face face, Direction dir,
                                  float[] min, float[] max, boolean outer) {
        Vector3f from = new Vector3f(min[0], min[1], min[2]);
        Vector3f to = new Vector3f(max[0], max[1], max[2]);
        int tint = CarvedColors.encode(material, appearance.tint(dir));
        BlockElementFace elementFace = new BlockElementFace(outer ? dir : null, tint, "", new BlockFaceUV(uvs(dir, from, to), 0));
        return BAKERY.bakeQuad(from, to, elementFace, appearance.sprite(dir), dir, BlockModelRotation.X0_Y0, null, true);
    }

    /** Same projection as a vanilla model element without explicit UVs, so textures line up across bits. */
    private static float[] uvs(Direction dir, Vector3f from, Vector3f to) {
        return switch (dir) {
            case DOWN -> new float[]{from.x(), 16 - to.z(), to.x(), 16 - from.z()};
            case UP -> new float[]{from.x(), from.z(), to.x(), to.z()};
            case NORTH -> new float[]{16 - to.x(), 16 - to.y(), 16 - from.x(), 16 - from.y()};
            case SOUTH -> new float[]{from.x(), 16 - to.y(), to.x(), 16 - from.y()};
            case WEST -> new float[]{from.z(), 16 - to.y(), to.z(), 16 - from.y()};
            case EAST -> new float[]{16 - to.z(), 16 - to.y(), 16 - from.z(), 16 - from.y()};
        };
    }
}
