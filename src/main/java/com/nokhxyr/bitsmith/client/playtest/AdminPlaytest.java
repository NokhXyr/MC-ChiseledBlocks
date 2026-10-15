/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client.playtest;

import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The {@code /bitsmith} command played from the chat: searches by radius of blocks, radius of
 * chunks and over the loaded chunks, a material filter, a click on a teleport link in the open
 * chat, the chunk map, and a profiling window while the player carves.
 */
final class AdminPlaytest {
    private static final BlockPos AMETHYST = new BlockPos(0, -60, 3);
    private static final BlockPos STONE = new BlockPos(2, -60, 3);
    /** Chunk 2, 2: inside a three chunk radius, outside an eight block one. */
    private static final BlockPos PLANKS = new BlockPos(40, -60, 40);
    /** Chunk 6, 6: loaded (render distance 12), outside a three chunk radius. */
    private static final BlockPos DIAMOND = new BlockPos(100, -60, 100);

    private AdminPlaytest() {
    }

    private static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    static void script(ClientPlaytest p) {
        p.step("set up", () -> {
            p.keepFlying = true;
            p.command("gamerule doDaylightCycle false");
            p.command("gamerule doMobSpawning false");
            p.command("time set noon");
            p.command("gamemode creative");
            p.command("tp @s 0.5 -60 0.5 0 30");
            p.command("clear @s");
            p.command("item replace entity @s hotbar.0 with bitsmith:iron_chisel");
        }, 30, () -> p.at(0.5, -60, 0.5));
        p.step("four carved blocks in three places", () -> server().submit(() -> {
            ServerLevel level = server().overworld();
            carve(level, AMETHYST, Blocks.AMETHYST_BLOCK, Blocks.STONE);
            carve(level, STONE, Blocks.STONE, null);
            carve(level, PLANKS, Blocks.OAK_PLANKS, null);
            carve(level, DIAMOND, Blocks.DIAMOND_BLOCK, Blocks.GOLD_BLOCK);
        }).join(), 10, () -> p.that("client sees the near carvings", p.gridAt(AMETHYST) != null && p.gridAt(STONE) != null));

        find(p, "bitsmith find radius 8", 2);
        find(p, "bitsmith find chunks 3", 3);
        find(p, "bitsmith find loaded", 4);
        p.step("the loaded search lists the busiest chunks", () -> { }, 1,
                () -> p.that("chunk list received", last(p, "bitsmith.find.chunks") != null));
        find(p, "bitsmith find loaded minecraft:amethyst_block", 1);
        find(p, "bitsmith find radius 8 minecraft:diamond_block", 0);

        // The far block, then its link clicked in the open chat.
        p.step("search the diamond carving", () -> p.command("bitsmith find radius 200 minecraft:diamond_block"), 5,
                () -> p.eq("blocks found", count(p), 1));
        p.shot("open the chat on the results", () -> Minecraft.getInstance().setScreen(new ChatScreen("")), 5,
                () -> p.that("chat open", Minecraft.getInstance().screen instanceof ChatScreen), "admin_find");
        p.step("click the [TP] link", () -> p.that("link found on screen", p.clickChatLink(style ->
                        style.getClickEvent().getAction() == ClickEvent.Action.RUN_COMMAND
                                && style.getClickEvent().getValue().contains("tp @s 100.5 -59 100.5"))), 20,
                () -> p.at(100.5, -59, 100.5));
        p.shot("close the chat on the diamond carving", () -> {
            Minecraft.getInstance().setScreen(null);
            p.look(DIAMOND.getCenter());
        }, 20, () -> p.that("diamond carving in view", p.gridAt(DIAMOND) != null), "admin_after_tp");

        // Map of the chunks around the player: chunk 6, 6 with a radius of 6 reaches chunk 0, 0.
        p.step("chunk map", () -> p.command("bitsmith map 6"), 5, () -> {
            Component summary = last(p, "bitsmith.map.summary");
            return p.that("map summary " + (summary == null ? "missing" : summary.getString()),
                    summary != null && arg(summary, 1).equals("4") && arg(summary, 2).equals("3"));
        });
        p.shot("open the chat on the map", () -> Minecraft.getInstance().setScreen(new ChatScreen("")), 5,
                () -> true, "admin_map");
        p.step("click the chunk of the oak carving on the map", () -> p.clickChatLink(style ->
                        style.getClickEvent().getValue().contains("tp @s 40.5 -59 40.5")), 20,
                () -> p.at(40.5, -59, 40.5));

        // Profiling while carving by hand.
        p.step("back to the near blocks", () -> {
            Minecraft.getInstance().setScreen(null);
            p.command("tp @s 0.5 -60 0.5 0 30");
        }, 20, () -> p.at(0.5, -60, 0.5));
        // A few strokes first: the very first ones of a session also pay for class loading and
        // a cold JIT, which is not what a running server costs.
        p.step("hold the chisel", () -> p.hotbar(0), 2, () -> true);
        for (int i = 0; i < 8; i++) {
            p.step("warm up stroke " + (i + 1), () -> {
                p.look(new Vec3(AMETHYST.getX() + 0.5, AMETHYST.getY() + 0.25, AMETHYST.getZ() + 0.02));
                p.attack();
            }, 4, () -> true);
        }
        p.step("start a three second measurement", () -> {
            p.command("bitsmith perf 3");
        }, 2, () -> p.that("started", last(p, "bitsmith.perf.started") != null));
        for (int i = 0; i < 8; i++) {
            p.step("carve while measuring " + (i + 1), () -> {
                // The lower half is carved stone: aim at its north face.
                p.look(new Vec3(STONE.getX() + 0.5, STONE.getY() + 0.25, STONE.getZ() + 0.02));
                p.attack();
            }, 4, () -> true);
        }
        p.step("the report arrives when the window ends", () -> { }, 40, () -> {
            Component header = last(p, "bitsmith.perf.header");
            Component entry = perfEntry(p, STONE);
            if (header != null) {
                p.note("perf: " + header.getString());
            }
            if (entry != null) {
                p.note("perf: " + entry.getString());
            }
            return p.that("header " + (header == null ? "missing" : arg(header, 0) + " ticks") + ", entry for the carved block "
                    + (entry == null ? "missing" : entry.getString()),
                    header != null && arg(header, 0).equals("60") && entry != null && entry.getString().contains("chisel"));
        });
        p.shot("open the chat on the report", () -> Minecraft.getInstance().setScreen(new ChatScreen("")), 5,
                () -> true, "admin_perf");
        p.step("stopping with no measurement running is refused", () -> {
            Minecraft.getInstance().setScreen(null);
            p.command("bitsmith perf stop");
        }, 5, () -> p.that("refusal received", last(p, "bitsmith.perf.none") != null));
    }

    private static void find(ClientPlaytest p, String command, int expected) {
        p.step("/" + command, () -> p.command(command), 5, () -> p.eq("blocks found", count(p), expected));
    }

    /** Server thread: places a carved block, half of one material and, if given, half of another. */
    private static void carve(ServerLevel level, BlockPos pos, Block bottom, @Nullable Block top) {
        BitGridBuilder builder = new BitGridBuilder().fill(0, 0, 0, 16, 8, 16, Materials.id(bottom.defaultBlockState()));
        if (top != null) {
            builder.fill(4, 8, 4, 12, 16, 12, Materials.id(top.defaultBlockState()));
        }
        BitGrid grid = builder.build();
        level.setBlockAndUpdate(pos, BitsmithRegistries.CARVED_BLOCK.get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof CarvedBlockEntity carved) {
            carved.setGrid(grid);
            level.sendBlockUpdated(pos, carved.getBlockState(), carved.getBlockState(), 3);
        }
    }

    /** The number of blocks in the last search header, or -1. */
    private static int count(ClientPlaytest p) {
        Component header = last(p, "bitsmith.find.header");
        return header == null ? -1 : Integer.parseInt(arg(header, 1));
    }

    private static @Nullable Component perfEntry(ClientPlaytest p, BlockPos pos) {
        for (int i = p.systemChat.size() - 1; i >= 0; i--) {
            Component entry = find(p.systemChat.get(i), "bitsmith.perf.entry");
            if (entry != null && arg(entry, 0).equals(String.valueOf(pos.getX())) && arg(entry, 1).equals(String.valueOf(pos.getY()))
                    && arg(entry, 2).equals(String.valueOf(pos.getZ()))) {
                return entry;
            }
        }
        return null;
    }

    /** The newest system message holding the given translation key, as that translatable part. */
    private static @Nullable Component last(ClientPlaytest p, String key) {
        List<Component> chat = p.systemChat;
        for (int i = chat.size() - 1; i >= 0; i--) {
            Component found = find(chat.get(i), key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static @Nullable Component find(Component component, String key) {
        if (component.getContents() instanceof TranslatableContents translatable && translatable.getKey().equals(key)) {
            return component;
        }
        for (Component sibling : component.getSiblings()) {
            Component found = find(sibling, key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String arg(Component component, int index) {
        Object arg = ((TranslatableContents) component.getContents()).getArgs()[index];
        return arg instanceof Component text ? text.getString() : String.valueOf(arg);
    }
}
