/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client.playtest;

import com.nokhxyr.bitsmith.item.BitBagItem;
import com.nokhxyr.bitsmith.item.BitItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * The Create addon ({@code ./gradlew :bitsmith-create:runClientCreate}), played like a player:
 * a creative motor turns a Mechanical Bit Mill, a chute feeds it, a hopper empties it into a
 * chest, a bit bag is filled from it by hand, and without rotation nothing is cut. Refers to the
 * addon's blocks by id only: Bitsmith itself knows nothing of the addon.
 */
final class CreateAddonPlaytest {
    private static final BlockPos MOTOR = new BlockPos(5, -60, 5);
    private static final BlockPos MILL = new BlockPos(6, -60, 5);
    private static final BlockPos CHEST = new BlockPos(6, -62, 5);
    private static final BlockPos IDLE = new BlockPos(10, -60, 5);

    private CreateAddonPlaytest() {
    }

    private static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    private static int bitsInChest() {
        return server().submit(() -> {
            int total = 0;
            if (server().overworld().getBlockEntity(CHEST) instanceof Container chest) {
                for (int i = 0; i < chest.getContainerSize(); i++) {
                    if (chest.getItem(i).getItem() instanceof BitItem) {
                        total += chest.getItem(i).getCount();
                    }
                }
            }
            return total;
        }).join();
    }

    /** Blocks waiting in the mill, read through its item handler like any machine would. */
    /** Puts items into a block's inventory on the server, from above, like a funnel or a belt. */
    private static void feed(BlockPos pos, ItemStack stack) {
        server().execute(() -> {
            IItemHandler items = server().overworld().getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
            if (items != null) {
                ItemHandlerHelper.insertItem(items, stack, false);
            }
        });
    }

    private static int blocksWaiting(BlockPos mill) {
        return slotCount(mill, 0);
    }

    /** Bits ready to come out (a stack at most), read the same way. */
    private static int bitsReady(BlockPos mill) {
        return slotCount(mill, 1);
    }

    private static int slotCount(BlockPos mill, int slot) {
        return server().submit(() -> {
            IItemHandler items = server().overworld().getCapability(Capabilities.ItemHandler.BLOCK, mill, Direction.UP);
            return items == null ? -1 : items.getStackInSlot(slot).getCount();
        }).join();
    }

    static void script(ClientPlaytest p) {
        p.step("set up", () -> {
            p.keepFlying = true;
            p.command("gamerule doDaylightCycle false");
            p.command("time set noon");
            p.command("gamemode creative");
            p.command("tp @s 6.5 -59 0.5 0 20");
        }, 30, () -> p.at(6.5, -59, 0.5));
        p.step("a motor, the mill, a chute on top, a hopper and a chest below", () -> {
            p.command("setblock 5 -60 5 create:creative_motor[facing=east]");
            p.command("setblock 6 -60 5 bitsmith_create:mechanical_bit_mill[axis=x]");
            p.command("setblock 6 -59 5 create:chute");
            p.command("setblock 6 -58 5 create:chute");
            p.command("setblock 6 -61 5 minecraft:hopper[facing=down]");
            p.command("setblock 6 -62 5 minecraft:chest");
        }, 20, () -> p.that("mill placed " + Minecraft.getInstance().level.getBlockState(MILL),
                Minecraft.getInstance().level.getBlockState(MILL).toString().contains("bitsmith_create:mechanical_bit_mill")));
        p.shot("the shaft turns through the mill", () -> p.look(new Vec3(6, -59.5, 5.5)), 20, () -> true, "create_mill_running");
        p.step("hand three stone blocks to the chute, as a funnel would", () -> feed(new BlockPos(6, -58, 5), new ItemStack(Blocks.STONE, 3)), 200,
                () -> p.that("stone bits in the chest " + bitsInChest() + ", bits ready in the mill " + bitsReady(MILL),
                        bitsInChest() > 0 && bitsReady(MILL) > 0));
        p.shot("bits reach the chest", () -> { }, 5, () -> true, "create_mill_chest");

        p.step("a bit bag in hand", () -> {
            p.command("clear @s");
            p.command("give @s bitsmith:bit_bag");
            p.hotbar(0);
        }, 10, () -> p.that("bag held", p.held().getItem() instanceof BitBagItem));
        p.step("right click the mill with the bag", () -> {
            p.lookAtFace(MILL, Direction.NORTH);
            p.use();
        }, 10, () -> {
            int stone = BitBagItem.contents(p.held()).count(Blocks.STONE.defaultBlockState());
            return p.that("stone bits in the bag " + stone, stone > 0);
        });

        // A second mill with no motor: blocks go in, nothing is cut.
        p.step("a second mill, with no motor", () -> {
            p.command("setblock 10 -60 5 bitsmith_create:mechanical_bit_mill[axis=x]");
            p.command("setblock 10 -59 5 create:chute");
        }, 10, () -> true);
        p.step("feed it two blocks", () -> feed(new BlockPos(10, -59, 5), new ItemStack(Blocks.OAK_PLANKS, 2)), 40, () -> true);
        p.step("without rotation the blocks only wait", () -> { }, 120,
                () -> p.eq("blocks waiting in the idle mill", blocksWaiting(IDLE), 2) && p.eq("bits ready", bitsReady(IDLE), 0));
    }
}
