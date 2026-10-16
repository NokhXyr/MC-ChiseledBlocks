/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client.playtest;

import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.client.admin.AdminClient;
import com.nokhxyr.bitsmith.client.admin.AdminScreen;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.network.AdminPayloads;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

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

        adminScreenScript(p);

        // The same searches as text, for the server console.
        p.step("back to the start", () -> p.command("tp @s 0.5 -60 0.5 0 30"), 20, () -> p.at(0.5, -60, 0.5));
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

        adminPerfScript(p);
        itemScript(p);
        materialItemScript(p);
    }

    private static String heldId(ClientPlaytest p) {
        return BuiltInRegistries.ITEM.getKey(p.held().getItem()).toString();
    }

    /** One item id per material: give, place, pick block and clear, as a player would. */
    private static void materialItemScript(ClientPlaytest p) {
        BlockPos ground = new BlockPos(-2, -61, 1);
        p.step("/give bitsmith:carved/oak_planks", () -> {
            p.command("tp @s 0.5 -60 0.5 0 30");
            p.command("clear @s");
            p.command("give @s bitsmith:carved/oak_planks");
        }, 10, () -> {
            p.hotbar(0);
            return p.that("held " + heldId(p) + " named " + p.held().getHoverName().getString(),
                    heldId(p).equals("bitsmith:carved/oak_planks") && p.held().getHoverName().getString().equals("Carved Oak Planks"));
        });
        p.shot("place it on the grass", () -> {
            p.lookAtFace(ground, Direction.UP);
            p.use();
        }, 10, () -> p.eq("bits of the placed example", p.filled(ground.above()), 3072), "material_item_placed");
        p.step("pick block on the amethyst carving", () -> {
            p.command("clear @s");
            p.hotbar(0);
            p.look(new Vec3(AMETHYST.getX() + 0.5, AMETHYST.getY() + 0.25, AMETHYST.getZ() + 0.02));
            KeyMapping.click(Minecraft.getInstance().options.keyPickItem.getKey());
        }, 10, () -> p.that("picked " + heldId(p), heldId(p).equals("bitsmith:carved/amethyst_block")
                && p.gridAt(AMETHYST) != null && p.gridAt(AMETHYST).equals(p.held().get(BitsmithComponents.GRID.get()))));
        p.step("/clear by the carved blocks tag", () -> p.command("clear @s #bitsmith:carved_blocks"), 10,
                () -> p.that("no carved block left, holding " + heldId(p), p.held().isEmpty()));
    }


    // The carved block item: its name and tooltip tell what it is made of

    private static void itemScript(ClientPlaytest p) {
        p.step("a carved block item in the hotbar", () -> {
            p.command("gamemode survival");
            server().submit(() -> {
                var player = server().getPlayerList().getPlayers().get(0);
                ItemStack stack = new ItemStack(BitsmithRegistries.CARVED_BLOCK_ITEM.get());
                stack.set(BitsmithComponents.GRID.get(), new BitGridBuilder()
                        .fill(0, 0, 0, 16, 8, 16, Materials.id(Blocks.AMETHYST_BLOCK.defaultBlockState()))
                        .fill(4, 8, 4, 12, 16, 12, Materials.id(Blocks.STONE.defaultBlockState()))
                        .build());
                player.getInventory().setItem(1, stack);
            }).join();
            p.hotbar(1);
        }, 10, () -> p.that("held item named " + p.held().getHoverName().getString(),
                p.held().getHoverName().getString().equals("Carved Block of Amethyst +1")));
        p.step("open the inventory", () -> p.tap(GLFW.GLFW_KEY_E), 10,
                () -> p.that("inventory open", Minecraft.getInstance().screen instanceof InventoryScreen));
        p.shot("hover the carved block", () -> {
            if (Minecraft.getInstance().screen instanceof InventoryScreen screen) {
                Slot slot = screen.getMenu().slots.get(InventoryMenu.USE_ROW_SLOT_START + 1);
                p.cursorAt(screen.getGuiLeft() + slot.x + 8, screen.getGuiTop() + slot.y + 8);
            }
        }, 10, () -> {
            List<Component> lines = p.held().getTooltipLines(Item.TooltipContext.EMPTY, Minecraft.getInstance().player,
                    TooltipFlag.NORMAL);
            p.note("tooltip: " + lines.stream().map(Component::getString).toList());
            return p.eq("tooltip lines", lines.size(), 4) && p.that("main material first",
                    lines.get(1).getString().contains("Block of Amethyst, 50 % (2048 bits)"));
        }, "item_tooltip");
        p.step("close the inventory", () -> {
            p.tap(GLFW.GLFW_KEY_ESCAPE);
            p.command("gamemode creative");
        }, 10, () -> p.that("closed", Minecraft.getInstance().screen == null));
    }

    // The admin screen, used with the mouse and keyboard

    private static boolean onScreen() {
        return Minecraft.getInstance().screen instanceof AdminScreen;
    }

    /** Moves the cursor onto a named area of the admin screen and clicks it. */
    private static boolean click(ClientPlaytest p, String id) {
        if (!(Minecraft.getInstance().screen instanceof AdminScreen screen)) {
            return p.that("admin screen not open", false);
        }
        int[] point = screen.locate(id);
        if (point == null) {
            return p.that(id + " not on screen", false);
        }
        p.cursorAt(point[0], point[1]);
        p.mouseClick();
        return true;
    }

    private static int searchTotal() {
        AdminPayloads.FindResult result = AdminClient.lastSearch();
        return result == null || result.unknownMaterial() ? -1 : result.total();
    }

    private static void adminScreenScript(ClientPlaytest p) {
        p.shot("/bitsmith opens the admin screen on the search tab", () -> p.command("bitsmith"), 10,
                () -> p.that("admin screen open", onScreen()) && p.eq("blocks within 64 blocks", searchTotal(), 3), "ui_search");
        p.step("click All loaded chunks", () -> click(p, "segment.2"), 5,
                () -> p.eq("blocks in the loaded chunks", searchTotal(), 4));
        p.step("click the material field", () -> click(p, "material"), 3, () -> true);
        p.shot("type part of a material name", () -> p.type("diamond_bl"), 5, () -> true, "ui_suggestions");
        p.shot("Enter picks the suggestion and searches", () -> p.tap(GLFW.GLFW_KEY_ENTER), 5,
                () -> p.eq("diamond carvings", searchTotal(), 1), "ui_filtered");
        p.step("click TP on the diamond carving", () -> click(p, "tp.search.0"), 20,
                () -> p.at(100.5, -59, 100.5) && p.that("screen closed", !onScreen()));

        p.step("reopen and go to the chunk map", () -> p.command("bitsmith"), 10, () -> p.that("open", onScreen()));
        p.shot("click the Chunk map tab", () -> click(p, "tab.map"), 10, () -> {
            AdminPayloads.MapResult map = AdminClient.lastMap();
            int total = 0;
            if (map != null) {
                for (int count : map.counts()) {
                    total += count;
                }
            }
            return p.eq("carved blocks on the map", total, 4);
        }, "ui_map");
        p.shot("click the oak carving's chunk", () -> click(p, "cell.2.2"), 10, () -> {
            AdminPayloads.FindResult chunk = AdminClient.lastChunk();
            return p.eq("blocks listed for the chunk", chunk == null ? -1 : chunk.total(), 1);
        }, "ui_map_chunk");
        p.step("click TP in the chunk list", () -> click(p, "tp.chunk.0"), 20, () -> p.at(40.5, -59, 40.5));
    }

    private static void adminPerfScript(ClientPlaytest p) {
        p.step("back to the near blocks for a measurement", () -> p.command("tp @s 0.5 -60 0.5 0 30"), 20,
                () -> p.at(0.5, -60, 0.5));
        p.step("open the admin screen", () -> p.command("bitsmith"), 10, () -> p.that("open", onScreen()));
        p.step("click the Performance tab", () -> click(p, "tab.perf"), 5, () -> true);
        p.shot("click Start measuring", () -> click(p, "perf.toggle"), 10,
                () -> p.that("measurement running", AdminClient.perfRunning()), "ui_perf_running");
        p.step("close the screen to play", () -> p.tap(GLFW.GLFW_KEY_ESCAPE), 5, () -> p.that("closed", !onScreen()));
        for (int i = 0; i < 8; i++) {
            p.step("carve during the measurement " + (i + 1), () -> {
                p.look(new Vec3(STONE.getX() + 0.5, STONE.getY() + 0.25, STONE.getZ() + 0.02));
                p.attack();
            }, 4, () -> true);
        }
        p.step("the measurement ends", () -> { }, 200, () -> p.that("finished", !AdminClient.perfRunning()));
        p.shot("reopen: the report is in the Performance tab", () -> p.command("bitsmith"), 10, () -> {
            AdminPayloads.PerfReport report = AdminClient.lastPerfReport();
            if (report == null || report.rows().isEmpty()) {
                return p.that("no report", false);
            }
            AdminPayloads.PerfRow first = report.rows().get(0);
            p.note(String.format(java.util.Locale.ROOT, "ui perf: %d ticks, %.1f us/t total, top block %s at %.1f us/t, %d chisel strokes",
                    report.ticks(), report.perTick(report.totalNanos()), first.pos().toShortString(),
                    report.perTick(first.totalNanos()), first.calls()[0]));
            return p.that("screen open", onScreen()) && p.eq("window ticks", report.ticks(), 200)
                    && p.that("the carved block is first, with its strokes", first.pos().equals(STONE) && first.calls()[0] > 0);
        }, "ui_perf");
        p.step("click TP on the costliest block", () -> click(p, "tp.perf.0"), 20,
                () -> p.at(STONE.getX() + 0.5, STONE.getY() + 1, STONE.getZ() + 0.5));
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
