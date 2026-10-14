/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.core.voxel;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.IntPredicate;

/**
 * Micro-benchmarks of the voxel core on representative carvings. Run with ./gradlew benchmark.
 * Numbers are wall clock on the build machine, after JIT warm up, single thread.
 */
@Tag("benchmark")
class VoxelCoreBenchmark {
    private static final IntPredicate OPAQUE = m -> true;
    private static final int WARMUP = 2_000;
    private static final int RUNS = 5_000;

    private static Map<String, BitGrid> scenarios() {
        Map<String, BitGrid> map = new LinkedHashMap<>();
        map.put("full block", BitGrid.filled(1));
        map.put("half slab", new BitGridBuilder().fill(0, 0, 0, 16, 8, 16, 1).build());
        map.put("stairs + 3 materials", new BitGridBuilder()
                .fill(0, 0, 0, 16, 8, 16, 1).fill(0, 8, 8, 16, 16, 16, 2).fill(4, 0, 4, 12, 4, 12, 3).build());

        BitGridBuilder sphere = new BitGridBuilder();
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    double dx = x - 7.5, dy = y - 7.5, dz = z - 7.5;
                    if (dx * dx + dy * dy + dz * dz <= 64) {
                        sphere.set(x, y, z, 1);
                    }
                }
            }
        }
        map.put("sphere", sphere.build());

        Random random = new Random(1);
        int[] noise = new int[BitGrid.VOLUME];
        for (int i = 0; i < noise.length; i++) {
            noise[i] = random.nextInt(10) < 7 ? 1 + random.nextInt(4) : 0;
        }
        map.put("random noise, 4 materials", BitGrid.of(noise));

        int[] checker = new int[BitGrid.VOLUME];
        for (int i = 0; i < checker.length; i++) {
            int x = i & 15, z = (i >> 4) & 15, y = i >> 8;
            checker[i] = ((x + y + z) & 1) == 0 ? 1 : 0;
        }
        map.put("3D checkerboard (worst case)", BitGrid.of(checker));
        return map;
    }

    @Test
    void run() {
        GreedyMesher mesher = new GreedyMesher();
        GreedyMesher.QuadSink sink = (f, m, p, u0, v0, u1, v1, o) -> { };
        int[] scratch = new int[BitGrid.VOLUME];

        System.out.println();
        System.out.printf("%-30s %8s %8s %10s %10s %10s %10s %10s%n",
                "scenario", "payload", "quads", "naive", "mesh us", "boxes", "boxes us", "pack us");

        for (Map.Entry<String, BitGrid> e : scenarios().entrySet()) {
            BitGrid grid = e.getValue();
            int[] voxels = grid.toArray(new int[BitGrid.VOLUME]);

            for (int i = 0; i < WARMUP; i++) {
                mesher.mesh(grid, OPAQUE, sink);
                BoxMerger.merge(grid, OPAQUE);
                BitGrid.of(voxels.clone());
            }

            long t0 = System.nanoTime();
            int quads = 0;
            for (int i = 0; i < RUNS; i++) {
                quads = mesher.mesh(grid, OPAQUE, sink);
            }
            long t1 = System.nanoTime();
            int boxes = 0;
            for (int i = 0; i < RUNS; i++) {
                boxes = BoxMerger.merge(grid, OPAQUE).size();
            }
            long t2 = System.nanoTime();
            for (int i = 0; i < RUNS; i++) {
                System.arraycopy(voxels, 0, scratch, 0, scratch.length);
                BitGrid.of(scratch);
            }
            long t3 = System.nanoTime();

            System.out.printf("%-30s %7dB %8d %10d %10.1f %10d %10.1f %10.1f%n",
                    e.getKey(), grid.payloadBytes(), quads, naiveFaces(grid),
                    (t1 - t0) / 1000.0 / RUNS, boxes, (t2 - t1) / 1000.0 / RUNS, (t3 - t2) / 1000.0 / RUNS);
        }
        System.out.println("payload = packed voxel data, quads = greedy quads, naive = one quad per visible voxel face");
    }

    private static int naiveFaces(BitGrid grid) {
        int faces = 0;
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    if (grid.get(x, y, z) == BitGrid.AIR) {
                        continue;
                    }
                    for (int[] d : dirs) {
                        int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                        if (nx < 0 || ny < 0 || nz < 0 || nx > 15 || ny > 15 || nz > 15
                                || grid.get(nx, ny, nz) == BitGrid.AIR) {
                            faces++;
                        }
                    }
                }
            }
        }
        return faces;
    }
}
