/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client.playtest;

import com.nokhxyr.bitsmith.item.BitItem;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * The Create addon's bit line ({@code ./gradlew :bitsmith-create:runClientCreate}), built and run
 * like a player would: a Create belt laid with the belt connector between two shafts, the Bit
 * Slicer, Bit Cutter and Bit Crusher two blocks above it turned by a motor, a hopper and a chest at
 * the end. One stone block goes in, 4096 stone bits must come out. Refers to the addon's blocks by
 * id only: Bitsmith itself knows nothing of the addon.
 */
final class CreateAddonPlaytest {
    private static final int Z = 5;
    private static final int BELT_Y = -60;
    private static final int MACHINE_Y = BELT_Y + 2;
    private static final BlockPos START = new BlockPos(0, BELT_Y, Z);
    private static final BlockPos END = new BlockPos(6, BELT_Y, Z);
    private static final BlockPos HOPPER = new BlockPos(7, BELT_Y - 1, Z);
    private static final BlockPos CHEST = new BlockPos(7, BELT_Y - 2, Z);

    private CreateAddonPlaytest() {
    }

    private static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    private static int stoneBits(ItemStack stack) {
        return stack.getItem() instanceof BitItem && BitItem.material(stack) == Blocks.STONE.defaultBlockState() ? stack.getCount() : 0;
    }

    /**
     * Stone bits the line made: still on the belt past the crusher, in the hopper and the chest, or
     * thrown off the end of the belt. A chest cannot hold 64 stacks; what matters is what was made.
     */
    private static int bitsOut() {
        return server().submit(() -> {
            var level = server().overworld();
            int total = 0;
            for (int x = START.getX(); x <= END.getX(); x++) {
                IItemHandler belt = level.getCapability(Capabilities.ItemHandler.BLOCK, new BlockPos(x, BELT_Y, Z), Direction.UP);
                for (int slot = 0; belt != null && slot < belt.getSlots(); slot++) {
                    total += stoneBits(belt.getStackInSlot(slot));
                }
            }
            for (BlockPos pos : new BlockPos[]{HOPPER, CHEST}) {
                if (level.getBlockEntity(pos) instanceof Container container) {
                    for (int i = 0; i < container.getContainerSize(); i++) {
                        total += stoneBits(container.getItem(i));
                    }
                }
            }
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(END).inflate(3))) {
                total += stoneBits(entity.getItem());
            }
            return total;
        }).join();
    }

    /** A machine's saved state, its speed and how far it is into a cut. */
    private static String machineState(BlockPos pos) {
        return server().submit(() -> {
            var entity = server().overworld().getBlockEntity(pos);
            if (entity == null) {
                return "no block entity";
            }
            var tag = entity.saveWithoutMetadata(server().registryAccess());
            return "speed " + tag.getFloat("Speed") + ", running " + tag.getBoolean("running") + ", progress " + tag.getFloat("progress");
        }).join();
    }

    /** One of the addon's pieces, by id: a slice or a rod of {@code material}. */
    private static ItemStack piece(String id, BlockState material) {
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("bitsmith_create", id)));
        stack.set(BitsmithComponents.BIT_MATERIAL.get(), material);
        return stack;
    }

    /** Every loose item along the line and in the hopper and chest, to tell where things went. */
    private static String whereItemsAre() {
        return server().submit(() -> {
            var level = server().overworld();
            StringBuilder out = new StringBuilder();
            for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, new AABB(-4, BELT_Y - 3, Z - 4, 11, BELT_Y + 4, Z + 4))) {
                out.append(entity.getItem()).append(" at ").append(entity.blockPosition().toShortString()).append("; ");
            }
            for (int x = START.getX(); x <= END.getX(); x++) {
                BlockPos segment = new BlockPos(x, BELT_Y, Z);
                IItemHandler belt = level.getCapability(Capabilities.ItemHandler.BLOCK, segment, Direction.UP);
                for (int slot = 0; belt != null && slot < belt.getSlots(); slot++) {
                    if (!belt.getStackInSlot(slot).isEmpty()) {
                        out.append(belt.getStackInSlot(slot)).append(" on belt ").append(x).append("; ");
                    }
                }
            }
            for (BlockPos pos : new BlockPos[]{HOPPER, CHEST}) {
                if (level.getBlockEntity(pos) instanceof Container container) {
                    for (int i = 0; i < container.getContainerSize(); i++) {
                        if (!container.getItem(i).isEmpty()) {
                            out.append(container.getItem(i)).append(" in ").append(pos.toShortString()).append("; ");
                        }
                    }
                }
            }
            return out.isEmpty() ? "nothing" : out.toString();
        }).join();
    }

    /** Puts items onto a belt segment from above, like a funnel or a player dropping them would. */
    private static void feed(BlockPos pos, ItemStack stack) {
        server().execute(() -> {
            IItemHandler items = server().overworld().getCapability(Capabilities.ItemHandler.BLOCK, pos, Direction.UP);
            if (items != null) {
                ItemHandlerHelper.insertItem(items, stack, false);
            }
        });
    }

    private static String blockAt(BlockPos pos) {
        return BuiltInRegistries.BLOCK.getKey(Minecraft.getInstance().level.getBlockState(pos).getBlock()).toString();
    }

    static void script(ClientPlaytest p) {
        p.step("set up", () -> {
            p.keepFlying = true;
            p.command("gamerule doDaylightCycle false");
            p.command("time set noon");
            p.command("gamemode creative");
            p.command("clear @s");
        }, 20, () -> true);
        p.step("stand by the line", () -> p.command("tp @s 3.5 -59 1.5 0 35"), 20, () -> p.at(3.5, -59, 1.5));

        p.step("the three machines can be crafted", () -> { }, 1, () -> {
            boolean all = server().submit(() -> {
                for (String machine : new String[]{"bit_slicer", "bit_cutter", "bit_crusher"}) {
                    if (server().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("bitsmith_create", machine)).isEmpty()) {
                        return false;
                    }
                }
                return true;
            }).join();
            return p.that("crafting recipes for the slicer, the cutter and the crusher", all);
        });

        // The belt: two shafts across its direction, joined with Create's belt connector.
        p.step("two shafts for the belt", () -> {
            p.command("setblock 0 -60 5 create:shaft[axis=z]");
            p.command("setblock 6 -60 5 create:shaft[axis=z]");
            p.command("give @s create:belt_connector");
        }, 10, () -> {
            p.hotbar(0);
            return p.that("belt connector in hand", BuiltInRegistries.ITEM.getKey(p.held().getItem()).getPath().equals("belt_connector"));
        });
        // Within reach of both shafts: they are six blocks apart.
        p.step("walk up to the shafts", () -> p.command("tp @s 3.5 -60 3.2 0 20"), 10, () -> p.at(3.5, -60, 3.2));
        p.step("right click the first shaft", () -> {
            p.lookAtFace(START, Direction.NORTH);
            p.use();
        }, 6, () -> true);
        p.step("right click the second shaft: the belt is laid", () -> {
            p.lookAtFace(END, Direction.NORTH);
            p.use();
        }, 10, () -> p.that("belt at x 3: " + blockAt(new BlockPos(3, BELT_Y, Z)), blockAt(new BlockPos(3, BELT_Y, Z)).equals("create:belt")));
        p.step("a motor turns the belt", () -> p.command("setblock 0 -60 6 create:creative_motor[facing=north]{ScrollValue:64}"), 10, () -> true);

        // The three machines two blocks above the belt, on one shaft line, turned by a second motor.
        p.step("the slicer, the cutter and the crusher above the belt", () -> {
            p.command("setblock 1 -58 5 bitsmith_create:bit_slicer[facing=east]");
            p.command("setblock 2 -58 5 create:shaft[axis=x]");
            p.command("setblock 3 -58 5 bitsmith_create:bit_cutter[facing=east]");
            p.command("setblock 4 -58 5 create:shaft[axis=x]");
            p.command("setblock 5 -58 5 bitsmith_create:bit_crusher[facing=east]");
            p.command("setblock 0 -58 5 create:creative_motor[facing=east]{ScrollValue:64}");
        }, 10, () -> p.that("machines placed", blockAt(new BlockPos(1, MACHINE_Y, Z)).equals("bitsmith_create:bit_slicer")
                && blockAt(new BlockPos(3, MACHINE_Y, Z)).equals("bitsmith_create:bit_cutter")
                && blockAt(new BlockPos(5, MACHINE_Y, Z)).equals("bitsmith_create:bit_crusher")));
        p.step("a hopper and a chest at the end of the belt", () -> {
            p.command("setblock 7 -61 5 minecraft:hopper[facing=down]");
            p.command("setblock 7 -62 5 minecraft:chest");
            // Items thrown off the end of the belt land against a wall instead of scattering.
            p.command("fill 9 -60 3 9 -58 7 minecraft:glass");
            p.command("fill 7 -60 3 8 -58 3 minecraft:glass");
            p.command("fill 7 -60 7 8 -58 7 minecraft:glass");
        }, 10, () -> true);
        p.shot("the line from the side", () -> {
            p.command("tp @s 3.5 -57.5 0.5 0 25");
            p.look(new Vec3(3.5, -58.5, 5.5));
        }, 20, () -> true, "create_line_overview");
        p.shot("the machines from above", () -> {
            p.command("tp @s 1.5 -55.5 3.0 0 60");
            p.look(new Vec3(3.0, -57.5, 5.5));
        }, 20, () -> true, "create_line_top");

        p.step("one stone block onto the start of the belt", () -> feed(START, new ItemStack(Blocks.STONE)), 25, () -> true);
        p.shot("the slicer at work", () -> p.look(new Vec3(1.5, -59.2, 5.5)), 4, () -> true, "create_line_slicer");
        p.shot("the cutter at work", () -> p.look(new Vec3(3.5, -59.2, 5.5)), 50, () -> true, "create_line_cutter");
        p.shot("the crusher at work", () -> p.look(new Vec3(5.5, -59.2, 5.5)), 50, () -> true, "create_line_crusher");
        p.step("4096 stone bits come out of the line", () -> { }, 1500, () -> {
            int out = bitsOut();
            return p.eq("stone bits out", out, 4096);
        });
        p.step("where the items are", () -> p.note("items: " + whereItemsAre()), 2, () -> true);

        // Up close: each machine over a depot at 16 rpm, one cut every two seconds, caught mid-cut.
        p.step("three machines over depots", () -> {
            String[] machines = {"bit_slicer", "bit_cutter", "bit_crusher"};
            for (int i = 0; i < 3; i++) {
                int x = 12 + i * 3;
                p.command("setblock " + x + " -60 5 create:depot");
                p.command("setblock " + x + " -58 5 bitsmith_create:" + machines[i] + "[facing=east]");
                p.command("setblock " + (x - 1) + " -58 5 create:creative_motor[facing=east]");
            }
            p.command("tp @s 15.5 -59 1.8 0 20");
        }, 20, () -> p.that("bench built", blockAt(new BlockPos(18, MACHINE_Y, Z)).equals("bitsmith_create:bit_crusher")));
        p.step("a block, a slice and a rod onto the depots", () -> {
            BlockState oak = Blocks.OAK_PLANKS.defaultBlockState();
            feed(new BlockPos(12, BELT_Y, Z), new ItemStack(Blocks.OAK_PLANKS));
            feed(new BlockPos(15, BELT_Y, Z), piece("bit_slice", oak));
            feed(new BlockPos(18, BELT_Y, Z), piece("bit_rod", oak));
        }, 15, () -> true);
        p.shot("the tools at the bottom of their stroke", () -> p.look(new Vec3(15.5, -59.2, 5.5)), 1, () -> true, "create_bench_midcut");
        p.step("let the first cuts finish", () -> { }, 60, () -> true);
        String[] names = {"slicer", "cutter", "crusher"};
        for (int i = 0; i < 3; i++) {
            int x = 12 + i * 3;
            int stage = i;
            p.step("an empty depot under the " + names[i], () -> {
                p.command("setblock " + x + " -60 5 air");
                p.command("setblock " + x + " -60 5 create:depot");
                p.command("kill @e[type=item,x=" + x + ",y=-60,z=5,distance=..3]");
            }, 5, () -> true);
            p.step("in front of the " + names[i], () -> {
                p.command("tp @s " + (x + 0.5) + " -59.4 3.2 0 20");
                BlockState oak = Blocks.OAK_PLANKS.defaultBlockState();
                feed(new BlockPos(x, BELT_Y, Z), switch (stage) {
                    case 0 -> new ItemStack(Blocks.OAK_PLANKS);
                    case 1 -> piece("bit_slice", oak);
                    default -> piece("bit_rod", oak);
                });
            }, 15, () -> {
                p.note(names[stage] + " state: " + machineState(new BlockPos(x, MACHINE_Y, Z)));
                return true;
            });
            p.shot("the " + names[i] + " mid-cut", () -> p.look(new Vec3(x + 0.5, -59.0, 5.5)), 1, () -> true, "create_" + names[i] + "_midcut");
            p.shot("the " + names[i] + " a little later", () -> { }, 3, () -> true, "create_" + names[i] + "_midcut_later");
            p.step("the " + names[i] + " finishes", () -> { }, 40, () -> true);
        }

        p.shot("the machines as items", () -> {
            p.command("give @s bitsmith_create:bit_slicer");
            p.command("give @s bitsmith_create:bit_cutter");
            p.command("give @s bitsmith_create:bit_crusher");
            p.hotbar(1);
        }, 10, () -> true, "create_items");

        // JEI: a page per machine, and how an oak slice is made.
        if (ModList.get().isLoaded("jei")) {
            p.step("JEI lists the bit line", () -> { }, 5, () -> {
                String missing = JeiCheck.listsBitLine();
                return p.that("JEI has the slicer, cutter and crusher pages" + (missing == null ? "" : ": " + missing), missing == null);
            });
            p.shot("how an oak slice is made", () -> JeiCheck.showRecipesFor(piece("bit_slice", Blocks.OAK_PLANKS.defaultBlockState())),
                    20, () -> p.that("a JEI page is open", Minecraft.getInstance().screen != null), "create_jei_slice");
            p.step("close JEI", () -> Minecraft.getInstance().setScreen(null), 5, () -> true);
        }
        p.shot("the end of the line", () -> p.look(new Vec3(7.5, -60.5, 5.5)), 10, () -> true, "create_line_end");
    }
}
