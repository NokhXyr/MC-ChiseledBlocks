/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.gametest;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.edit.ChiselLogic;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In game checks of carving, run headless with {@code ./gradlew runGameTestServer}. They only run
 * when the {@code bitsmith} game test namespace is enabled, never in a normal game.
 */
@GameTestHolder(Bitsmith.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CarvingGameTests {
    private static final String TEMPLATE = "empty";
    private static final BlockPos TARGET = new BlockPos(2, 2, 2);
    private static final int STONE = Materials.id(Blocks.STONE.defaultBlockState());

    private CarvingGameTests() {
    }

    @SuppressWarnings("removal")
    private static ServerPlayer chiseler(GameTestHelper helper, double distance) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos target = helper.absolutePos(TARGET);
        player.moveTo(target.getX() + 0.5, target.getY(), target.getZ() - distance);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BitsmithRegistries.IRON_CHISEL.get()));
        return player;
    }

    private static CarvedBlockEntity placeCarved(GameTestHelper helper, BitGrid grid) {
        helper.setBlock(TARGET, BitsmithRegistries.CARVED_BLOCK.get());
        CarvedBlockEntity carved = helper.getBlockEntity(TARGET);
        carved.setGrid(grid);
        return carved;
    }

    @GameTest(template = TEMPLATE)
    public static void chiselConvertsPlainBlock(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        int voxel = BitGrid.index(15, 15, 15);
        ChiselLogic.chisel(chiseler(helper, 1.5), helper.absolutePos(TARGET), voxel);

        helper.assertBlockPresent(BitsmithRegistries.CARVED_BLOCK.get(), TARGET);
        CarvedBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().get(voxel) == BitGrid.AIR, "chiseled bit should be air");
        helper.assertTrue(carved.getGrid().filledCount() == BitGrid.VOLUME - 1, "exactly one bit removed");
        helper.assertTrue(carved.getGrid().contains(STONE), "material is the converted block");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void editsApplyOncePerTickWithinBudget(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = chiseler(helper, 1.5);
        BlockPos target = helper.absolutePos(TARGET);
        for (int x = 0; x < 6; x++) {
            ChiselLogic.chisel(player, target, BitGrid.index(x, 15, 0));
        }
        CarvedBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().filledCount() == BitGrid.VOLUME - 1,
                "only the conversion is visible before the tick ends");

        helper.runAfterDelay(1, () -> {
            // 4 actions per tick: the conversion plus 3 queued edits; the last 2 were refused.
            helper.assertTrue(carved.getGrid().filledCount() == BitGrid.VOLUME - 4,
                    "expected 4 bits removed, got " + (BitGrid.VOLUME - carved.getGrid().filledCount()));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void playerOutOfReachIsRefused(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ChiselLogic.chisel(chiseler(helper, 20), helper.absolutePos(TARGET), 0);
        helper.assertBlockPresent(Blocks.STONE, TARGET);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void emptiedBlockBecomesAir(GameTestHelper helper) {
        int voxel = BitGrid.index(4, 4, 4);
        CarvedBlockEntity carved = placeCarved(helper, new BitGridBuilder().set(voxel, STONE).build());
        carved.edit(builder -> builder.set(voxel, BitGrid.AIR));
        helper.runAfterDelay(1, () -> {
            helper.assertBlockPresent(Blocks.AIR, TARGET);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void uniformBlockRevertsToPlainBlock(GameTestHelper helper) {
        int voxel = BitGrid.index(7, 7, 7);
        CarvedBlockEntity carved = placeCarved(helper, BitGrid.filled(STONE).toBuilder().set(voxel, BitGrid.AIR).build());
        carved.edit(builder -> builder.set(voxel, STONE));
        helper.runAfterDelay(1, () -> {
            helper.assertBlockPresent(Blocks.STONE, TARGET);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void gridSurvivesSaveAndLoad(GameTestHelper helper) {
        BitGrid grid = new BitGridBuilder()
                .fill(0, 0, 0, 16, 8, 16, STONE)
                .fill(0, 8, 0, 8, 16, 16, Materials.id(Blocks.GLASS.defaultBlockState()))
                .build();
        CarvedBlockEntity carved = placeCarved(helper, grid);
        CompoundTag tag = carved.saveWithoutMetadata(helper.getLevel().registryAccess());

        BlockState state = BitsmithRegistries.CARVED_BLOCK.get().defaultBlockState();
        CarvedBlockEntity loaded = new CarvedBlockEntity(helper.absolutePos(TARGET), state);
        loaded.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(loaded.getGrid().equals(grid), "grid content survives a save");
        helper.assertTrue(loaded.getGrid() == carved.getGrid(), "identical grids are shared after load");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void shapeFollowsGrid(GameTestHelper helper) {
        BitGrid slab = new BitGridBuilder().fill(0, 0, 0, 16, 8, 16, STONE).build();
        placeCarved(helper, slab);
        BlockPos pos = helper.absolutePos(TARGET);
        BlockState state = helper.getLevel().getBlockState(pos);

        VoxelShape shape = state.getShape(helper.getLevel(), pos);
        VoxelShape collision = state.getCollisionShape(helper.getLevel(), pos, CollisionContext.empty());
        helper.assertTrue(Math.abs(shape.max(net.minecraft.core.Direction.Axis.Y) - 0.5) < 1e-9, "outline is a half slab");
        helper.assertTrue(Math.abs(collision.max(net.minecraft.core.Direction.Axis.Y) - 0.5) < 1e-9, "collision is a half slab");
        helper.assertTrue(shape == state.getShape(helper.getLevel(), pos), "shape is cached, not rebuilt");
        helper.succeed();
    }
}
