/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client.playtest;

import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import com.nokhxyr.chiseledblocks.client.admin.AdminClient;
import com.nokhxyr.chiseledblocks.client.PlacementControl;
import com.nokhxyr.chiseledblocks.client.RadialMenuScreen;
import com.nokhxyr.chiseledblocks.client.admin.AdminScreen;
import com.nokhxyr.chiseledblocks.block.Materials;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.core.edit.PlacementMode;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGridBuilder;
import com.nokhxyr.chiseledblocks.item.PixelBagItem;
import com.nokhxyr.chiseledblocks.item.CreativePixelBagItem;
import com.nokhxyr.chiseledblocks.network.AdminPayloads;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksComponents;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksRegistries;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
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
 * The admin panel used with the mouse and keyboard, then the carved block items.
 * Search tab (scope, radius, a typed material, TP), chunk map (a chunk picked, TP), performance
 * tab (a measurement while carving, TP), then the per material items (give, place, pick block,
 * clear by tag).
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
            p.command("item replace entity @s hotbar.0 with chiseledblocks:iron_chisel");
        }, 30, () -> p.at(0.5, -60, 0.5));
        p.step("four carved blocks in three places", () -> server().submit(() -> {
            ServerLevel level = server().overworld();
            carve(level, AMETHYST, Blocks.AMETHYST_BLOCK, Blocks.STONE);
            carve(level, STONE, Blocks.STONE, null);
            carve(level, PLANKS, Blocks.OAK_PLANKS, null);
            carve(level, DIAMOND, Blocks.DIAMOND_BLOCK, Blocks.GOLD_BLOCK);
        }).join(), 10, () -> p.that("client sees the near carvings", p.gridAt(AMETHYST) != null && p.gridAt(STONE) != null));

        adminScreenScript(p);

        // A few strokes before measuring: the very first ones of a session also pay for class
        // loading and a cold JIT, which is not what a running server costs.
        p.step("back to the near blocks", () -> p.command("tp @s 0.5 -60 0.5 0 30"), 20, () -> p.at(0.5, -60, 0.5));
        p.step("hold the chisel", () -> p.hotbar(0), 2, () -> true);
        for (int i = 0; i < 8; i++) {
            p.step("warm up stroke " + (i + 1), () -> {
                p.look(new Vec3(AMETHYST.getX() + 0.5, AMETHYST.getY() + 0.25, AMETHYST.getZ() + 0.02));
                p.attack();
            }, 4, () -> true);
        }

        adminPerfScript(p);
        itemScript(p);
        materialItemScript(p);
        creativeBagScript(p);
        placementScript(p);
    }

    private static RadialMenuScreen radial() {
        return Minecraft.getInstance().screen instanceof RadialMenuScreen screen ? screen : null;
    }

    /** Clicks a named part of the open tool menu. */
    private static boolean clickRadial(ClientPlaytest p, String id) {
        RadialMenuScreen screen = radial();
        int[] point = screen == null ? null : screen.locate(id);
        if (point == null) {
            return p.that(id + " not in the tool menu", false);
        }
        p.cursorAt(point[0], point[1]);
        p.mouseClick();
        return true;
    }

    private static int filledRow(ClientPlaytest p, int fromX, int toX, int y, int z) {
        int total = 0;
        for (int x = fromX; x <= toX; x++) {
            total += Math.max(0, p.filled(new BlockPos(x, y, z)));
        }
        return total;
    }

    /** Placement modes and bag pages, with the creative bag in hand. */
    private static void placementScript(ClientPlaytest p) {
        p.step("stand next to an empty strip of grass", () -> {
            p.command("tp @s -2.5 -60 2.5 0 50");
            p.hotbar(0);
        }, 20, () -> p.at(-2.5, -60, 2.5));

        // Line: chosen in the tool menu, then two right clicks.
        p.step("open the tool menu", () -> p.key(GLFW.GLFW_KEY_LEFT_ALT, true), 8, () -> p.that("open", radial() != null));
        p.shot("click Line in the row of placement modes", () -> clickRadial(p, "place.line"), 6,
                () -> p.that("mode " + PlacementControl.mode(), PlacementControl.mode() == PlacementMode.LINE), "radial_placement_modes");
        p.step("release the menu key", () -> p.key(GLFW.GLFW_KEY_LEFT_ALT, false), 5, () -> p.that("closed", radial() == null));
        p.step("right click sets the start", () -> {
            p.lookAtFace(new BlockPos(-4, -61, 4), Direction.UP);
            p.use();
        }, 5, () -> p.that("start set", PlacementControl.start() != null));
        p.shot("the outline follows to the end", () -> p.lookAtFace(new BlockPos(-2, -61, 4), Direction.UP), 5,
                () -> true, "placement_line_preview");
        // Two blocks between the centres of the end faces: 33 bits, one more if a point falls on an edge.
        p.step("right click places the line", p::use, 10, () -> {
            int bits = filledRow(p, -4, -2, -60, 4);
            return p.that("bits in the line " + bits, bits >= 33 && bits <= 35);
        });
        p.step("tool menu", () -> p.key(GLFW.GLFW_KEY_LEFT_ALT, true), 8, () -> p.that("open", radial() != null));
        p.step("the undo button takes the whole line back", () -> clickRadial(p, "undo"), 10,
                () -> p.eq("bits left", filledRow(p, -4, -2, -60, 4), 0));
        p.step("close", () -> p.key(GLFW.GLFW_KEY_LEFT_ALT, false), 5, () -> radial() == null);

        // Box, flat on the ground: two corners.
        p.step("tool menu: Box", () -> p.key(GLFW.GLFW_KEY_LEFT_ALT, true), 8, () -> p.that("open", radial() != null));
        p.step("click Box", () -> clickRadial(p, "place.box"), 5,
                () -> p.that("mode " + PlacementControl.mode(), PlacementControl.mode() == PlacementMode.BOX));
        p.step("close", () -> p.key(GLFW.GLFW_KEY_LEFT_ALT, false), 5, () -> radial() == null);
        p.step("first corner", () -> {
            p.lookAtFace(new BlockPos(-4, -61, 5), Direction.UP);
            p.use();
        }, 5, () -> p.that("start set", PlacementControl.start() != null));
        p.step("second corner", () -> {
            p.lookAtFace(new BlockPos(-3, -61, 6), Direction.UP);
            p.use();
        }, 10, () -> p.eq("bits in the box", filledRow(p, -4, -3, -60, 5) + filledRow(p, -4, -3, -60, 6), 17 * 17));
        p.shot("the box on the grass", () -> p.look(new Vec3(-3.5, -60, 5.5)), 6, () -> true, "placement_box");

        // Draw: hold the use key and sweep along a row.
        p.step("tool menu: Draw", () -> p.key(GLFW.GLFW_KEY_LEFT_ALT, true), 8, () -> p.that("open", radial() != null));
        p.step("click Draw", () -> clickRadial(p, "place.draw"), 5,
                () -> p.that("mode " + PlacementControl.mode(), PlacementControl.mode() == PlacementMode.DRAW));
        p.step("close", () -> p.key(GLFW.GLFW_KEY_LEFT_ALT, false), 5, () -> radial() == null);
        p.step("closer to the row to draw on", () -> p.command("tp @s -1.5 -60 4.5 0 50"), 20, () -> p.at(-1.5, -60, 4.5));
        p.step("press and hold the use key on the grass", () -> {
            p.look(new Vec3(-2.45, -61, 6.5));
            KeyMapping.set(Minecraft.getInstance().options.keyUse.getKey(), true);
            p.use();
        }, 3, () -> true);
        for (int i = 1; i <= 10; i++) {
            double x = -2.45 + i * 0.2;
            p.step("sweep " + i, () -> p.look(new Vec3(x, -61, 6.5)), 2, () -> true);
        }
        p.step("release the use key", () -> KeyMapping.set(Minecraft.getInstance().options.keyUse.getKey(), false), 10,
                () -> p.that("bits drawn " + filledRow(p, -3, 0, -60, 6), filledRow(p, -3, 0, -60, 6) >= 20));
        p.step("back to single bits", () -> PlacementControl.set(PlacementMode.SINGLE), 2, () -> true);

        // Bag pages: twenty materials, twelve per page.
        p.step("a creative bag with twenty materials", () -> server().submit(() -> {
            var player = server().getPlayerList().getPlayers().get(0);
            ItemStack bag = new ItemStack(ChiseledBlocksRegistries.CREATIVE_PIXEL_BAG.get());
            int added = 0;
            for (Block block : BuiltInRegistries.BLOCK) {
                if (added < 20 && Materials.isValidMaterial(block.defaultBlockState()) && block.asItem() != net.minecraft.world.item.Items.AIR) {
                    CreativePixelBagItem.remember(bag, block.defaultBlockState());
                    added++;
                }
            }
            player.getInventory().setItem(0, bag);
        }).join(), 10, () -> p.eq("materials", PixelBagItem.contents(p.held()).entries().size(), 20));
        p.step("open the bag's menu", () -> p.key(GLFW.GLFW_KEY_LEFT_ALT, true), 8,
                () -> p.that("page 1", radial() != null && radial().page() == 0));
        p.shot("next page", () -> clickRadial(p, "page.next"), 6,
                () -> p.that("page " + (radial() == null ? -1 : radial().page() + 1), radial() != null && radial().page() == 1),
                "radial_bag_page2");
        p.step("click the first material of page 2", () -> clickRadial(p, "option.0"), 10, () -> {
            var selected = PixelBagItem.contents(p.held()).selected();
            return p.eq("selected material", selected, 12);
        });
        p.step("close", () -> p.key(GLFW.GLFW_KEY_LEFT_ALT, false), 5, () -> radial() == null);
    }

    /** The creative bit bag in survival: pick a material, place without running out, its menu. */
    private static void creativeBagScript(ClientPlaytest p) {
        BlockPos gold = new BlockPos(-1, -60, 2);
        BlockPos ground = new BlockPos(1, -61, 1);
        p.step("a creative bit bag, in survival", () -> {
            p.command("clear @s");
            p.command("setblock " + gold.getX() + " " + gold.getY() + " " + gold.getZ() + " minecraft:gold_block");
            p.command("give @s chiseledblocks:creative_pixel_bag");
            p.command("gamemode survival");
        }, 20, () -> {
            p.hotbar(0);
            return p.that("holding " + heldId(p), heldId(p).equals("chiseledblocks:creative_pixel_bag"));
        });
        p.step("sneak", () -> p.key(GLFW.GLFW_KEY_LEFT_SHIFT, true), 4, () -> true);
        p.step("sneak and right click the gold block", () -> {
            p.lookAtFace(gold, Direction.UP);
            p.use();
        }, 10, () -> {
            var selected = PixelBagItem.contents(p.held()).selectedEntry();
            return p.that("selected " + (selected == null ? "nothing" : selected.material()),
                    selected != null && selected.material().is(Blocks.GOLD_BLOCK));
        });
        p.step("stand up", () -> p.key(GLFW.GLFW_KEY_LEFT_SHIFT, false), 4, () -> true);
        for (int i = 0; i < 3; i++) {
            p.step("place a gold bit " + (i + 1), () -> {
                p.lookAtFace(ground, Direction.UP);
                p.use();
            }, 6, () -> true);
        }
        p.step("bits placed, the bag never empties", () -> { }, 10, () -> {
            var selected = PixelBagItem.contents(p.held()).selectedEntry();
            return p.that("bits " + p.filled(ground.above()), p.filled(ground.above()) >= 1)
                    && p.that("still gold", selected != null && selected.material().is(Blocks.GOLD_BLOCK));
        });
        p.shot("hold the menu key: the radial menu in the panel style", () -> p.key(GLFW.GLFW_KEY_LEFT_ALT, true), 10,
                () -> p.that("radial menu open", Minecraft.getInstance().screen instanceof RadialMenuScreen),
                "radial_creative_bag");
        p.step("release the menu key", () -> p.key(GLFW.GLFW_KEY_LEFT_ALT, false), 5,
                () -> p.that("closed", Minecraft.getInstance().screen == null));
        p.step("back to creative", () -> p.command("gamemode creative"), 5, () -> true);
    }

    private static String heldId(ClientPlaytest p) {
        return BuiltInRegistries.ITEM.getKey(p.held().getItem()).toString();
    }

    /** One item id per material: give, place, pick block and clear, as a player would. */
    private static void materialItemScript(ClientPlaytest p) {
        BlockPos ground = new BlockPos(-2, -61, 1);
        p.step("/give chiseledblocks:chiseled/oak_planks", () -> {
            p.command("tp @s 0.5 -60 0.5 0 30");
            p.command("clear @s");
            p.command("give @s chiseledblocks:chiseled/oak_planks");
        }, 10, () -> {
            p.hotbar(0);
            return p.that("held " + heldId(p) + " named " + p.held().getHoverName().getString(),
                    heldId(p).equals("chiseledblocks:chiseled/oak_planks") && p.held().getHoverName().getString().equals("Chiseled Oak Planks"));
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
        }, 10, () -> p.that("picked " + heldId(p), heldId(p).equals("chiseledblocks:chiseled/amethyst_block")
                && p.gridAt(AMETHYST) != null && p.gridAt(AMETHYST).equals(p.held().get(ChiseledBlocksComponents.GRID.get()))));
        p.step("/clear by the carved blocks tag", () -> p.command("clear @s #chiseledblocks:chiseled_blocks"), 10,
                () -> p.that("no carved block left, holding " + heldId(p), p.held().isEmpty()));
    }


    // The carved block item: its name and tooltip tell what it is made of

    private static void itemScript(ClientPlaytest p) {
        p.step("a carved block item in the hotbar", () -> {
            p.command("gamemode survival");
            server().submit(() -> {
                var player = server().getPlayerList().getPlayers().get(0);
                ItemStack stack = new ItemStack(ChiseledBlocksRegistries.CHISELED_BLOCK_ITEM.get());
                stack.set(ChiseledBlocksComponents.GRID.get(), new PixelGridBuilder()
                        .fill(0, 0, 0, 16, 8, 16, Materials.id(Blocks.AMETHYST_BLOCK.defaultBlockState()))
                        .fill(4, 8, 4, 12, 16, 12, Materials.id(Blocks.STONE.defaultBlockState()))
                        .build());
                player.getInventory().setItem(1, stack);
            }).join();
            p.hotbar(1);
        }, 10, () -> p.that("held item named " + p.held().getHoverName().getString(),
                p.held().getHoverName().getString().equals("Chiseled Block of Amethyst +1")));
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
                    lines.get(1).getString().contains("Block of Amethyst, 50 % (2048 pixels)"));
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
        p.shot("/chiseledblocks opens the admin screen on the search tab", () -> p.command("chiseledblocks"), 10,
                () -> p.that("admin screen open", onScreen()) && p.eq("blocks within 64 blocks", searchTotal(), 3), "ui_search");
        p.step("click All loaded chunks", () -> click(p, "segment.2"), 5,
                () -> p.eq("blocks in the loaded chunks", searchTotal(), 4));
        p.step("click the material field", () -> click(p, "material"), 3, () -> true);
        p.shot("type part of a material name", () -> p.type("diamond_bl"), 5, () -> true, "ui_suggestions");
        p.shot("Enter picks the suggestion and searches", () -> p.tap(GLFW.GLFW_KEY_ENTER), 5,
                () -> p.eq("diamond carvings", searchTotal(), 1), "ui_filtered");
        p.step("click TP on the diamond carving", () -> click(p, "tp.search.0"), 20,
                () -> p.at(100.5, -59, 100.5) && p.that("screen closed", !onScreen()));

        p.step("reopen and go to the chunk map", () -> p.command("chiseledblocks"), 10, () -> p.that("open", onScreen()));
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
        p.step("open the admin screen", () -> p.command("chiseledblocks"), 10, () -> p.that("open", onScreen()));
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
        p.shot("reopen: the report is in the Performance tab", () -> p.command("chiseledblocks"), 10, () -> {
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

    /** Server thread: places a carved block, half of one material and, if given, half of another. */
    private static void carve(ServerLevel level, BlockPos pos, Block bottom, @Nullable Block top) {
        PixelGridBuilder builder = new PixelGridBuilder().fill(0, 0, 0, 16, 8, 16, Materials.id(bottom.defaultBlockState()));
        if (top != null) {
            builder.fill(4, 8, 4, 12, 16, 12, Materials.id(top.defaultBlockState()));
        }
        PixelGrid grid = builder.build();
        level.setBlockAndUpdate(pos, ChiseledBlocksRegistries.CHISELED_BLOCK.get().defaultBlockState());
        if (level.getBlockEntity(pos) instanceof ChiseledBlockEntity carved) {
            carved.setGrid(grid);
            level.sendBlockUpdated(pos, carved.getBlockState(), carved.getBlockState(), 3);
        }
    }
}
