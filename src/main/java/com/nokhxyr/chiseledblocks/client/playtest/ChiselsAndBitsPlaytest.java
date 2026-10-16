/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client.playtest;

import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import com.nokhxyr.chiseledblocks.block.Materials;
import com.nokhxyr.chiseledblocks.compat.cnb.ChiselsAndBitsImport;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeSet;

/**
 * The Chisels &amp; Bits import, checked in two game runs.
 *
 * <p>{@code runClientCnbSource} has Chisels &amp; Bits installed: the player carves blocks with its
 * own chisel, then each block's data is decoded by {@link ChiselsAndBitsImport} and compared,
 * voxel by voxel, with the shape Chisels &amp; Bits itself computes. The expected result is written
 * to {@code cnb-expected.txt} and the world is saved.
 *
 * <p>{@code runClientCnbMigrate} opens a copy of that world with Chiseled Blocks alone and checks that
 * every block came back as a carved block with the same shape and materials.
 */
final class ChiselsAndBitsPlaytest {
    static final String WORLD = "cnb-source";
    private static final String EXPECTED = "cnb-expected.txt";
    private static final BlockPos STONE = new BlockPos(0, -60, 3);
    private static final BlockPos PLANKS = new BlockPos(1, -60, 3);
    private static final BlockPos GLASS = new BlockPos(-1, -60, 3);
    private static final BlockPos SOURCE = new BlockPos(2, -60, 3);
    private static final List<BlockPos> CHISELED = List.of(STONE, PLANKS, GLASS, SOURCE);
    private static final double[][] CLICKS = {{0.5, 0.5}, {0.3, 0.7}, {0.7, 0.3}, {0.55, 0.55}};

    private ChiselsAndBitsPlaytest() {
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static String heldId() {
        return BuiltInRegistries.ITEM.getKey(mc().player.getMainHandItem().getItem()).toString();
    }

    private static String blockId(BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(mc().level.getBlockState(pos).getBlock()).toString();
    }

    private static void lookAtNorth(ClientPlaytest p, BlockPos pos, double u, double v) {
        p.look(new Vec3(pos.getX() + u, pos.getY() + v, pos.getZ()));
    }

    // Step 1: carve with Chisels & Bits

    static void sourceScript(ClientPlaytest p) {
        p.step("set up the blocks and the Chisels & Bits chisel", () -> {
            p.command("gamerule doDaylightCycle false");
            p.command("time set noon");
            p.command("gamemode creative");
            p.command("tp @s 0.5 -60 0.5 0 0");
            p.command("setblock 0 -60 3 minecraft:stone");
            p.command("setblock 1 -60 3 minecraft:oak_planks");
            p.command("setblock -1 -60 3 minecraft:glass");
            p.command("setblock 2 -60 3 minecraft:stone");
            p.command("clear @s");
            p.command("item replace entity @s hotbar.0 with chiselsandbits:chisel_iron");
        }, 30, () -> p.that("held " + heldId(), heldId().equals("chiselsandbits:chisel_iron")));

        for (BlockPos target : List.of(STONE, PLANKS, GLASS)) {
            for (double[] click : CLICKS) {
                p.step("aim at " + target.toShortString(), () -> lookAtNorth(p, target, click[0], click[1]), 3, () -> true);
                p.step("left click with the Chisels & Bits chisel", p::attack, 8, () -> true);
            }
            p.step("the block is a Chisels & Bits block", () -> { }, 4,
                    () -> p.that("block " + blockId(target), blockId(target).equals("chiselsandbits:chiseled")));
        }

        // A block with two materials: carve a stone bit in survival, then place it into the planks.
        p.step("survival, carve the spare stone block", () -> {
            p.command("gamemode survival");
            p.hotbar(0);
            lookAtNorth(p, SOURCE, 0.5, 0.5);
        }, 6, () -> true);
        p.step("one stone bit comes out", p::attack, 10, () -> p.that("bit slot " + bitSlot(), bitSlot() >= 0));
        p.step("hold the bit", () -> p.hotbar(bitSlot()), 4,
                () -> p.that("held " + heldId(), heldId().equals("chiselsandbits:block_bit")));
        p.step("aim into the hole of the planks", () -> lookAtNorth(p, PLANKS, 0.53, 0.53), 3, () -> true);
        p.step("right click places the stone bit inside the planks", p::use, 10, () -> true);

        p.step("decode every block and compare with the Chisels & Bits shapes", () -> { }, 4, () -> {
            String result = compareOnServer();
            return p.that(result, result.startsWith("OK"));
        });
        p.step("save the world", () -> p.command("save-all flush"), 40, () -> true);
    }

    private static int bitSlot() {
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc().player.getInventory().getItem(slot);
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("chiselsandbits:block_bit")) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * On the server thread: decodes each block with Chiseled Blocks's reader and checks every voxel against
     * the shape Chisels & Bits computes. Writes the expected results for the migration run.
     */
    private static String compareOnServer() {
        MinecraftServer server = mc().getSingleplayerServer();
        return server.submit(() -> {
            ServerLevel level = server.overworld();
            List<String> expected = new ArrayList<>();
            StringBuilder summary = new StringBuilder();
            boolean ok = true;
            for (BlockPos pos : CHISELED) {
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity == null) {
                    summary.append(pos.toShortString()).append(" has no block entity; ");
                    ok = false;
                    continue;
                }
                CompoundTag tag = blockEntity.saveWithoutMetadata(level.registryAccess());
                try {
                    // Real Chisels & Bits data, kept as a fixture for the import game test.
                    Files.writeString(mc().gameDirectory.toPath().resolve("cnb-raw-" + pos.getX() + ".snbt"), tag.toString());
                } catch (IOException ignored) {
                    // Diagnostics only.
                }
                PixelGrid grid = ChiselsAndBitsImport.read(tag, level.registryAccess());
                if (grid == null) {
                    summary.append(pos.toShortString()).append(" not decoded, keys ").append(tag.getAllKeys()).append("; ");
                    try {
                        Files.writeString(mc().gameDirectory.toPath().resolve("cnb-raw-" + pos.getX() + ".snbt"), tag.toString());
                    } catch (IOException ignored) {
                        // Diagnostics only.
                    }
                    ok = false;
                    continue;
                }
                VoxelShape shape = level.getBlockState(pos).getShape(level, pos);
                List<AABB> boxes = shape.toAabbs();
                int mismatches = 0;
                for (int i = 0; i < PixelGrid.VOLUME; i++) {
                    double cx = ((i & 15) + 0.5) / 16;
                    double cy = ((i >> 8) + 0.5) / 16;
                    double cz = (((i >> 4) & 15) + 0.5) / 16;
                    boolean inShape = boxes.stream().anyMatch(box -> box.contains(cx, cy, cz));
                    if (inShape != (grid.get(i) != PixelGrid.AIR)) {
                        mismatches++;
                    }
                }
                if (mismatches > 0) {
                    try {
                        Files.writeString(mc().gameDirectory.toPath().resolve("cnb-decoded-" + pos.getX() + ".snbt"),
                                ChiselsAndBitsImport.describe(tag));
                    } catch (IOException ignored) {
                        // Diagnostics only.
                    }
                }
                String materials = materials(grid);
                summary.append(pos.toShortString()).append(": ").append(grid.filledCount()).append(" bits [")
                        .append(materials).append("] ").append(mismatches).append(" mismatches; ");
                ok &= mismatches == 0;
                expected.add(pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + materials + " " + occupancy(grid));
            }
            try {
                Files.write(mc().gameDirectory.toPath().resolve(EXPECTED), expected);
            } catch (IOException e) {
                return "FAIL could not write " + EXPECTED;
            }
            ChiseledBlocks.LOGGER.info("[playtest] Chisels & Bits comparison: {}", summary);
            return (ok ? "OK " : "FAIL ") + summary;
        }).join();
    }

    static String materials(PixelGrid grid) {
        TreeSet<String> names = new TreeSet<>();
        for (int i = 0; i < grid.paletteSize(); i++) {
            if (grid.paletteEntry(i) != PixelGrid.AIR) {
                BlockState state = Materials.state(grid.paletteEntry(i));
                names.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            }
        }
        return String.join(",", names);
    }

    static String occupancy(PixelGrid grid) {
        BitSet bits = new BitSet(PixelGrid.VOLUME);
        for (int i = 0; i < PixelGrid.VOLUME; i++) {
            bits.set(i, grid.get(i) != PixelGrid.AIR);
        }
        return HexFormat.of().formatHex(bits.toByteArray());
    }

    // Step 2: open the saved world with Chiseled Blocks only

    static void migrateScript(ClientPlaytest p) {
        List<String> lines = readExpected();
        p.step("the expected results from the source run are there", () -> { }, 1,
                () -> p.that("expected blocks " + lines.size(), lines.size() == CHISELED.size()));
        for (String line : lines) {
            String[] parts = line.split(" ");
            BlockPos pos = new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            String materials = parts[3];
            String occupancy = parts.length > 4 ? parts[4] : "";
            p.step("block " + pos.toShortString() + " is now a Chiseled Blocks carved block", () -> { }, 4, () -> {
                if (!(mc().level.getBlockEntity(pos) instanceof ChiseledBlockEntity carved)) {
                    return p.that("block " + blockId(pos), false);
                }
                PixelGrid grid = carved.getGrid();
                return p.that("materials " + materials(grid) + ", expected " + materials, materials(grid).equals(materials))
                        && p.that("shape differs from Chisels & Bits", occupancy(grid).equals(occupancy));
            });
        }
        p.shot("look at the migrated blocks", () -> {
            p.command("tp @s 0.5 -59.2 0.5 0 0");
        }, 15, () -> true, null);
        p.shot("migrated blocks", () -> p.look(new Vec3(0.5, -59.5, 3.0)), 10, () -> true, "cnb_migrated");
        p.step("save the migrated world", () -> p.command("save-all flush"), 30, () -> true);
    }

    private static List<String> readExpected() {
        Path path = mc().gameDirectory.toPath().resolve(EXPECTED);
        try {
            return Files.exists(path) ? Files.readAllLines(path) : List.of();
        } catch (IOException e) {
            return List.of();
        }
    }
}
