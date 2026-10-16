/*
 * Bitsmith: Create
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.create.gametest;

import com.nokhxyr.bitsmith.create.BitsmithCreate;
import com.nokhxyr.bitsmith.create.MechanicalBitMillBlockEntity;
import com.nokhxyr.bitsmith.create.MillContents;
import com.nokhxyr.bitsmith.item.BitItem;
import com.simibubi.create.content.kinetics.base.RotatedPillarKineticBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * The Mechanical Bit Mill on a headless server with Create: it stays idle without rotation, cuts
 * blocks once a Create motor drives it, works through its item handler and keeps its contents
 * when broken. {@code ./gradlew :bitsmith-create:runGameTestServer}.
 */
@GameTestHolder(BitsmithCreate.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MillGameTests {
    private static final String TEMPLATE = "empty";
    private static final BlockPos MILL = new BlockPos(2, 2, 2);

    private MillGameTests() {
    }

    private static IItemHandler placeMill(GameTestHelper helper) {
        helper.setBlock(MILL, BitsmithCreate.MILL.get().defaultBlockState().setValue(RotatedPillarKineticBlock.AXIS, Direction.Axis.X));
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(MILL), Direction.UP);
    }

    /** A Create creative motor west of the mill, its shaft facing the mill (16 rpm by default). */
    private static void drive(GameTestHelper helper) {
        Block motor = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", "creative_motor"));
        helper.setBlock(MILL.west(), motor.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.EAST));
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 100)
    public static void millStaysIdleWithoutRotation(GameTestHelper helper) {
        IItemHandler items = placeMill(helper);
        helper.assertTrue(items != null, "the mill exposes an item handler");
        helper.assertTrue(items.insertItem(0, new ItemStack(Blocks.STONE, 4), false).isEmpty(), "stone goes in");
        helper.assertTrue(items.insertItem(0, new ItemStack(Blocks.TORCH, 4), false).getCount() == 4, "torches cannot be cut");
        helper.runAfterDelay(80, () -> {
            MechanicalBitMillBlockEntity mill = helper.getBlockEntity(MILL);
            helper.assertTrue(mill.bits() == 0 && mill.input().getCount() == 4, "no rotation, no bits: " + mill.bits());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 400)
    public static void millCutsBlocksWhenDriven(GameTestHelper helper) {
        IItemHandler items = placeMill(helper);
        drive(helper);
        items.insertItem(0, new ItemStack(Blocks.STONE, 3), false);
        helper.succeedWhen(() -> {
            MechanicalBitMillBlockEntity mill = helper.getBlockEntity(MILL);
            helper.assertTrue(Math.abs(mill.getSpeed()) > 0, "the motor turns the mill");
            helper.assertTrue(mill.bits() >= MechanicalBitMillBlockEntity.BITS_PER_BLOCK, "a block cut: " + mill.bits());
            ItemStack out = items.extractItem(1, 64, true);
            helper.assertTrue(out.getItem() instanceof BitItem && out.getCount() == 64
                    && BitItem.material(out) == Blocks.STONE.defaultBlockState(), "stone bits come out");
            helper.assertTrue(items.extractItem(0, 64, true).isEmpty(), "blocks cannot be pulled back by machines");
        });
    }

    @GameTest(template = TEMPLATE)
    public static void millKeepsItsContentsWhenBroken(GameTestHelper helper) {
        placeMill(helper);
        MechanicalBitMillBlockEntity mill = helper.getBlockEntity(MILL);
        mill.insert(new ItemStack(Blocks.OAK_PLANKS, 10));
        BlockPos pos = helper.absolutePos(MILL);
        BlockState state = helper.getLevel().getBlockState(pos);
        List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), pos, mill);
        helper.assertTrue(drops.size() == 1 && drops.get(0).is(BitsmithCreate.MILL_ITEM.get()), "the mill drops itself only");
        MillContents kept = drops.get(0).get(BitsmithCreate.MILL_CONTENTS.get());
        helper.assertTrue(kept != null && kept.input().is(Blocks.OAK_PLANKS.asItem()) && kept.input().getCount() == 10,
                "with its ten planks inside");
        helper.succeed();
    }
}
