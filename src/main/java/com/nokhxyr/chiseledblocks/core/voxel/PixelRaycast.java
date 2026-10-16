/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.core.voxel;

import java.util.function.IntPredicate;

/**
 * Exact voxel picking by grid traversal (Amanatides and Woo). Visits only the voxels the ray
 * crosses, at most 46 for a 16^3 grid, so targeting a bit never needs a VoxelShape.
 */
public final class PixelRaycast {

    /**
     * @param face     face of the voxel the ray entered through, or {@code null} when the ray
     *                 starts inside that voxel
     * @param distance ray parameter at the hit, in the units of the direction vector
     */
    public record Hit(int x, int y, int z, Face face, double distance) {
    }

    private PixelRaycast() {
    }

    /**
     * Casts a ray in voxel space (block local coordinates multiplied by 16).
     *
     * @param maxDistance largest ray parameter accepted
     * @param solid       which materials stop the ray
     * @return the first voxel hit, or {@code null}
     */
    public static Hit cast(PixelGrid grid,
                           double ox, double oy, double oz,
                           double dx, double dy, double dz,
                           double maxDistance, IntPredicate solid) {
        double[] origin = {ox, oy, oz};
        double[] dir = {dx, dy, dz};
        int size = PixelGrid.SIZE;

        double tEnter = 0;
        double tExit = Double.POSITIVE_INFINITY;
        int enterAxis = -1;
        for (int a = 0; a < 3; a++) {
            if (Math.abs(dir[a]) < 1e-12) {
                if (origin[a] < 0 || origin[a] > size) {
                    return null;
                }
                continue;
            }
            double t1 = (0 - origin[a]) / dir[a];
            double t2 = (size - origin[a]) / dir[a];
            double near = Math.min(t1, t2);
            double far = Math.max(t1, t2);
            if (near > tEnter) {
                tEnter = near;
                enterAxis = a;
            }
            tExit = Math.min(tExit, far);
            if (tEnter > tExit) {
                return null;
            }
        }
        if (tExit < 0 || tEnter > maxDistance) {
            return null;
        }

        int[] cell = new int[3];
        int[] step = new int[3];
        double[] tNext = new double[3];
        double[] tDelta = new double[3];
        for (int a = 0; a < 3; a++) {
            double p = origin[a] + dir[a] * tEnter;
            cell[a] = Math.max(0, Math.min(size - 1, (int) Math.floor(p)));
            if (dir[a] > 0) {
                step[a] = 1;
                tNext[a] = (cell[a] + 1 - origin[a]) / dir[a];
                tDelta[a] = 1 / dir[a];
            } else if (dir[a] < 0) {
                step[a] = -1;
                tNext[a] = (cell[a] - origin[a]) / dir[a];
                tDelta[a] = -1 / dir[a];
            } else {
                tNext[a] = Double.POSITIVE_INFINITY;
                tDelta[a] = Double.POSITIVE_INFINITY;
            }
        }

        int lastAxis = enterAxis;
        double t = tEnter;
        while (true) {
            int material = grid.get(cell[0], cell[1], cell[2]);
            if (material != PixelGrid.AIR && solid.test(material)) {
                Face face = lastAxis < 0 ? null : Face.of(lastAxis, step[lastAxis] < 0);
                return new Hit(cell[0], cell[1], cell[2], face, t);
            }

            int a = tNext[0] < tNext[1]
                    ? (tNext[0] < tNext[2] ? 0 : 2)
                    : (tNext[1] < tNext[2] ? 1 : 2);
            t = tNext[a];
            if (t > tExit || t > maxDistance) {
                return null;
            }
            cell[a] += step[a];
            if (cell[a] < 0 || cell[a] >= size) {
                return null;
            }
            tNext[a] += tDelta[a];
            lastAxis = a;
        }
    }
}
