/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.core.voxel;

import java.util.function.IntPredicate;

/**
 * Turns a {@link BitGrid} into the smallest practical set of rectangles: for every face direction
 * and every slice, visible faces of the same material are merged into maximal rectangles.
 *
 * <p>A face is visible when the voxel behind it is air, or a different material that is not
 * opaque (stone behind glass stays visible, glass behind glass of the same kind does not).
 * Faces lying on the block boundary are flagged {@code outer}: the caller decides whether they
 * are hidden by the neighbouring block, so the mesh itself never depends on the neighbours and
 * can be cached by grid content alone.
 *
 * <p>Cost is fixed: 6 directions x 16 slices x 256 cells, no allocation besides the reused mask.
 */
public final class GreedyMesher {

    /** Receives one merged rectangle. Coordinates are in voxel units (0..16). */
    public interface QuadSink {
        /**
         * @param face     direction the rectangle faces
         * @param material material of the voxels behind the rectangle
         * @param plane    position of the rectangle along the face axis (0..16)
         * @param u0       start on the first in-plane axis, inclusive
         * @param v0       start on the second in-plane axis, inclusive
         * @param u1       end on the first in-plane axis, exclusive
         * @param v1       end on the second in-plane axis, exclusive
         * @param outer    true when the rectangle lies on the block boundary
         */
        void accept(Face face, int material, int plane, int u0, int v0, int u1, int v1, boolean outer);
    }

    /** Index step of one voxel along X, Y and Z, matching {@link BitGrid#index(int, int, int)}. */
    private static final int[] STRIDES = {1, BitGrid.AREA, BitGrid.SIZE};

    private final int[] mask = new int[BitGrid.AREA];
    private final int[] voxels = new int[BitGrid.VOLUME];

    /** First in-plane axis for a face on {@code axis}. */
    public static int uAxis(int axis) {
        return (axis + 1) % 3;
    }

    /** Second in-plane axis for a face on {@code axis}. */
    public static int vAxis(int axis) {
        return (axis + 2) % 3;
    }

    /**
     * Meshes {@code grid} and returns the number of rectangles emitted. Not thread safe: use one
     * mesher per thread.
     *
     * @param opaque tells whether a material fully hides what lies behind it
     */
    public int mesh(BitGrid grid, IntPredicate opaque, QuadSink sink) {
        if (grid.isEmpty()) {
            return 0;
        }
        if (grid.paletteSize() == 1) {
            return meshUniform(grid.paletteEntry(0), sink);
        }

        grid.toArray(voxels);
        int quads = 0;
        for (Face face : Face.values()) {
            int axis = face.axis;
            int strideA = STRIDES[axis];
            int strideU = STRIDES[uAxis(axis)];
            int strideV = STRIDES[vAxis(axis)];
            int step = face.step();
            int behindOffset = step * strideA;

            for (int depth = 0; depth < BitGrid.SIZE; depth++) {
                int behind = depth + step;
                boolean outer = behind < 0 || behind >= BitGrid.SIZE;
                boolean any = false;
                int sliceBase = depth * strideA;

                for (int cv = 0; cv < BitGrid.SIZE; cv++) {
                    int rowBase = sliceBase + cv * strideV;
                    int maskRow = cv * BitGrid.SIZE;
                    for (int cu = 0; cu < BitGrid.SIZE; cu++) {
                        int index = rowBase + cu * strideU;
                        int material = voxels[index];
                        int cell = 0;
                        if (material != BitGrid.AIR) {
                            if (outer) {
                                cell = material;
                            } else {
                                int other = voxels[index + behindOffset];
                                if (other == BitGrid.AIR || (other != material && !opaque.test(other))) {
                                    cell = material;
                                }
                            }
                        }
                        mask[maskRow + cu] = cell;
                        any |= cell != 0;
                    }
                }

                if (any) {
                    int plane = face.positive ? depth + 1 : depth;
                    quads += mergeMask(face, plane, outer, sink);
                }
            }
        }
        return quads;
    }

    private static int meshUniform(int material, QuadSink sink) {
        for (Face face : Face.values()) {
            sink.accept(face, material, face.positive ? BitGrid.SIZE : 0, 0, 0, BitGrid.SIZE, BitGrid.SIZE, true);
        }
        return 6;
    }

    private int mergeMask(Face face, int plane, boolean outer, QuadSink sink) {
        int quads = 0;
        int size = BitGrid.SIZE;
        for (int v = 0; v < size; v++) {
            for (int u = 0; u < size; ) {
                int material = mask[u + v * size];
                if (material == 0) {
                    u++;
                    continue;
                }

                int width = 1;
                while (u + width < size && mask[u + width + v * size] == material) {
                    width++;
                }

                int height = 1;
                grow:
                while (v + height < size) {
                    int row = (v + height) * size;
                    for (int k = 0; k < width; k++) {
                        if (mask[u + k + row] != material) {
                            break grow;
                        }
                    }
                    height++;
                }

                for (int h = 0; h < height; h++) {
                    int row = (v + h) * size;
                    for (int k = 0; k < width; k++) {
                        mask[u + k + row] = 0;
                    }
                }

                sink.accept(face, material, plane, u, v, u + width, v + height, outer);
                quads++;
                u += width;
            }
        }
        return quads;
    }
}
