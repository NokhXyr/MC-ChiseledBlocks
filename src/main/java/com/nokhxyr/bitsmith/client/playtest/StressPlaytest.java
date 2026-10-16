/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client.playtest;

import com.nokhxyr.bitsmith.admin.BlockProfiler;
import com.nokhxyr.bitsmith.core.edit.PlacementMode;
import com.nokhxyr.bitsmith.edit.ShapePlacement;
import com.nokhxyr.bitsmith.item.BitItem;
import com.nokhxyr.bitsmith.metrics.Metrics;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Locale;

/**
 * The stress run ({@code ./gradlew runClientStress}, {@code runClientStressOpti},
 * {@code runClientStressArcadia}): the same scenes with Bitsmith alone, with the usual
 * optimisation mods, and inside the Arcadia modpack ({@code -Dbitsmith.stress.config}).
 *
 * <p>The eight reference blocks of the bench are carved with the chisel and cloned into 16,000
 * carved blocks (40 x 10 x 40). Measured: Java memory per carved block, frame times (static view,
 * a carving storm, a storm of large shape placements, chunks loading back, flying over), server
 * tick times at rest and under each storm, the time to save the world, and Bitsmith's own
 * per-block profiler. Spark profiles and a heap summary are saved to files, never uploaded.
 * Every result line starts with {@code STRESS} for the report.
 */
final class StressPlaytest {
    private static final String CONFIG = System.getProperty("bitsmith.stress.config", "alone");
    private static final int WINDOW = 200;
    private static final int SIZE_X = 40;
    private static final int SIZE_Y = 10;
    private static final int SIZE_Z = 40;
    private static final int BLOCKS = SIZE_X * SIZE_Y * SIZE_Z;
    private static final int PLACEMENTS = 60;

    private static volatile BlockProfiler.Report profile;
    private static long settleStart;
    private static long windowStart;

    private StressPlaytest() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static MinecraftServer server() {
        return mc().getSingleplayerServer();
    }

    private static String blockId(BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(mc().level.getBlockState(pos).getBlock()).toString();
    }

    private static void result(ClientPlaytest p, String line) {
        p.note("STRESS [" + CONFIG + "] " + line);
    }

    /** Heap in use after a full collection, in MB. */
    private static long heapMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
    }

    /** Server tick times over the last 100 ticks: average, 95th percentile and worst. */
    private static String serverTicks() {
        long[] ticks = Arrays.stream(server().getTickTimesNanos()).filter(nanos -> nanos > 0).sorted().toArray();
        if (ticks.length == 0) {
            return "no ticks";
        }
        double average = Arrays.stream(ticks).average().orElse(0) / 1e6;
        double p95 = ticks[Math.min(ticks.length - 1, (int) Math.ceil(ticks.length * 0.95) - 1)] / 1e6;
        double worst = ticks[ticks.length - 1] / 1e6;
        return String.format(Locale.ROOT, "server tick %.2f ms average, %.2f ms p95, %.2f ms worst (%d ticks)",
                average, p95, worst, ticks.length);
    }

    private static void frames(ClientPlaytest p, String name, int ticks) {
        p.step(name + " (recording)", p::startFrames, ticks, () -> true);
        p.step(name + " (result)", () -> { }, 1, () -> {
            ClientPlaytest.FrameStats frames = p.stopFrames();
            result(p, name + ": " + frames + "; " + serverTicks());
            return frames.frames() > 0;
        });
    }

    static void script(ClientPlaytest p) {
        BenchPlaytest.carveReferences(p);
        // A modpack may hand out items on first join (Arcadia's starter kits): only the chisel may be there.
        p.step("the inventory holds only the chisel", () -> { }, 1, () -> {
            var inventory = mc().player.getInventory();
            StringBuilder others = new StringBuilder();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                var stack = inventory.getItem(slot);
                String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                // Chisels & Bits gives the chiseled bits to the player, even in creative.
                boolean ownBits = BenchPlaytest.CNB && id.equals("chiselsandbits:block_bit");
                if (!stack.isEmpty() && !id.equals(BenchPlaytest.CHISEL) && !ownBits) {
                    others.append(stack).append(' ');
                }
            }
            return p.that("other items: " + (others.isEmpty() ? "none" : others.toString().trim()), others.isEmpty());
        });
        p.step("gamerules for a steady run", () -> {
            p.command("gamerule randomTickSpeed 0");
            p.command("kill @e[type=!player]");
        }, 20, () -> true);
        p.step("memory before the scene", System::gc, 40, () -> {
            result(p, "heap in use before the scene: " + heapMb() + " MB, max " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB");
            return true;
        });

        // 16,000 carved blocks: a row of five copies of the references, doubled along z, then up.
        p.until("clone the references into " + BLOCKS + " carved blocks", () -> {
            for (int x = 8; x < SIZE_X; x += 8) {
                p.command("clone 0 -60 0 7 -60 0 " + x + " -60 0");
            }
            int z = 1;
            while (z < SIZE_Z) {
                int copy = Math.min(z, SIZE_Z - z);
                p.command("clone 0 -60 0 " + (SIZE_X - 1) + " -60 " + (copy - 1) + " 0 -60 " + z);
                z += copy;
            }
            int y = 1;
            while (y < SIZE_Y) {
                int copy = Math.min(y, SIZE_Y - y);
                p.command("clone 0 -60 0 " + (SIZE_X - 1) + " " + (-60 + copy - 1) + " " + (SIZE_Z - 1) + " 0 " + (-60 + y) + " 0");
                y += copy;
            }
            p.command("fill 200 -60 0 239 -51 39 minecraft:stone");
        }, 2400, () -> {
            BlockPos far = new BlockPos(SIZE_X - 1, -60 + SIZE_Y - 1, SIZE_Z - 1);
            return p.that("far corner " + blockId(far), blockId(far).equals(BenchPlaytest.CARVED));
        });
        // Wait until the game is playable again (60 fps over a second), however long the mod needs.
        p.until("the scene is playable", () -> {
            p.command("tp @s 20.5 -35 -30.5 0 30");
            settleStart = System.nanoTime();
            windowStart = settleStart;
            p.startFrames();
        }, 6000, () -> {
            long now = System.nanoTime();
            if (now - windowStart < 1_000_000_000L) {
                return false;
            }
            ClientPlaytest.FrameStats frames = p.stopFrames();
            if (frames.averageFps() >= 60) {
                result(p, String.format(Locale.ROOT, "playable %.1f s after the scene was built (%.0f fps over the last second)",
                        (now - settleStart) / 1e9, frames.averageFps()));
                return true;
            }
            windowStart = now;
            p.startFrames();
            return false;
        });
        p.step("let the server and the client settle", () -> { }, 100, () -> true);
        p.step("memory with the scene", System::gc, 40, () -> {
            result(p, "heap in use at rest after a collection, with " + BLOCKS + " carved blocks: " + heapMb() + " MB");
            return true;
        });
        p.step("server at rest", () -> { }, 100, () -> {
            result(p, "at rest with " + BLOCKS + " carved blocks: " + serverTicks());
            return true;
        });

        // T1: static view of the whole scene, then the same view of plain stone.
        p.step("T1 look at the scene", () -> p.look(new Vec3(20, -55, 20)), 100, () -> true);
        frames(p, "T1 static view, " + BLOCKS + " carved blocks", WINDOW);
        p.step("control view", () -> {
            p.command("tp @s 220.5 -35 -30.5 0 30");
        }, 100, () -> p.at(220.5, -35, -30.5));
        p.step("control look", () -> p.look(new Vec3(220, -55, 20)), 60, () -> true);
        frames(p, "T1 control, " + BLOCKS + " stone blocks", WINDOW);

        // T2: a carving storm, a stroke every 2 ticks on the front face, with the block profiler on.
        p.step("T2 stand in front of the scene", () -> p.command("tp @s 20.5 -58 -2.5 0 10"), 20, () -> p.at(20.5, -58, -2.5));
        p.step("T2 start counters and the block profiler", () -> {
            Metrics.reset();
            profile = null;
            if (!BenchPlaytest.CNB) {
                server().execute(() -> BlockProfiler.start(WINDOW, report -> profile = report));
            }
            p.command("spark profiler start --timeout 25 --save-to-file --thread *");
            startRecording();
            p.startFrames();
        }, 1, () -> true);
        for (int i = 0; i < WINDOW / 2; i++) {
            int n = i;
            p.step("T2 stroke", () -> {
                double x = 18 + (n % 5) + 0.15 + 0.07 * (n % 7);
                double y = -60 + (n % 4) + 0.15 + 0.11 * (n % 6);
                p.look(new Vec3(x, y, 0));
                p.attack();
            }, 2, () -> true);
        }
        p.step("T2 result", () -> { }, 1, () -> {
            ClientPlaytest.FrameStats frames = p.stopFrames();
            String strokes = BenchPlaytest.CNB ? "100 clicks" : Metrics.CHISEL_STROKES.get() + " strokes taken by the server";
            result(p, "T2 carving storm (a click every 2 ticks, " + strokes + "): " + frames + "; " + serverTicks());
            saveRecording(p, "t2");
            if (Metrics.ENABLED && !BenchPlaytest.CNB) {
                result(p, String.format(Locale.ROOT, "T2 Bitsmith counters: %d strokes, %.1f us per stroke, %.1f us per tick for batched edits, "
                                + "%d meshes, %.1f us per mesh, %d section rebuilds, %d resyncs",
                        Metrics.CHISEL_STROKES.get(), micros(Metrics.CHISEL_NANOS.get(), Metrics.CHISEL_STROKES.get()),
                        micros(Metrics.FLUSH_NANOS.get(), Metrics.FLUSH_TICKS.get()), Metrics.MESH_BUILDS.get(),
                        micros(Metrics.MESH_NANOS.get(), Metrics.MESH_BUILDS.get()), Metrics.SECTION_DIRTY_MARKS.get(),
                        Metrics.RESYNC_REQUESTS.get()));
            }
            return frames.frames() > 0;
        });
        p.step("T2 block profiler report", () -> { }, 40, () -> {
            if (BenchPlaytest.CNB) {
                return true;
            }
            BlockProfiler.Report report = profile;
            if (report == null) {
                return p.that("the block profiler has not reported", false);
            }
            long worst = report.blocks().stream().mapToLong(BlockProfiler.BlockCost::totalNanos).max().orElse(0);
            result(p, String.format(Locale.ROOT, "T2 block profiler: %d blocks over %d ticks, %.1f us per tick in all, worst block %.2f us per tick",
                    report.blocks().size(), report.ticks(), report.perTick(report.totalNanos()), report.perTick(worst)));
            return true;
        });

        if (!BenchPlaytest.CNB) {
            placementStorm(p);
        }

        // T4: come back after the chunks were unloaded: the stone control, then the carved scene,
        // recorded with Java Flight Recorder when -Dbitsmith.stress.jfr=true (it slows the run down).
        comeBack(p, "T4 control, stone chunks loading back in", 220.5, new Vec3(220, -55, 20), false);
        comeBack(p, "T4 carved chunks loading back in", 20.5, new Vec3(20, -55, 20), true);

        // T5: fly over the scene, with a client Spark profile.
        p.step("T5 start", () -> {
            p.command("sparkc profiler start --timeout 15 --save-to-file");
            p.command("tp @s -20 -40 20 -90 35");
            p.startFrames();
        }, 4, () -> true);
        for (int i = 0; i < WINDOW / 2; i++) {
            double x = -20 + i * 0.8;
            p.step("T5 fly", () -> p.command(String.format(Locale.ROOT, "tp @s %.2f -40 20 -90 35", x)), 2, () -> true);
        }
        p.step("T5 result", () -> { }, 1, () -> {
            ClientPlaytest.FrameStats frames = p.stopFrames();
            result(p, "T5 flying over: " + frames);
            return frames.frames() > 0;
        });

        // T6: saving the world with every carved block in it.
        p.step("T6 save the world", () -> { }, 1, () -> {
            long nanos = server().submit(() -> {
                long start = System.nanoTime();
                server().saveEverything(true, true, true);
                return System.nanoTime() - start;
            }).join();
            result(p, String.format(Locale.ROOT, "T6 saving the world: %.1f ms", nanos / 1e6));
            return true;
        });
        p.step("heap summary", () -> p.command("spark heapsummary --save-to-file"), 200, () -> true);
        p.step("let spark write its files", () -> { }, 200, () -> true);
    }

    private static jdk.jfr.Recording recording;
    private static final boolean JFR = Boolean.getBoolean("bitsmith.stress.jfr");

    /** Starts a Java Flight Recorder recording when -Dbitsmith.stress.jfr=true. */
    private static void startRecording() {
        if (!JFR) {
            return;
        }
        try {
            recording = new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"));
            recording.start();
        } catch (java.io.IOException | java.text.ParseException e) {
            recording = null;
        }
    }

    /** Saves the recording, if any, as {@code stress-<name>.jfr} in the game directory. */
    private static void saveRecording(ClientPlaytest p, String name) {
        if (recording == null) {
            return;
        }
        try {
            java.nio.file.Path file = mc().gameDirectory.toPath().resolve("stress-" + name + ".jfr");
            recording.dump(file);
            recording.close();
            result(p, name + ": flight recording saved to " + file.getFileName());
        } catch (java.io.IOException e) {
            result(p, name + ": flight recording failed, " + e.getMessage());
        }
        recording = null;
    }

    private static void comeBack(ClientPlaytest p, String name, double x, Vec3 target, boolean record) {
        p.step(name + ": go far away", () -> p.command("tp @s 3000.5 -35 3000.5"), 160, () -> p.at(3000.5, -35, 3000.5));
        p.step(name + ": come back", () -> {
            if (record) {
                startRecording();
            }
            p.command(String.format(Locale.ROOT, "tp @s %.1f -35 -30.5 0 30", x));
            p.startFrames();
        }, 1, () -> true);
        p.step(name + ": look", () -> p.look(target), WINDOW, () -> true);
        p.step(name + ": result", () -> { }, 1, () -> {
            ClientPlaytest.FrameStats frames = p.stopFrames();
            result(p, name + ": " + frames + "; " + serverTicks());
            if (record) {
                saveRecording(p, "t4");
            }
            return frames.frames() > 0;
        });
    }

    /** T3, Bitsmith only (Chisels &amp; Bits has no shape placement): a box of 31 x 15 x 31 bits every 2 ticks. */
    private static void placementStorm(ClientPlaytest p) {
        p.step("T3 hold stone bits", () -> server().execute(() -> {
            ServerPlayer player = server().getPlayerList().getPlayers().get(0);
            player.setItemInHand(InteractionHand.MAIN_HAND, BitItem.of(Blocks.STONE.defaultBlockState(), 64));
        }), 5, () -> true);
        p.step("T3 start", () -> {
            p.command("tp @s 1.5 -57 -24.5 0 20");
            p.startFrames();
        }, 5, () -> true);
        for (int i = 0; i < PLACEMENTS; i++) {
            int bx = i * 2;
            p.step("T3 place a box", () -> server().execute(() -> {
                ServerPlayer player = server().getPlayerList().getPlayers().get(0);
                player.teleportTo(bx + 1.5, -58, -24.5);
                int[] start = {bx * 16, -60 * 16, -22 * 16};
                int[] end = {start[0] + 30, start[1] + 14, start[2] + 30};
                ShapePlacement.place(player, PlacementMode.BOX, start, end, 0);
            }), 2, () -> true);
        }
        p.step("T3 result", () -> { }, 1, () -> {
            ClientPlaytest.FrameStats frames = p.stopFrames();
            BlockPos placed = new BlockPos((PLACEMENTS - 1) * 2, -60, -22);
            result(p, String.format(Locale.ROOT, "T3 placement storm (%d boxes of 14,415 bits, one every 2 ticks): ", PLACEMENTS)
                    + frames + "; " + serverTicks());
            return p.that("last box placed: " + blockId(placed), blockId(placed).equals("bitsmith:carved_block"));
        });
    }

    private static double micros(long nanos, long count) {
        return count == 0 ? 0 : nanos / 1e3 / count;
    }
}
