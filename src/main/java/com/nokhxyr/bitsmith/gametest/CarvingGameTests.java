/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.gametest;

import com.mojang.authlib.GameProfile;
import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.edit.ChiselMode;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.core.voxel.Face;
import com.nokhxyr.bitsmith.edit.BitInventory;
import com.nokhxyr.bitsmith.edit.ChiselLogic;
import com.nokhxyr.bitsmith.edit.EditHistory;
import com.nokhxyr.bitsmith.item.BitBagItem;
import com.nokhxyr.bitsmith.item.BitItem;
import com.nokhxyr.bitsmith.item.ChiselSettings;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.UUID;

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

    // Players

    @SuppressWarnings("removal")
    private static ServerPlayer creative(GameTestHelper helper, double distance) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        equip(helper, player, distance);
        return player;
    }

    private static ServerPlayer survival(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "bitsmith-test"));
        equip(helper, player, 1.5);
        return player;
    }

    private static void equip(GameTestHelper helper, ServerPlayer player, double distance) {
        BlockPos target = helper.absolutePos(TARGET);
        player.moveTo(target.getX() + 0.5, target.getY(), target.getZ() - distance);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BitsmithRegistries.IRON_CHISEL.get()));
    }

    private static void setMode(ServerPlayer player, ChiselMode mode, int size) {
        player.getMainHandItem().set(BitsmithComponents.CHISEL_SETTINGS.get(), new ChiselSettings(mode, size));
    }

    private static CarvedBlockEntity placeCarved(GameTestHelper helper, BitGrid grid) {
        helper.setBlock(TARGET, BitsmithRegistries.CARVED_BLOCK.get());
        CarvedBlockEntity carved = helper.getBlockEntity(TARGET);
        carved.setGrid(grid);
        return carved;
    }

    private static int bitsInInventory(ServerPlayer player, BlockState material) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof BitItem && BitItem.material(stack) == material) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // Chiseling

    @GameTest(template = TEMPLATE)
    public static void chiselConvertsPlainBlock(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        int voxel = BitGrid.index(15, 15, 15);
        ChiselLogic.chisel(creative(helper, 1.5), helper.absolutePos(TARGET), voxel, Face.UP);

        helper.assertBlockPresent(BitsmithRegistries.CARVED_BLOCK.get(), TARGET);
        CarvedBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().get(voxel) == BitGrid.AIR, "chiseled bit should be air");
        helper.assertTrue(carved.getGrid().filledCount() == BitGrid.VOLUME - 1, "exactly one bit removed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void editsApplyOncePerTickWithinBudget(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = creative(helper, 1.5);
        BlockPos target = helper.absolutePos(TARGET);
        for (int x = 0; x < 6; x++) {
            ChiselLogic.chisel(player, target, BitGrid.index(x, 15, 0), Face.UP);
        }
        CarvedBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().filledCount() == BitGrid.VOLUME - 1,
                "only the conversion is visible before the tick ends");

        helper.runAfterDelay(1, () -> {
            // 4 strokes per tick: the conversion plus 3 queued edits; the last 2 were refused.
            helper.assertTrue(carved.getGrid().filledCount() == BitGrid.VOLUME - 4,
                    "expected 4 bits removed, got " + (BitGrid.VOLUME - carved.getGrid().filledCount()));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void cubeModeRemovesAlignedCube(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = creative(helper, 1.5);
        setMode(player, ChiselMode.CUBE, 4);
        ChiselLogic.chisel(player, helper.absolutePos(TARGET), BitGrid.index(5, 13, 6), Face.UP);

        CarvedBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().filledCount() == BitGrid.VOLUME - 64, "a 4x4x4 cube is removed");
        helper.assertTrue(carved.getGrid().get(4, 12, 4) == BitGrid.AIR, "cube is aligned on the size grid");
        helper.assertTrue(carved.getGrid().get(8, 12, 4) == STONE, "cube does not overflow");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void playerOutOfReachIsRefused(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ChiselLogic.chisel(creative(helper, 20), helper.absolutePos(TARGET), 0, Face.UP);
        helper.assertBlockPresent(Blocks.STONE, TARGET);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void ironChiselCannotCutObsidian(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.OBSIDIAN);
        ChiselLogic.chisel(survival(helper), helper.absolutePos(TARGET), 0, Face.UP);
        helper.assertBlockPresent(Blocks.OBSIDIAN, TARGET);
        helper.succeed();
    }

    // Survival

    @GameTest(template = TEMPLATE)
    public static void survivalChiselGivesBitsAndWearsTheTool(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        setMode(player, ChiselMode.CUBE, 2);
        ChiselLogic.chisel(player, helper.absolutePos(TARGET), BitGrid.index(0, 15, 0), Face.UP);

        helper.assertTrue(bitsInInventory(player, Blocks.STONE.defaultBlockState()) == 8, "8 stone bits given");
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "one use of durability per stroke");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void bitBagCollectsChiseledBits(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        ItemStack bag = new ItemStack(BitsmithRegistries.BIT_BAG.get());
        player.getInventory().setItem(5, bag);
        setMode(player, ChiselMode.CUBE, 4);
        ChiselLogic.chisel(player, helper.absolutePos(TARGET), BitGrid.index(0, 15, 0), Face.UP);

        helper.assertTrue(BitBagItem.contents(bag).count(Blocks.STONE.defaultBlockState()) == 64, "64 bits in the bag");
        helper.assertTrue(bitsInInventory(player, Blocks.STONE.defaultBlockState()) == 0, "nothing loose in the inventory");
        helper.succeed();
    }

    // Placing

    @GameTest(template = TEMPLATE)
    public static void bitPlacedOnBlockTopCreatesCarvedBlock(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, BitItem.of(Blocks.OAK_PLANKS.defaultBlockState(), 3));
        BlockPos target = helper.absolutePos(TARGET);
        Vec3 click = new Vec3(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(click, Direction.UP, target, false)));

        BlockPos above = TARGET.above();
        helper.assertBlockPresent(BitsmithRegistries.CARVED_BLOCK.get(), above);
        CarvedBlockEntity carved = helper.getBlockEntity(above);
        helper.assertTrue(carved.getGrid().get(8, 0, 8) == Materials.id(Blocks.OAK_PLANKS.defaultBlockState()),
                "the bit sits on the clicked face");
        helper.assertTrue(carved.getGrid().filledCount() == 1, "exactly one bit placed");
        helper.assertTrue(player.getMainHandItem().getCount() == 2, "one bit consumed");
        helper.succeed();
    }

    // Undo and redo

    @GameTest(template = TEMPLATE)
    public static void undoAndRedoAChiselStroke(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = creative(helper, 1.5);
        ChiselLogic.chisel(player, helper.absolutePos(TARGET), BitGrid.index(1, 15, 1), Face.UP);
        helper.assertBlockPresent(BitsmithRegistries.CARVED_BLOCK.get(), TARGET);

        EditHistory.undo(player);
        helper.assertBlockPresent(Blocks.STONE, TARGET);

        EditHistory.redo(player);
        helper.assertBlockPresent(BitsmithRegistries.CARVED_BLOCK.get(), TARGET);
        CarvedBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().filledCount() == BitGrid.VOLUME - 1, "redo removes the same bit again");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void survivalUndoTakesTheBitsBack(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        setMode(player, ChiselMode.CUBE, 2);
        ChiselLogic.chisel(player, helper.absolutePos(TARGET), BitGrid.index(0, 15, 0), Face.UP);
        BlockState stone = Blocks.STONE.defaultBlockState();
        helper.assertTrue(bitsInInventory(player, stone) == 8, "8 bits gained");

        EditHistory.undo(player);
        helper.assertBlockPresent(Blocks.STONE, TARGET);
        helper.assertTrue(bitsInInventory(player, stone) == 0, "the 8 bits went back into the block");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void survivalUndoNeedsTheBits(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        setMode(player, ChiselMode.CUBE, 2);
        ChiselLogic.chisel(player, helper.absolutePos(TARGET), BitGrid.index(0, 15, 0), Face.UP);
        BitInventory.take(player, Blocks.STONE.defaultBlockState(), 8);

        EditHistory.undo(player);
        helper.assertBlockPresent(BitsmithRegistries.CARVED_BLOCK.get(), TARGET);
        helper.assertTrue(EditHistory.of(player).undoSize() == 1, "the step stays available");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void undoNeverOverwritesALaterChange(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = creative(helper, 1.5);
        ChiselLogic.chisel(player, helper.absolutePos(TARGET), 0, Face.UP);
        helper.setBlock(TARGET, Blocks.DIRT);

        EditHistory.undo(player);
        helper.assertBlockPresent(Blocks.DIRT, TARGET);
        helper.assertTrue(EditHistory.of(player).undoSize() == 0, "the outdated step is dropped");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void undoingAPlacementGivesTheBitBack(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, BitItem.of(Blocks.OAK_PLANKS.defaultBlockState(), 3));
        BlockPos target = helper.absolutePos(TARGET);
        Vec3 click = new Vec3(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(click, Direction.UP, target, false)));

        EditHistory.undo(player);
        helper.assertBlockPresent(Blocks.AIR, TARGET.above());
        helper.assertTrue(bitsInInventory(player, Blocks.OAK_PLANKS.defaultBlockState()) == 3, "the bit is back");
        helper.succeed();
    }

    // Blocks and items

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
    public static void brokenCarvedBlockDropsItsCarving(GameTestHelper helper) {
        BitGrid slab = new BitGridBuilder().fill(0, 0, 0, 16, 8, 16, STONE).build();
        CarvedBlockEntity carved = placeCarved(helper, slab);
        BlockPos pos = helper.absolutePos(TARGET);
        List<ItemStack> drops = Block.getDrops(helper.getLevel().getBlockState(pos), helper.getLevel(), pos, carved);

        helper.assertTrue(drops.size() == 1, "one item dropped");
        ItemStack drop = drops.get(0);
        helper.assertTrue(drop.is(BitsmithRegistries.CARVED_BLOCK_ITEM.get()), "the drop is a carved block item");
        helper.assertTrue(slab.equals(drop.get(BitsmithComponents.GRID.get())), "the drop keeps the carving");

        CarvedBlockEntity placed = new CarvedBlockEntity(pos, BitsmithRegistries.CARVED_BLOCK.get().defaultBlockState());
        placed.applyComponentsFromItemStack(drop);
        helper.assertTrue(placed.getGrid() == carved.getGrid(), "placing the item restores the same grid");
        helper.succeed();
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
        helper.assertTrue(Math.abs(shape.max(Direction.Axis.Y) - 0.5) < 1e-9, "outline is a half slab");
        helper.assertTrue(Math.abs(collision.max(Direction.Axis.Y) - 0.5) < 1e-9, "collision is a half slab");
        helper.assertTrue(shape == state.getShape(helper.getLevel(), pos), "shape is cached, not rebuilt");
        helper.succeed();
    }
}
