/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client.playtest;

import com.nokhxyr.chiseledblocks.metrics.Metrics;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

/**
 * The performance scenes of {@code docs/PROFILING.md}, played the same way with Chiseled Blocks or with
 * Chisels &amp; Bits ({@code -Dchiseledblocks.bench.mod=chiseledblocks|cnb}).
 *
 * <p>Eight blocks are carved with the mod's own chisel (single bits, sixteen per block), then
 * cloned into a 40 x 5 x 10 scene of 2000 carved blocks. A control scene of the same size is made
 * of plain stone. Frame times are recorded for 10 seconds in each scene: static view, continuous
 * carving, coming back after the chunks were unloaded, and flying over. Server tick times and,
 * for Chiseled Blocks, its own counters are recorded during carving.
 */
final class BenchPlaytest {
    static final String MOD = System.getProperty("chiseledblocks.bench.mod", "chiseledblocks");
    static final boolean CNB = MOD.equals("cnb");
    static final String CHISEL = CNB ? "chiselsandbits:chisel_iron" : "chiseledblocks:iron_chisel";
    static final String CHISELED = CNB ? "chiselsandbits:chiseled" : "chiseledblocks:chiseled_block";
    private static final List<String> MATERIALS = List.of("stone", "oak_planks", "glass", "bricks",
            "cobblestone", "spruce_planks", "white_wool", "sandstone");
    private static final double[] POINTS = {0.2, 0.4, 0.6, 0.8};
    private static final int WINDOW = 200;

    private BenchPlaytest() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static String blockId(BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(mc().level.getBlockState(pos).getBlock()).toString();
    }

    static void script(ClientPlaytest p) {
        carveReferences(p);
        cloneScene(p);
    }

    /**
     * Carves the eight reference blocks at (0..7, -60, 0) with the mod's own chisel, sixteen
     * single bits each, from a creative player flying in front of them. Shared with the stress run.
     */
    static void carveReferences(ClientPlaytest p) {
        p.step("set up for " + MOD, () -> {
            p.command("gamerule doDaylightCycle false");
            p.command("gamerule doMobSpawning false");
            p.command("gamerule doWeatherCycle false");
            p.command("time set noon");
            p.command("gamemode creative");
            p.command("tp @s 4.5 -60 -2.5 0 0");
            for (int i = 0; i < MATERIALS.size(); i++) {
                p.command("setblock " + i + " -60 0 minecraft:" + MATERIALS.get(i));
            }
            p.command("clear @s");
            p.command("item replace entity @s hotbar.0 with " + CHISEL);
        }, 30, () -> p.at(4.5, -60, -2.5));
        p.step("hold the chisel and fly", () -> {
            p.hotbar(0);
            // Creative flight, so that the views stay where the protocol puts them.
            p.keepFlying = true;
        }, 4, () -> true);

        // Carve the eight reference blocks with the mod's own chisel.
        for (int i = 0; i < MATERIALS.size(); i++) {
            int x = i;
            for (double u : POINTS) {
                for (double v : POINTS) {
                    p.step("aim", () -> p.look(new Vec3(x + u, -60 + v, 0)), 1, () -> true);
                    p.step("click", p::attack, 5, () -> true);
                }
            }
        }
        p.step("the eight references are carved", () -> { }, 10, () -> {
            for (int i = 0; i < MATERIALS.size(); i++) {
                BlockPos pos = new BlockPos(i, -60, 0);
                if (!blockId(pos).equals(CHISELED)) {
                    return p.that("block " + i + " is " + blockId(pos), false);
                }
            }
            return true;
        });
    }

    private static void cloneScene(ClientPlaytest p) {
        p.step("clone them into 2000 carved blocks, and build the stone control scene", () -> {
            for (int z = 0; z < 10; z++) {
                for (int y = 0; y < 5; y++) {
                    for (int x = 0; x < 5; x++) {
                        if (x == 0 && y == 0 && z == 0) {
                            continue;
                        }
                        p.command("clone 0 -60 0 7 -60 0 " + (x * 8) + " " + (-60 + y) + " " + z);
                    }
                }
            }
            p.command("fill 100 -60 0 139 -56 9 minecraft:stone");
        }, 60, () -> p.that("far corner " + blockId(new BlockPos(39, -56, 9)), blockId(new BlockPos(39, -56, 9)).equals(CHISELED)));

        p.step("let the server and the client settle after the clone", () -> { }, 200, () -> true);

        // S1: static view of the carved scene.
        p.step("S1 view the carved scene", () -> {
            p.command("tp @s 20.5 -50 -14.5 0 25");
        }, 20, () -> p.at(20.5, -50, -14.5));
        p.step("S1 let the sections build", () -> p.look(new Vec3(20, -58, 5)), 100, () -> true);
        record(p, "S1 static view, 2000 carved blocks");

        // Control: the same view of plain stone.
        p.step("control view", () -> p.command("tp @s 120.5 -50 -14.5 0 25"), 20, () -> p.at(120.5, -50, -14.5));
        p.step("control let the sections build", () -> p.look(new Vec3(120, -58, 5)), 100, () -> true);
        record(p, "Control static view, 2000 stone blocks");

        // S2: continuous carving in front of the scene.
        p.step("S2 stand in front of the scene", () -> p.command("tp @s 20.5 -58 -2.5 0 10"), 20, () -> p.at(20.5, -58, -2.5));
        p.step("S2 reset counters", () -> {
            Metrics.reset();
            p.startFrames();
        }, 1, () -> true);
        for (int i = 0; i < WINDOW / 4; i++) {
            int n = i;
            p.step("S2 stroke", () -> {
                double x = 16 + (n % 9) + 0.15 + 0.07 * (n % 7);
                double y = -60 + (n % 5) + 0.15 + 0.11 * (n % 6);
                p.look(new Vec3(x, y, 0));
            }, 1, () -> true);
            p.step("S2 click", p::attack, 3, () -> true);
        }
        p.step("S2 measure", () -> { }, 1, () -> {
            ClientPlaytest.FrameStats frames = p.stopFrames();
            p.note("S2 continuous carving: " + frames);
            p.note("S2 server ticks: " + serverTicks());
            if (Metrics.ENABLED) {
                p.note(String.format(Locale.ROOT,
                        "S2 Chiseled Blocks: %d strokes, %.1f us per stroke, %.1f us per tick for batched edits, "
                                + "%d meshes built, %.1f us per mesh, %d section rebuilds asked, %d resyncs",
                        Metrics.CHISEL_STROKES.get(), micros(Metrics.CHISEL_NANOS.get(), Metrics.CHISEL_STROKES.get()),
                        micros(Metrics.FLUSH_NANOS.get(), Metrics.FLUSH_TICKS.get()),
                        Metrics.MESH_BUILDS.get(), micros(Metrics.MESH_NANOS.get(), Metrics.MESH_BUILDS.get()),
                        Metrics.SECTION_DIRTY_MARKS.get(), Metrics.RESYNC_REQUESTS.get()));
            }
            return frames.frames() > 0;
        });

        // S3: come back after the chunks were unloaded.
        p.step("S3 go far away", () -> p.command("tp @s 2000.5 -50 2000.5"), 120, () -> p.at(2000.5, -50, 2000.5));
        p.step("S3 come back", () -> {
            p.command("tp @s 20.5 -50 -14.5 0 25");
            p.startFrames();
        }, 1, () -> true);
        p.step("S3 look at the scene", () -> p.look(new Vec3(20, -58, 5)), WINDOW, () -> true);
        p.step("S3 measure", () -> { }, 1, () -> {
            ClientPlaytest.FrameStats frames = p.stopFrames();
            p.note("S3 chunks loading back in: " + frames);
            return frames.frames() > 0;
        });

        // S4: fly over the scene.
        p.step("S4 start", () -> {
            p.command("tp @s -20 -50 5 -90 40");
            p.startFrames();
        }, 4, () -> true);
        for (int i = 0; i < WINDOW / 2; i++) {
            double x = -20 + i * 0.8;
            p.step("S4 fly", () -> p.command(String.format(Locale.ROOT, "tp @s %.2f -50 5 -90 40", x)), 2, () -> true);
        }
        p.step("S4 measure", () -> { }, 1, () -> {
            ClientPlaytest.FrameStats frames = p.stopFrames();
            p.note("S4 flying over: " + frames);
            return frames.frames() > 0;
        });

        // Spark profiles of the static view and of carving, saved to files (never uploaded).
        p.step("spark client profile of the static view", () -> {
            p.command("tp @s 20.5 -50 -14.5 0 25");
            p.command("sparkc profiler start --timeout 30 --save-to-file");
        }, 700, () -> true);
        p.step("spark server profile while carving", () -> {
            p.command("tp @s 20.5 -58 -2.5 0 10");
            p.command("spark profiler start --timeout 30 --save-to-file --thread *");
        }, 10, () -> true);
        for (int i = 0; i < 150; i++) {
            int n = i;
            p.step("spark stroke", () -> p.look(new Vec3(18 + (n % 5) + 0.3, -59 + (n % 3) + 0.3, 0)), 1, () -> true);
            p.step("spark click", p::attack, 3, () -> true);
        }
        p.step("let spark write its files", () -> { }, 200, () -> true);
    }

    private static void record(ClientPlaytest p, String name) {
        p.step(name + " (recording)", p::startFrames, WINDOW, () -> true);
        p.step(name + " (result)", () -> { }, 1, () -> {
            ClientPlaytest.FrameStats frames = p.stopFrames();
            p.note(name + ": " + frames);
            return frames.frames() > 0;
        });
    }

    private static String serverTicks() {
        MinecraftServer server = mc().getSingleplayerServer();
        long[] ticks = server.getTickTimesNanos();
        long total = 0;
        long worst = 0;
        int counted = 0;
        for (long nanos : ticks) {
            if (nanos > 0) {
                total += nanos;
                worst = Math.max(worst, nanos);
                counted++;
            }
        }
        return String.format(Locale.ROOT, "%.2f ms average, %.2f ms worst over the last %d ticks",
                counted == 0 ? 0 : total / 1e6 / counted, worst / 1e6, counted);
    }

    private static double micros(long nanos, long count) {
        return count == 0 ? 0 : nanos / 1e3 / count;
    }
}
