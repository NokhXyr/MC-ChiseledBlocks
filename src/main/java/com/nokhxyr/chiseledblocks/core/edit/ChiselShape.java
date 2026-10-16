/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.core.edit;

import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.core.voxel.Face;

import java.util.function.IntConsumer;

/**
 * The voxels covered by one chisel stroke, inside the targeted block. Pure and deterministic:
 * the client uses it to predict a stroke and the server to apply it, with the same result.
 */
public final class ChiselShape {
    /** Tool sizes, in bits. */
    public static final int[] SIZES = {1, 2, 4, 8};

    private ChiselShape() {
    }

    /** Clamps any value to the closest allowed tool size not above it (at least 1). */
    public static int validSize(int size) {
        int result = SIZES[0];
        for (int candidate : SIZES) {
            if (candidate <= size) {
                result = candidate;
            }
        }
        return result;
    }

    public static int nextSize(int size) {
        int valid = validSize(size);
        for (int i = 0; i < SIZES.length; i++) {
            if (SIZES[i] == valid) {
                return SIZES[(i + 1) % SIZES.length];
            }
        }
        return SIZES[0];
    }

    /**
     * Bounding box of the stroke, as {minX, minY, minZ, maxX, maxY, maxZ} with max exclusive,
     * clipped to the block.
     */
    public static int[] bounds(ChiselMode mode, int size, int x, int y, int z, Face face) {
        int s = validSize(size);
        int[] target = {x, y, z};
        int[] box = new int[6];
        for (int a = 0; a < 3; a++) {
            int min;
            int max;
            switch (mode) {
                case PIXEL -> {
                    min = target[a];
                    max = min + 1;
                }
                case CUBE -> {
                    min = align(target[a], s);
                    max = min + s;
                }
                case LINE -> {
                    min = a == face.axis ? 0 : align(target[a], s);
                    max = a == face.axis ? PixelGrid.SIZE : min + s;
                }
                case PLANE -> {
                    min = a == face.axis ? align(target[a], s) : 0;
                    max = a == face.axis ? min + s : PixelGrid.SIZE;
                }
                case SPHERE -> {
                    min = target[a] - s;
                    max = target[a] + s + 1;
                }
                default -> throw new IllegalStateException();
            }
            box[a] = Math.max(0, min);
            box[a + 3] = Math.min(PixelGrid.SIZE, max);
        }
        return box;
    }

    /** Calls {@code out} with the {@link PixelGrid#index} of every voxel the stroke covers. */
    public static void forEach(ChiselMode mode, int size, int x, int y, int z, Face face, IntConsumer out) {
        int s = validSize(size);
        int[] box = bounds(mode, s, x, y, z, face);
        int radiusSquared = s * s;
        for (int vy = box[1]; vy < box[4]; vy++) {
            for (int vz = box[2]; vz < box[5]; vz++) {
                for (int vx = box[0]; vx < box[3]; vx++) {
                    if (mode == ChiselMode.SPHERE) {
                        int dx = vx - x;
                        int dy = vy - y;
                        int dz = vz - z;
                        if (dx * dx + dy * dy + dz * dz > radiusSquared) {
                            continue;
                        }
                    }
                    out.accept(PixelGrid.index(vx, vy, vz));
                }
            }
        }
    }

    private static int align(int value, int size) {
        return value - Math.floorMod(value, size);
    }
}
