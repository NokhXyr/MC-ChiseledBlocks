/*
 * Chiseled Blocks: Create
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.create.gametest;

import com.nokhxyr.chiseledblocks.create.PieceItem;
import com.nokhxyr.chiseledblocks.create.ChiseledBlocksCreate;
import com.nokhxyr.chiseledblocks.create.LineMachineBlockEntity;
import com.nokhxyr.chiseledblocks.create.LineStage;
import com.nokhxyr.chiseledblocks.item.PixelItem;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.function.Predicate;

/**
 * The pixel line machines on Create depots, on a headless server with Create: each cuts what sits
 * below it into 16 pieces once a creative motor turns it, and leaves it alone when nothing turns
 * it. {@code ./gradlew :chiseledblocks-create:runGameTestServer}.
 */
@GameTestHolder(ChiseledBlocksCreate.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LineGameTests {
    private static final String TEMPLATE = "empty";
    private static final BlockPos DEPOT = new BlockPos(2, 1, 2);
    /** Two blocks up: Create's depots and belts look for processing machines there, like the press. */
    private static final BlockPos MACHINE = DEPOT.above(2);

    private LineGameTests() {
    }

    private static Block create(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path));
    }

    /** A depot holding {@code input}, the machine above it, and a motor turning it if asked. */
    private static IItemHandler build(GameTestHelper helper, LineStage stage, ItemStack input, boolean powered) {
        helper.setBlock(DEPOT, create("depot").defaultBlockState());
        helper.setBlock(MACHINE, ChiseledBlocksCreate.MACHINES.get(stage).get().defaultBlockState()
                .setValue(HorizontalKineticBlock.HORIZONTAL_FACING, Direction.EAST));
        if (powered) {
            helper.setBlock(MACHINE.west(), create("creative_motor").defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.EAST));
        }
        IItemHandler depot = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(DEPOT), Direction.UP);
        helper.assertTrue(depot != null, "the depot takes items");
        depot.insertItem(0, input, false);
        return depot;
    }

    private static int count(IItemHandler depot, Predicate<ItemStack> wanted) {
        int total = 0;
        for (int slot = 0; slot < depot.getSlots(); slot++) {
            ItemStack stack = depot.getStackInSlot(slot);
            if (wanted.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void slicerCutsABlockIntoSixteenSlices(GameTestHelper helper) {
        IItemHandler depot = build(helper, LineStage.SLICER, new ItemStack(Blocks.STONE), true);
        helper.succeedWhen(() -> helper.assertTrue(count(depot, stack -> stack.is(ChiseledBlocksCreate.SLICE.get())
                && PieceItem.material(stack) == Blocks.STONE.defaultBlockState()) == 16, "16 stone slices on the depot"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void cutterCutsASliceIntoSixteenRods(GameTestHelper helper) {
        IItemHandler depot = build(helper, LineStage.CUTTER,
                PieceItem.of(ChiseledBlocksCreate.SLICE.get(), Blocks.OAK_PLANKS.defaultBlockState(), 1), true);
        helper.succeedWhen(() -> helper.assertTrue(count(depot, stack -> stack.is(ChiseledBlocksCreate.ROD.get())
                && PieceItem.material(stack) == Blocks.OAK_PLANKS.defaultBlockState()) == 16, "16 oak rods on the depot"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void crusherCrushesARodIntoSixteenBits(GameTestHelper helper) {
        IItemHandler depot = build(helper, LineStage.CRUSHER,
                PieceItem.of(ChiseledBlocksCreate.ROD.get(), Blocks.GOLD_BLOCK.defaultBlockState(), 2), true);
        helper.succeedWhen(() -> helper.assertTrue(count(depot, stack -> stack.getItem() instanceof PixelItem
                && PixelItem.material(stack) == Blocks.GOLD_BLOCK.defaultBlockState()) == 32, "two rods give 32 gold bits"));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void stoppedMachineLeavesItemsAlone(GameTestHelper helper) {
        IItemHandler depot = build(helper, LineStage.SLICER, new ItemStack(Blocks.STONE, 3), false);
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(count(depot, stack -> stack.is(Blocks.STONE.asItem())) == 3, "the stone is untouched");
            helper.assertTrue(count(depot, stack -> stack.is(ChiseledBlocksCreate.SLICE.get())) == 0, "no slices");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void machinesOnlyTakeTheirOwnInput(GameTestHelper helper) {
        // A slice under the slicer: not its input, it stays a slice.
        IItemHandler depot = build(helper, LineStage.SLICER,
                PieceItem.of(ChiseledBlocksCreate.SLICE.get(), Blocks.STONE.defaultBlockState(), 1), true);
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(count(depot, stack -> stack.is(ChiseledBlocksCreate.SLICE.get())) == 1, "the slice is left as it is");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 120)
    public static void machineStopsWhenItsItemIsTakenAway(GameTestHelper helper) {
        IItemHandler depot = build(helper, LineStage.SLICER, new ItemStack(Blocks.STONE), true);
        helper.runAfterDelay(10, () -> {
            LineMachineBlockEntity machine = helper.getBlockEntity(MACHINE);
            helper.assertTrue(machine.running(), "the slicer has started on the stone");
            depot.extractItem(0, 64, false);
        });
        helper.runAfterDelay(30, () -> {
            LineMachineBlockEntity machine = helper.getBlockEntity(MACHINE);
            helper.assertFalse(machine.running(), "with nothing left to cut, the slicer stops instead of running on empty");
            helper.succeed();
        });
    }
}
