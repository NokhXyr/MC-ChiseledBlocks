/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.admin.BlockProfiler;
import com.nokhxyr.bitsmith.admin.CarvedIndex;
import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.core.voxel.Face;
import com.nokhxyr.bitsmith.edit.ChiselLogic;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The {@code /bitsmith} command: carved block index, searches and per block profiling. */
@GameTestHolder(Bitsmith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdminGameTests {
    private static final String TEMPLATE = "empty";
    private static final BlockPos TARGET = new BlockPos(2, 2, 2);
    /** A material no other test uses, so parallel tests never show up in the searches. */
    private static final int AMETHYST = Materials.id(Blocks.AMETHYST_BLOCK.defaultBlockState());
    private static final int STONE = Materials.id(Blocks.STONE.defaultBlockState());

    private AdminGameTests() {
    }

    private static CarvedBlockEntity placeCarved(GameTestHelper helper, BitGrid grid) {
        helper.setBlock(TARGET, BitsmithRegistries.CARVED_BLOCK.get());
        CarvedBlockEntity carved = helper.getBlockEntity(TARGET);
        carved.setGrid(grid);
        return carved;
    }

    private static int run(GameTestHelper helper, String command) {
        CommandSourceStack source = helper.getLevel().getServer().createCommandSourceStack()
                .withLevel(helper.getLevel())
                .withPosition(helper.absolutePos(TARGET).getCenter())
                .withSuppressedOutput();
        try {
            return helper.getLevel().getServer().getCommands().getDispatcher().execute(command, source);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException(command + ": " + e.getMessage(), e);
        }
    }

    @GameTest(template = TEMPLATE)
    public static void indexAndFindFollowCarvedBlocks(GameTestHelper helper) {
        BitGrid grid = new BitGridBuilder()
                .fill(0, 0, 0, 16, 8, 16, AMETHYST)
                .fill(0, 8, 0, 8, 16, 16, STONE)
                .build();
        CarvedBlockEntity carved = placeCarved(helper, grid);

        // The level calls onLoad, which indexes the block, at its next block entity tick.
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(CarvedIndex.loaded(helper.getLevel()).contains(carved), "a placed carved block is indexed");
            helper.assertTrue(run(helper, "bitsmith find radius 2 minecraft:amethyst_block") == 1, "found by radius and material");
            helper.assertTrue(run(helper, "bitsmith find chunks 0 minecraft:amethyst_block") == 1, "found in its own chunk");
            helper.assertTrue(run(helper, "bitsmith find loaded minecraft:amethyst_block") >= 1, "found in the loaded chunks");
            helper.assertTrue(run(helper, "bitsmith find radius 2 minecraft:emerald_block") == 0, "the material filter excludes");
            helper.assertTrue(run(helper, "bitsmith map 1") >= 1, "the map counts it");

            helper.setBlock(TARGET, Blocks.AIR);
            helper.assertFalse(CarvedIndex.loaded(helper.getLevel()).contains(carved), "a removed carved block leaves the index");
            helper.assertTrue(run(helper, "bitsmith find radius 2 minecraft:amethyst_block") == 0, "no longer found");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    @SuppressWarnings("removal")
    public static void profilerAttributesTimeToTheBlock(GameTestHelper helper) {
        placeCarved(helper, BitGrid.filled(STONE).toBuilder().set(BitGrid.index(0, 0, 0), BitGrid.AIR).build());
        BlockPos target = helper.absolutePos(TARGET);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(target.getX() + 0.5, target.getY(), target.getZ() - 1.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BitsmithRegistries.IRON_CHISEL.get()));

        BlockProfiler.start(1000, report -> { });
        ChiselLogic.chisel(player, target, BitGrid.index(7, 15, 0), Face.UP);
        helper.runAfterDelay(2, () -> {
            BlockProfiler.Report report = BlockProfiler.stop();
            helper.assertTrue(report != null && report.ticks() >= 1, "the window counted ticks");
            BlockProfiler.BlockCost cost = report.blocks().stream()
                    .filter(block -> block.pos().equals(target)).findFirst().orElse(null);
            helper.assertTrue(cost != null, "the carved block has a cost");
            helper.assertTrue(cost.calls()[BlockProfiler.Kind.CHISEL.ordinal()] == 1, "one stroke measured");
            helper.assertTrue(cost.calls()[BlockProfiler.Kind.APPLY.ordinal()] == 1, "one batched apply measured");
            helper.assertTrue(cost.totalNanos() > 0 && report.perTick(cost.totalNanos()) > 0, "time per tick is positive");
            helper.assertFalse(BlockProfiler.active, "stopping ends the window");
            helper.succeed();
        });
    }
}
