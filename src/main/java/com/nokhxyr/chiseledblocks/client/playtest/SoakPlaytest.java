/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client.playtest;

import com.mojang.authlib.GameProfile;
import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import com.nokhxyr.chiseledblocks.core.edit.ChiselMode;
import com.nokhxyr.chiseledblocks.core.edit.Symmetry;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.core.voxel.Face;
import com.nokhxyr.chiseledblocks.edit.ChiselLogic;
import com.nokhxyr.chiseledblocks.edit.EditHistory;
import com.nokhxyr.chiseledblocks.edit.PlayerSymmetry;
import com.nokhxyr.chiseledblocks.item.PixelItem;
import com.nokhxyr.chiseledblocks.item.ChiselSettings;
import com.nokhxyr.chiseledblocks.metrics.Metrics;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksComponents;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Multiplayer endurance run ({@code -Dchiseledblocks.soak.minutes}, 10 by default). Four bot players on
 * the server chisel, place, undo and redo at random over a 10 x 10 area while the real client
 * carves the same area. Every minute the bots pause and every carving the client sees is compared
 * with the server's: any difference is a desync.
 */
final class SoakPlaytest {
    private static final int MINUTES = Integer.getInteger("chiseledblocks.soak.minutes", 10);
    private static final int SIZE = 10;
    private static final int Y = -60;
    private static final List<FakePlayer> BOTS = new ArrayList<>();
    private static final Random RANDOM = new Random(7);
    private static long operations;
    private static int checks;
    private static int desyncs;

    private SoakPlaytest() {
    }

    private static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    static void script(ClientPlaytest p) {
        p.step("set up the soak area", () -> {
            p.keepFlying = true;
            p.command("gamerule doDaylightCycle false");
            p.command("gamerule doMobSpawning false");
            p.command("time set noon");
            p.command("gamemode creative");
            p.command("fill 0 " + Y + " 0 " + (SIZE - 1) + " " + Y + " " + (SIZE - 1) + " minecraft:stone");
            p.command("tp @s 4.5 -58 -2.5 0 25");
            p.command("clear @s");
            p.command("item replace entity @s hotbar.0 with chiseledblocks:iron_chisel");
        }, 30, () -> p.at(4.5, -58, -2.5));
        p.step("hold the chisel and fly", () -> {
            p.hotbar(0);
            p.keepFlying = true;
        }, 4, () -> true);
        p.step("four bots join", () -> server().submit(SoakPlaytest::spawnBots).join(), 4, () -> p.that("bots " + BOTS.size(), BOTS.size() == 4));

        int minutes = Math.max(1, MINUTES);
        for (int minute = 1; minute <= minutes; minute++) {
            int label = minute;
            // One minute of play: bots act every tick, the player clicks every 4 ticks.
            for (int i = 0; i < 300; i++) {
                int n = i;
                p.step("minute " + label + " play", () -> {
                    server().execute(SoakPlaytest::botsAct);
                    if (n % 2 == 0) {
                        p.look(new Vec3(RANDOM.nextInt(SIZE) + 0.2 + RANDOM.nextDouble() * 0.6, Y + 1.0,
                                RANDOM.nextInt(SIZE) + 0.2 + RANDOM.nextDouble() * 0.6));
                    } else {
                        p.attack();
                    }
                }, 3, () -> true);
            }
            p.step("minute " + label + " pause and compare client and server", () -> { }, 60, () -> {
                String result = compare();
                p.note("minute " + label + ": " + result + ", " + operations + " bot operations so far");
                return p.that(result, result.startsWith("0 desync"));
            });
        }
        p.step("soak summary", () -> { }, 1, () -> {
            p.note(String.format("soak: %d minutes, %d bot operations, %d comparisons, %d desyncs, %d resync requests",
                    minutes, operations, checks, desyncs, Metrics.RESYNC_REQUESTS.get()));
            return desyncs == 0;
        });
    }

    private static void spawnBots() {
        ServerLevel level = server().overworld();
        double[][] corners = {{1.5, 1.5}, {8.5, 1.5}, {1.5, 8.5}, {8.5, 8.5}};
        for (int i = 0; i < corners.length; i++) {
            FakePlayer bot = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "chiseledblocks-bot-" + i));
            bot.setGameMode(GameType.CREATIVE);
            bot.moveTo(corners[i][0], Y + 1, corners[i][1]);
            BOTS.add(bot);
        }
    }

    /** Server thread: every bot does one random thing. */
    private static void botsAct() {
        ServerLevel level = server().overworld();
        for (FakePlayer bot : BOTS) {
            BlockPos pos = new BlockPos(RANDOM.nextInt(SIZE), Y, RANDOM.nextInt(SIZE));
            int voxel = PixelGrid.index(RANDOM.nextInt(16), RANDOM.nextInt(16), RANDOM.nextInt(16));
            int action = RANDOM.nextInt(10);
            if (action < 6) {
                ItemStack chisel = new ItemStack(ChiseledBlocksRegistries.IRON_CHISEL.get());
                ChiselMode mode = ChiselMode.values()[RANDOM.nextInt(ChiselMode.values().length)];
                chisel.set(ChiseledBlocksComponents.CHISEL_SETTINGS.get(), new ChiselSettings(mode, 1 << RANDOM.nextInt(3)));
                bot.setItemInHand(InteractionHand.MAIN_HAND, chisel);
                PlayerSymmetry.setOnServer(bot, Symmetry.values()[RANDOM.nextInt(Symmetry.values().length)]);
                ChiselLogic.chisel(bot, pos, voxel, Face.values()[RANDOM.nextInt(6)]);
            } else if (action < 8) {
                bot.setItemInHand(InteractionHand.MAIN_HAND, PixelItem.of(Blocks.OAK_PLANKS.defaultBlockState(), 64));
                Vec3 click = new Vec3(pos.getX() + RANDOM.nextDouble(), pos.getY() + 1.0, pos.getZ() + RANDOM.nextDouble());
                bot.getMainHandItem().useOn(new UseOnContext(bot, InteractionHand.MAIN_HAND,
                        new BlockHitResult(click, Direction.UP, pos, false)));
            } else if (action < 9) {
                EditHistory.undo(bot);
            } else {
                EditHistory.redo(bot);
            }
            operations++;
        }
    }

    /** Compares every carving of the area, and the layer above it, between client and server. */
    private static String compare() {
        checks++;
        List<BlockPos> positions = new ArrayList<>();
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                positions.add(new BlockPos(x, Y, z));
                positions.add(new BlockPos(x, Y + 1, z));
            }
        }
        List<PixelGrid> serverGrids = server().submit(() -> {
            List<PixelGrid> grids = new ArrayList<>();
            for (BlockPos pos : positions) {
                grids.add(server().overworld().getBlockEntity(pos) instanceof ChiseledBlockEntity carved ? carved.getGrid() : null);
            }
            return grids;
        }).join();
        int differences = 0;
        int carved = 0;
        for (int i = 0; i < positions.size(); i++) {
            PixelGrid serverGrid = serverGrids.get(i);
            PixelGrid clientGrid = Minecraft.getInstance().level.getBlockEntity(positions.get(i)) instanceof ChiseledBlockEntity c
                    ? c.getGrid() : null;
            if (serverGrid != null) {
                carved++;
            }
            if (serverGrid == null ? clientGrid != null : !serverGrid.equals(clientGrid)) {
                differences++;
            }
        }
        desyncs += differences;
        return differences + " desync(s) over " + carved + " carved blocks";
    }
}
