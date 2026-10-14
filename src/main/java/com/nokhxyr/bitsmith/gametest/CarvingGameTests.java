/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.gametest;

import com.mojang.authlib.GameProfile;
import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.block.CarvedBlock;
import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.compat.cnb.ChiselsAndBitsImport;
import com.nokhxyr.bitsmith.core.edit.ChiselMode;
import com.nokhxyr.bitsmith.core.edit.Symmetry;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.core.voxel.Face;
import com.nokhxyr.bitsmith.edit.BitInventory;
import com.nokhxyr.bitsmith.edit.ChiselLogic;
import com.nokhxyr.bitsmith.edit.EditHistory;
import com.nokhxyr.bitsmith.edit.PlayerSymmetry;
import com.nokhxyr.bitsmith.item.BagContents;
import com.nokhxyr.bitsmith.item.BitBagItem;
import com.nokhxyr.bitsmith.item.BitItem;
import com.nokhxyr.bitsmith.item.ChiselSettings;
import com.nokhxyr.bitsmith.item.PatternItem;
import com.nokhxyr.bitsmith.item.PatternMode;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
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

import java.nio.charset.StandardCharsets;
import java.util.BitSet;
import java.util.HexFormat;
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

    // Patterns

    private static final BitGrid STONE_SLAB = new BitGridBuilder().fill(0, 0, 0, 16, 8, 16, STONE).build();

    private static void useOnTop(ServerPlayer player, GameTestHelper helper, BlockPos relative) {
        BlockPos target = helper.absolutePos(relative);
        Vec3 click = new Vec3(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(click, Direction.UP, target, false)));
    }

    @GameTest(template = TEMPLATE)
    public static void blankPatternCopiesACarving(GameTestHelper helper) {
        placeCarved(helper, STONE_SLAB);
        ServerPlayer player = survival(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BitsmithRegistries.BLANK_PATTERN.get()));
        useOnTop(player, helper, TARGET);

        ItemStack held = player.getMainHandItem();
        helper.assertTrue(held.getItem() instanceof PatternItem, "the blank pattern became a pattern");
        helper.assertTrue(STONE_SLAB.equals(PatternItem.grid(held)), "the pattern holds the carving");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void positivePatternPrintsNextToTheClickedFace(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.DIRT);
        ServerPlayer player = creative(helper, 1.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, PatternItem.of(STONE_SLAB, PatternMode.POSITIVE));
        useOnTop(player, helper, TARGET);

        CarvedBlockEntity printed = helper.getBlockEntity(TARGET.above());
        helper.assertTrue(printed.getGrid().equals(STONE_SLAB), "the carving is printed above the clicked block");
        helper.assertBlockPresent(Blocks.DIRT, TARGET);

        EditHistory.undo(player);
        helper.assertBlockPresent(Blocks.AIR, TARGET.above());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void survivalPrintConsumesBitsOrRefuses(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.DIRT);
        ServerPlayer player = survival(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, PatternItem.of(STONE_SLAB, PatternMode.POSITIVE));

        useOnTop(player, helper, TARGET);
        helper.assertBlockPresent(Blocks.AIR, TARGET.above());

        ItemStack bag = new ItemStack(BitsmithRegistries.BIT_BAG.get());
        BitBagItem.setContents(bag, BagContents.EMPTY.add(Blocks.STONE.defaultBlockState(), 2048 + 10));
        player.getInventory().setItem(5, bag);
        useOnTop(player, helper, TARGET);

        helper.assertBlockPresent(BitsmithRegistries.CARVED_BLOCK.get(), TARGET.above());
        helper.assertTrue(BitBagItem.contents(bag).count(Blocks.STONE.defaultBlockState()) == 10, "2048 bits used");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void negativePatternCarvesItsShape(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, PatternItem.of(STONE_SLAB, PatternMode.NEGATIVE));
        ItemStack bag = new ItemStack(BitsmithRegistries.BIT_BAG.get());
        player.getInventory().setItem(5, bag);
        useOnTop(player, helper, TARGET);

        CarvedBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().get(0, 0, 0) == BitGrid.AIR, "the bottom half is carved out");
        helper.assertTrue(carved.getGrid().get(0, 8, 0) == STONE, "the top half stays");
        helper.assertTrue(BitBagItem.contents(bag).count(Blocks.STONE.defaultBlockState()) == 2048, "the carved bits are given");
        helper.succeed();
    }

    // Advanced tools

    @GameTest(template = TEMPLATE)
    public static void mirrorRepeatsAChiselStroke(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = creative(helper, 1.5);
        PlayerSymmetry.setOnServer(player, Symmetry.X);
        ChiselLogic.chisel(player, helper.absolutePos(TARGET), BitGrid.index(2, 15, 3), Face.UP);

        CarvedBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().get(2, 15, 3) == BitGrid.AIR, "the clicked bit is removed");
        helper.assertTrue(carved.getGrid().get(13, 15, 3) == BitGrid.AIR, "its mirror image is removed too");
        helper.assertTrue(carved.getGrid().filledCount() == BitGrid.VOLUME - 2, "nothing else is touched");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void mirrorRepeatsAPlacementWithinTheBits(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        PlayerSymmetry.setOnServer(player, Symmetry.XZ);
        player.setItemInHand(InteractionHand.MAIN_HAND, BitItem.of(Blocks.OAK_PLANKS.defaultBlockState(), 3));
        BlockPos target = helper.absolutePos(TARGET);
        Vec3 click = new Vec3(target.getX() + 0.1, target.getY() + 1.0, target.getZ() + 0.1);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(click, Direction.UP, target, false)));

        CarvedBlockEntity placed = helper.getBlockEntity(TARGET.above());
        helper.assertTrue(placed.getGrid().filledCount() == 3, "only as many bits as held are placed");
        helper.assertTrue(placed.getGrid().get(1, 0, 1) != BitGrid.AIR, "the clicked bit is placed first");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the 3 bits are used");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void wrenchTurnsACarving(GameTestHelper helper) {
        int planks = Materials.id(Blocks.OAK_PLANKS.defaultBlockState());
        placeCarved(helper, new BitGridBuilder().fill(0, 0, 0, 16, 1, 16, STONE).set(8, 3, 0, planks).build());
        ServerPlayer player = survival(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BitsmithRegistries.WRENCH.get()));
        useOnTop(player, helper, TARGET);

        // Edits on an existing carving are applied at the end of the tick.
        helper.runAfterDelay(1, () -> {
            CarvedBlockEntity carved = helper.getBlockEntity(TARGET);
            helper.assertTrue(carved.getGrid().get(15, 3, 8) == planks, "a quarter turn moves north to east");
            helper.assertTrue(carved.getGrid().get(8, 3, 0) == BitGrid.AIR, "the old place is empty");
            helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "the wrench wears");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void sneakingWrenchMirrorsACarving(GameTestHelper helper) {
        placeCarved(helper, new BitGridBuilder().fill(0, 0, 0, 16, 4, 16, STONE).build());
        ServerPlayer player = survival(helper);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BitsmithRegistries.WRENCH.get()));
        useOnTop(player, helper, TARGET);

        helper.runAfterDelay(1, () -> {
            CarvedBlockEntity carved = helper.getBlockEntity(TARGET);
            helper.assertTrue(carved.getGrid().get(0, 15, 0) == STONE && carved.getGrid().get(0, 0, 0) == BitGrid.AIR,
                    "mirrored upside down across the clicked face's axis");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void sawCutsASliceIntoAnItem(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        ItemStack saw = new ItemStack(BitsmithRegistries.SAW.get());
        saw.set(BitsmithComponents.SAW_THICKNESS.get(), 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, saw);
        useOnTop(player, helper, TARGET);

        CarvedBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().filledCount() == BitGrid.VOLUME - 1024, "a 4 bit slice is cut from the top");
        helper.assertTrue(carved.getGrid().get(0, 11, 0) == STONE && carved.getGrid().get(0, 12, 0) == BitGrid.AIR, "the cut is at y 12");
        ItemStack piece = player.getInventory().items.stream()
                .filter(stack -> stack.is(BitsmithRegistries.CARVED_BLOCK_ITEM.get())).findFirst().orElse(ItemStack.EMPTY);
        BitGrid sliceGrid = piece.get(BitsmithComponents.GRID.get());
        helper.assertTrue(sliceGrid != null && sliceGrid.filledCount() == 1024, "the slice is given as a carved block");
        helper.succeed();
    }

    // Chisels & Bits import

    @GameTest(template = TEMPLATE)
    public static void readsARealChiselsAndBitsBlock(GameTestHelper helper) throws Exception {
        // Block entity data saved by Chisels & Bits 21.1.33 for planks holding one stone bit, and the
        // occupancy Chisels & Bits computed for it (see runClientCnbSource).
        List<String> fixture;
        try (var in = CarvingGameTests.class.getResourceAsStream("/bitsmith_test/cnb_planks_with_stone_bit.txt")) {
            fixture = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
        CompoundTag tag = TagParser.parseTag(fixture.get(0));
        String[] expected = fixture.get(1).split(" ");

        BitGrid grid = ChiselsAndBitsImport.read(tag, helper.getLevel().registryAccess());
        helper.assertTrue(grid != null, "the data is read");
        helper.assertTrue(grid.filledCount() == BitGrid.VOLUME - 3, "3 bits carved out");
        helper.assertTrue(grid.contains(Materials.id(Blocks.OAK_PLANKS.defaultBlockState())), "planks");
        helper.assertTrue(grid.contains(STONE), "the stone bit");
        BitSet occupied = BitSet.valueOf(HexFormat.of().parseHex(expected[4]));
        for (int i = 0; i < BitGrid.VOLUME; i++) {
            helper.assertTrue(occupied.get(i) == (grid.get(i) != BitGrid.AIR), "voxel " + i + " matches Chisels & Bits");
        }
        helper.succeed();
    }

    // Blocks and items

    @GameTest(template = TEMPLATE)
    public static void turnedBlockStateTurnsTheCarving(GameTestHelper helper) {
        // What WorldEdit, structure blocks and Create do: a turned state with the original data.
        BitGrid north = new BitGridBuilder().fill(0, 0, 0, 16, 1, 16, STONE).set(8, 3, 0, STONE).build();
        CarvedBlockEntity original = placeCarved(helper, north);
        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        BlockState east = BitsmithRegistries.CARVED_BLOCK.get().defaultBlockState()
                .setValue(CarvedBlock.FACING, Direction.EAST);
        CarvedBlockEntity turned = new CarvedBlockEntity(helper.absolutePos(TARGET), east);
        turned.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(turned.getGrid().get(15, 3, 8) == STONE, "the carving turned with the block");
        helper.assertTrue(turned.getGrid().get(8, 3, 0) == BitGrid.AIR, "nothing left at the old place");
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
