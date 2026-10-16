/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.gametest;

import com.mojang.authlib.GameProfile;
import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.block.ChiseledBlock;
import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import com.nokhxyr.chiseledblocks.block.GridNbt;
import com.nokhxyr.chiseledblocks.block.Materials;
import com.nokhxyr.chiseledblocks.compat.cnb.ChiselsAndBitsImport;
import com.nokhxyr.chiseledblocks.core.edit.ChiselMode;
import com.nokhxyr.chiseledblocks.core.edit.PlacementMode;
import com.nokhxyr.chiseledblocks.core.edit.Symmetry;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGridBuilder;
import com.nokhxyr.chiseledblocks.core.voxel.Face;
import com.nokhxyr.chiseledblocks.edit.PixelInventory;
import com.nokhxyr.chiseledblocks.edit.ChiselLogic;
import com.nokhxyr.chiseledblocks.edit.EditHistory;
import com.nokhxyr.chiseledblocks.edit.PlayerSymmetry;
import com.nokhxyr.chiseledblocks.edit.ShapePlacement;
import com.nokhxyr.chiseledblocks.item.BagContents;
import com.nokhxyr.chiseledblocks.item.PixelBagItem;
import com.nokhxyr.chiseledblocks.item.PixelItem;
import com.nokhxyr.chiseledblocks.item.ChiselSettings;
import com.nokhxyr.chiseledblocks.item.CreativePixelBagItem;
import com.nokhxyr.chiseledblocks.item.PatternItem;
import com.nokhxyr.chiseledblocks.item.PatternMode;
import com.nokhxyr.chiseledblocks.network.GridDeltaPayload;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksComponents;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksRegistries;
import com.nokhxyr.chiseledblocks.registry.ChiseledItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
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
 * when the {@code chiseledblocks} game test namespace is enabled, never in a normal game.
 */
@GameTestHolder(ChiseledBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CarvingGameTests {
    private static final String TEMPLATE = "empty";
    private static final BlockPos TARGET = new BlockPos(2, 2, 2);
    private static final int STONE = Materials.id(Blocks.STONE.defaultBlockState());

    /** The item of carvings made mostly of stone, looked up by its id as other mods would. */
    private static Item carvedStone() {
        return BuiltInRegistries.ITEM.get(ChiseledBlocks.id("chiseled/stone"));
    }

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
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "chiseledblocks-test"));
        equip(helper, player, 1.5);
        return player;
    }

    private static void equip(GameTestHelper helper, ServerPlayer player, double distance) {
        BlockPos target = helper.absolutePos(TARGET);
        player.moveTo(target.getX() + 0.5, target.getY(), target.getZ() - distance);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ChiseledBlocksRegistries.IRON_CHISEL.get()));
    }

    private static void setMode(ServerPlayer player, ChiselMode mode, int size) {
        player.getMainHandItem().set(ChiseledBlocksComponents.CHISEL_SETTINGS.get(), new ChiselSettings(mode, size));
    }

    private static ChiseledBlockEntity placeCarved(GameTestHelper helper, PixelGrid grid) {
        helper.setBlock(TARGET, ChiseledBlocksRegistries.CHISELED_BLOCK.get());
        ChiseledBlockEntity carved = helper.getBlockEntity(TARGET);
        carved.setGrid(grid);
        return carved;
    }

    private static int bitsInInventory(ServerPlayer player, BlockState material) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.getItem() instanceof PixelItem && PixelItem.material(stack) == material) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // Chiseling

    @GameTest(template = TEMPLATE)
    public static void chiselConvertsPlainBlock(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        int voxel = PixelGrid.index(15, 15, 15);
        ChiselLogic.chisel(creative(helper, 1.5), helper.absolutePos(TARGET), voxel, Face.UP);

        helper.assertBlockPresent(ChiseledBlocksRegistries.CHISELED_BLOCK.get(), TARGET);
        ChiseledBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().get(voxel) == PixelGrid.AIR, "chiseled bit should be air");
        helper.assertTrue(carved.getGrid().filledCount() == PixelGrid.VOLUME - 1, "exactly one bit removed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void editsApplyOncePerTickWithinBudget(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = creative(helper, 1.5);
        BlockPos target = helper.absolutePos(TARGET);
        for (int x = 0; x < 6; x++) {
            ChiselLogic.chisel(player, target, PixelGrid.index(x, 15, 0), Face.UP);
        }
        ChiseledBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().filledCount() == PixelGrid.VOLUME - 1,
                "only the conversion is visible before the tick ends");

        helper.runAfterDelay(1, () -> {
            // 4 strokes per tick: the conversion plus 3 queued edits; the last 2 were refused.
            helper.assertTrue(carved.getGrid().filledCount() == PixelGrid.VOLUME - 4,
                    "expected 4 bits removed, got " + (PixelGrid.VOLUME - carved.getGrid().filledCount()));
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void cubeModeRemovesAlignedCube(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = creative(helper, 1.5);
        setMode(player, ChiselMode.CUBE, 4);
        ChiselLogic.chisel(player, helper.absolutePos(TARGET), PixelGrid.index(5, 13, 6), Face.UP);

        ChiseledBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().filledCount() == PixelGrid.VOLUME - 64, "a 4x4x4 cube is removed");
        helper.assertTrue(carved.getGrid().get(4, 12, 4) == PixelGrid.AIR, "cube is aligned on the size grid");
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
    public static void refusedStrokeOfAFakePlayerDoesNotThrow(GameTestHelper helper) {
        // Machines of other mods hold tools as fake players: a refused stroke must not try to
        // correct a client they do not have.
        placeCarved(helper, STONE_SLAB);
        FakePlayer machine = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "chiseledblocks-machine"));
        machine.moveTo(helper.absolutePos(TARGET).getX() + 30, helper.absolutePos(TARGET).getY(), helper.absolutePos(TARGET).getZ());
        machine.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ChiseledBlocksRegistries.IRON_CHISEL.get()));
        ChiselLogic.chisel(machine, helper.absolutePos(TARGET), 0, Face.UP);
        helper.assertTrue(((ChiseledBlockEntity) helper.getBlockEntity(TARGET)).getGrid().equals(STONE_SLAB), "the stroke was refused");
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
        ChiselLogic.chisel(player, helper.absolutePos(TARGET), PixelGrid.index(0, 15, 0), Face.UP);

        helper.assertTrue(bitsInInventory(player, Blocks.STONE.defaultBlockState()) == 8, "8 stone bits given");
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "one use of durability per stroke");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void bitBagCollectsChiseledBits(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        ItemStack bag = new ItemStack(ChiseledBlocksRegistries.PIXEL_BAG.get());
        player.getInventory().setItem(5, bag);
        setMode(player, ChiselMode.CUBE, 4);
        ChiselLogic.chisel(player, helper.absolutePos(TARGET), PixelGrid.index(0, 15, 0), Face.UP);

        helper.assertTrue(PixelBagItem.contents(bag).count(Blocks.STONE.defaultBlockState()) == 64, "64 bits in the bag");
        helper.assertTrue(bitsInInventory(player, Blocks.STONE.defaultBlockState()) == 0, "nothing loose in the inventory");
        helper.succeed();
    }

    // Creative bit bag

    @GameTest(template = TEMPLATE)
    public static void creativeBagSwallowsChiseledBitsAndRemembersTheMaterial(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        ItemStack bag = new ItemStack(ChiseledBlocksRegistries.CREATIVE_PIXEL_BAG.get());
        player.getInventory().setItem(5, bag);
        setMode(player, ChiselMode.CUBE, 4);
        ChiselLogic.chisel(player, helper.absolutePos(TARGET), PixelGrid.index(0, 15, 0), Face.UP);

        helper.assertTrue(bitsInInventory(player, Blocks.STONE.defaultBlockState()) == 0, "the chiseled bits are swallowed");
        helper.assertTrue(PixelBagItem.contents(bag).count(Blocks.STONE.defaultBlockState()) > 0, "stone is now in its list");
        helper.assertTrue(PixelInventory.count(player, Blocks.DIAMOND_BLOCK.defaultBlockState()) >= 1 << 30,
                "it pays for any material");
        helper.assertTrue(PixelInventory.take(player, Blocks.DIAMOND_BLOCK.defaultBlockState(), 4096) == 4096,
                "a whole block of diamond can be taken");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void creativeBagPlacesWithoutRunningOut(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        ItemStack bag = new ItemStack(ChiseledBlocksRegistries.CREATIVE_PIXEL_BAG.get());
        CreativePixelBagItem.pick(bag, Blocks.GOLD_BLOCK.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, bag);
        BagContents before = PixelBagItem.contents(bag);
        BlockPos target = helper.absolutePos(TARGET);
        for (int i = 0; i < 3; i++) {
            Vec3 click = new Vec3(target.getX() + 0.1 + i * 0.3, target.getY() + 1.0, target.getZ() + 0.5);
            player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(click, Direction.UP, target, false)));
        }
        // Edits after the first one are applied with the tick's batch.
        helper.runAfterDelay(1, () -> {
            ChiseledBlockEntity carved = helper.getBlockEntity(TARGET.above());
            helper.assertTrue(carved.getGrid().filledCount() == 3, "three gold bits placed in survival: " + carved.getGrid().filledCount());
            helper.assertTrue(PixelBagItem.contents(bag).equals(before), "the bag is unchanged");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void creativeBagHasNoRecipe(GameTestHelper helper) {
        boolean craftable = helper.getLevel().getRecipeManager().getRecipes().stream()
                .anyMatch(recipe -> recipe.value().getResultItem(helper.getLevel().registryAccess())
                        .is(ChiseledBlocksRegistries.CREATIVE_PIXEL_BAG.get()));
        helper.assertFalse(craftable, "no recipe makes the creative bit bag");
        helper.succeed();
    }

    // Bulk bit production belongs to the Create addon only

    @GameTest(template = TEMPLATE)
    public static void chiseledblocksAloneCannotMassProduceBits(GameTestHelper helper) {
        List<String> machines = BuiltInRegistries.BLOCK.keySet().stream()
                .filter(id -> id.getNamespace().equals(ChiseledBlocks.MOD_ID) && id.getPath().contains("mill"))
                .map(Object::toString).toList();
        helper.assertTrue(machines.isEmpty(), "no bit machine in Chiseled Blocks itself: " + machines);
        boolean bitRecipe = helper.getLevel().getRecipeManager().getRecipes().stream()
                .filter(recipe -> recipe.id().getNamespace().equals(ChiseledBlocks.MOD_ID))
                .anyMatch(recipe -> recipe.value().getResultItem(helper.getLevel().registryAccess()).getItem() instanceof PixelItem);
        helper.assertFalse(bitRecipe, "no Chiseled Blocks recipe makes bits: they come from the chisel");
        helper.succeed();
    }

    // Putting bits back

    @GameTest(template = TEMPLATE)
    public static void bitsGoBackIntoTheBag(GameTestHelper helper) {
        ServerPlayer player = survival(helper);
        BlockState stone = Blocks.STONE.defaultBlockState();
        ItemStack bag = new ItemStack(ChiseledBlocksRegistries.PIXEL_BAG.get());
        SimpleContainer container = new SimpleContainer(2);
        container.setItem(0, bag);
        container.setItem(1, PixelItem.of(stone, 40));
        PixelBagItem bagItem = (PixelBagItem) bag.getItem();

        // Bits on the cursor, right clicked onto the bag.
        ItemStack cursor = PixelItem.of(stone, 24);
        bagItem.overrideOtherStackedOnMe(bag, cursor, new Slot(container, 0, 0, 0), ClickAction.SECONDARY, player, SlotAccess.NULL);
        helper.assertTrue(cursor.isEmpty() && PixelBagItem.contents(bag).count(stone) == 24, "24 bits put in from the cursor");

        // The bag on the cursor, right clicked onto a stack of bits.
        bagItem.overrideStackedOnOther(bag, new Slot(container, 1, 0, 0), ClickAction.SECONDARY, player);
        helper.assertTrue(container.getItem(1).isEmpty() && PixelBagItem.contents(bag).count(stone) == 64, "the stack of 40 taken in");

        // Bits lying on the ground go to the bag when picked up.
        player.getInventory().setItem(5, bag);
        ItemEntity loose = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), PixelItem.of(stone, 16));
        loose.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(loose);
        loose.playerTouch(player);
        helper.assertTrue(PixelBagItem.contents(bag).count(stone) == 80, "picked up into the bag: " + PixelBagItem.contents(bag).count(stone));
        helper.assertTrue(bitsInInventory(player, stone) == 0, "nothing loose in the inventory");
        helper.succeed();
    }

    // Placement modes

    /** World bit coordinates of a bit of a block given relative to the test. */
    private static int[] bitOf(GameTestHelper helper, BlockPos relative, int x, int y, int z) {
        BlockPos pos = helper.absolutePos(relative);
        return new int[]{pos.getX() * 16 + x, pos.getY() * 16 + y, pos.getZ() * 16 + z};
    }

    private static int filledAt(GameTestHelper helper, BlockPos relative) {
        return helper.getLevel().getBlockEntity(helper.absolutePos(relative)) instanceof ChiseledBlockEntity carved
                ? carved.getGrid().filledCount() : 0;
    }

    @GameTest(template = TEMPLATE)
    public static void lineCrossesBlocksAndUndoesAsOne(GameTestHelper helper) {
        ServerPlayer player = creative(helper, 1.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, PixelItem.of(Blocks.STONE.defaultBlockState(), 1));
        // From the first bit of TARGET to the last bit of the block two further along x.
        ShapePlacement.place(player, PlacementMode.LINE, bitOf(helper, TARGET, 0, 3, 8),
                bitOf(helper, TARGET.east(2), 15, 3, 8), 1);
        int total = filledAt(helper, TARGET) + filledAt(helper, TARGET.east()) + filledAt(helper, TARGET.east(2));
        helper.assertTrue(total == 48, "a 48 pixel line over three blocks: " + total);
        helper.assertTrue(EditHistory.of(player).undoSize() == 1, "one action for the three blocks");

        EditHistory.undo(player);
        helper.assertBlockPresent(Blocks.AIR, TARGET);
        helper.assertBlockPresent(Blocks.AIR, TARGET.east());
        helper.assertBlockPresent(Blocks.AIR, TARGET.east(2));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void planeStaysOnTheFirstLayer(GameTestHelper helper) {
        ServerPlayer player = creative(helper, 1.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, PixelItem.of(Blocks.OAK_PLANKS.defaultBlockState(), 1));
        ShapePlacement.place(player, PlacementMode.PLANE, bitOf(helper, TARGET, 0, 0, 0), bitOf(helper, TARGET, 15, 9, 15), 1);
        ChiseledBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().filledCount() == 256, "a full 16 x 16 layer: " + carved.getGrid().filledCount());
        helper.assertTrue(carved.getGrid().get(4, 0, 4) != PixelGrid.AIR && carved.getGrid().get(4, 1, 4) == PixelGrid.AIR,
                "only the bottom layer");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void survivalBoxNeedsEnoughBits(GameTestHelper helper) {
        ServerPlayer player = survival(helper);
        BlockState stone = Blocks.STONE.defaultBlockState();
        player.setItemInHand(InteractionHand.MAIN_HAND, PixelItem.of(stone, 20));
        int[] from = bitOf(helper, TARGET, 0, 0, 0);
        int[] to = bitOf(helper, TARGET, 2, 2, 2);
        ShapePlacement.place(player, PlacementMode.BOX, from, to, 1);
        helper.assertBlockPresent(Blocks.AIR, TARGET);
        helper.assertTrue(bitsInInventory(player, stone) == 20, "nothing spent when 27 bits are needed and 20 held");

        player.setItemInHand(InteractionHand.MAIN_HAND, PixelItem.of(stone, 64));
        helper.runAfterDelay(1, () -> {
            ShapePlacement.place(player, PlacementMode.BOX, from, to, 1);
            helper.assertTrue(filledAt(helper, TARGET) == 27, "a 3 x 3 x 3 box placed");
            helper.assertTrue(bitsInInventory(player, stone) == 37, "27 bits spent");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void bitBagHoldsSixtyFourMaterials(GameTestHelper helper) {
        BagContents contents = BagContents.EMPTY;
        int added = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            if (added == 64) {
                helper.assertTrue(contents.room(state, 10) == 0 || contents.count(state) > 0, "no room for a 65th material");
                break;
            }
            if (Materials.isValidMaterial(state) && contents.count(state) == 0) {
                contents = contents.add(state, 10);
                added++;
            }
        }
        helper.assertTrue(contents.entries().size() == 64, "64 materials held: " + contents.entries().size());
        helper.succeed();
    }

    // Placing

    @GameTest(template = TEMPLATE)
    public static void bitPlacedOnBlockTopCreatesCarvedBlock(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, PixelItem.of(Blocks.OAK_PLANKS.defaultBlockState(), 3));
        BlockPos target = helper.absolutePos(TARGET);
        Vec3 click = new Vec3(target.getX() + 0.5, target.getY() + 1.0, target.getZ() + 0.5);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(click, Direction.UP, target, false)));

        BlockPos above = TARGET.above();
        helper.assertBlockPresent(ChiseledBlocksRegistries.CHISELED_BLOCK.get(), above);
        ChiseledBlockEntity carved = helper.getBlockEntity(above);
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
        ChiselLogic.chisel(player, helper.absolutePos(TARGET), PixelGrid.index(1, 15, 1), Face.UP);
        helper.assertBlockPresent(ChiseledBlocksRegistries.CHISELED_BLOCK.get(), TARGET);

        EditHistory.undo(player);
        helper.assertBlockPresent(Blocks.STONE, TARGET);

        EditHistory.redo(player);
        helper.assertBlockPresent(ChiseledBlocksRegistries.CHISELED_BLOCK.get(), TARGET);
        ChiseledBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().filledCount() == PixelGrid.VOLUME - 1, "redo removes the same bit again");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void survivalUndoTakesTheBitsBack(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        setMode(player, ChiselMode.CUBE, 2);
        ChiselLogic.chisel(player, helper.absolutePos(TARGET), PixelGrid.index(0, 15, 0), Face.UP);
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
        ChiselLogic.chisel(player, helper.absolutePos(TARGET), PixelGrid.index(0, 15, 0), Face.UP);
        PixelInventory.take(player, Blocks.STONE.defaultBlockState(), 8);

        EditHistory.undo(player);
        helper.assertBlockPresent(ChiseledBlocksRegistries.CHISELED_BLOCK.get(), TARGET);
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
        player.setItemInHand(InteractionHand.MAIN_HAND, PixelItem.of(Blocks.OAK_PLANKS.defaultBlockState(), 3));
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

    private static final PixelGrid STONE_SLAB = new PixelGridBuilder().fill(0, 0, 0, 16, 8, 16, STONE).build();

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
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ChiseledBlocksRegistries.BLANK_PATTERN.get()));
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

        ChiseledBlockEntity printed = helper.getBlockEntity(TARGET.above());
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

        ItemStack bag = new ItemStack(ChiseledBlocksRegistries.PIXEL_BAG.get());
        PixelBagItem.setContents(bag, BagContents.EMPTY.add(Blocks.STONE.defaultBlockState(), 2048 + 10));
        player.getInventory().setItem(5, bag);
        useOnTop(player, helper, TARGET);

        helper.assertBlockPresent(ChiseledBlocksRegistries.CHISELED_BLOCK.get(), TARGET.above());
        helper.assertTrue(PixelBagItem.contents(bag).count(Blocks.STONE.defaultBlockState()) == 10, "2048 bits used");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void negativePatternCarvesItsShape(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, PatternItem.of(STONE_SLAB, PatternMode.NEGATIVE));
        ItemStack bag = new ItemStack(ChiseledBlocksRegistries.PIXEL_BAG.get());
        player.getInventory().setItem(5, bag);
        useOnTop(player, helper, TARGET);

        ChiseledBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().get(0, 0, 0) == PixelGrid.AIR, "the bottom half is carved out");
        helper.assertTrue(carved.getGrid().get(0, 8, 0) == STONE, "the top half stays");
        helper.assertTrue(PixelBagItem.contents(bag).count(Blocks.STONE.defaultBlockState()) == 2048, "the carved bits are given");
        helper.succeed();
    }

    // Advanced tools

    @GameTest(template = TEMPLATE)
    public static void mirrorRepeatsAChiselStroke(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = creative(helper, 1.5);
        PlayerSymmetry.setOnServer(player, Symmetry.X);
        ChiselLogic.chisel(player, helper.absolutePos(TARGET), PixelGrid.index(2, 15, 3), Face.UP);

        ChiseledBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().get(2, 15, 3) == PixelGrid.AIR, "the clicked bit is removed");
        helper.assertTrue(carved.getGrid().get(13, 15, 3) == PixelGrid.AIR, "its mirror image is removed too");
        helper.assertTrue(carved.getGrid().filledCount() == PixelGrid.VOLUME - 2, "nothing else is touched");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void mirrorRepeatsAPlacementWithinTheBits(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        PlayerSymmetry.setOnServer(player, Symmetry.XZ);
        player.setItemInHand(InteractionHand.MAIN_HAND, PixelItem.of(Blocks.OAK_PLANKS.defaultBlockState(), 3));
        BlockPos target = helper.absolutePos(TARGET);
        Vec3 click = new Vec3(target.getX() + 0.1, target.getY() + 1.0, target.getZ() + 0.1);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(click, Direction.UP, target, false)));

        ChiseledBlockEntity placed = helper.getBlockEntity(TARGET.above());
        helper.assertTrue(placed.getGrid().filledCount() == 3, "only as many bits as held are placed");
        helper.assertTrue(placed.getGrid().get(1, 0, 1) != PixelGrid.AIR, "the clicked bit is placed first");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the 3 bits are used");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void wrenchTurnsACarving(GameTestHelper helper) {
        int planks = Materials.id(Blocks.OAK_PLANKS.defaultBlockState());
        placeCarved(helper, new PixelGridBuilder().fill(0, 0, 0, 16, 1, 16, STONE).set(8, 3, 0, planks).build());
        ServerPlayer player = survival(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ChiseledBlocksRegistries.WRENCH.get()));
        useOnTop(player, helper, TARGET);

        // Edits on an existing carving are applied at the end of the tick.
        helper.runAfterDelay(1, () -> {
            ChiseledBlockEntity carved = helper.getBlockEntity(TARGET);
            helper.assertTrue(carved.getGrid().get(15, 3, 8) == planks, "a quarter turn moves north to east");
            helper.assertTrue(carved.getGrid().get(8, 3, 0) == PixelGrid.AIR, "the old place is empty");
            helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "the wrench wears");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void sneakingWrenchMirrorsACarving(GameTestHelper helper) {
        placeCarved(helper, new PixelGridBuilder().fill(0, 0, 0, 16, 4, 16, STONE).build());
        ServerPlayer player = survival(helper);
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ChiseledBlocksRegistries.WRENCH.get()));
        useOnTop(player, helper, TARGET);

        helper.runAfterDelay(1, () -> {
            ChiseledBlockEntity carved = helper.getBlockEntity(TARGET);
            helper.assertTrue(carved.getGrid().get(0, 15, 0) == STONE && carved.getGrid().get(0, 0, 0) == PixelGrid.AIR,
                    "mirrored upside down across the clicked face's axis");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void sawCutsASliceIntoAnItem(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = survival(helper);
        ItemStack saw = new ItemStack(ChiseledBlocksRegistries.SAW.get());
        saw.set(ChiseledBlocksComponents.SAW_THICKNESS.get(), 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, saw);
        useOnTop(player, helper, TARGET);

        ChiseledBlockEntity carved = helper.getBlockEntity(TARGET);
        helper.assertTrue(carved.getGrid().filledCount() == PixelGrid.VOLUME - 1024, "a 4 bit slice is cut from the top");
        helper.assertTrue(carved.getGrid().get(0, 11, 0) == STONE && carved.getGrid().get(0, 12, 0) == PixelGrid.AIR, "the cut is at y 12");
        ItemStack piece = player.getInventory().items.stream()
                .filter(stack -> stack.is(carvedStone())).findFirst().orElse(ItemStack.EMPTY);
        PixelGrid sliceGrid = piece.get(ChiseledBlocksComponents.GRID.get());
        helper.assertTrue(sliceGrid != null && sliceGrid.filledCount() == 1024, "the slice is given as a carved block");
        helper.succeed();
    }

    // One item id per material

    private static Item carved(String path) {
        return BuiltInRegistries.ITEM.get(ChiseledBlocks.id("chiseled/" + path));
    }

    @GameTest(template = TEMPLATE)
    public static void carvableMaterialsHaveTheirOwnItem(GameTestHelper helper) {
        for (String path : List.of("stone", "oak_planks", "glass", "oak_log", "white_wool")) {
            helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ChiseledBlocks.id("chiseled/" + path)), "chiseledblocks:chiseled/" + path + " exists");
        }
        // A block entity or a partial shape cannot be carved: no item.
        helper.assertFalse(BuiltInRegistries.ITEM.containsKey(ChiseledBlocks.id("chiseled/chest")), "no item for chests");
        helper.assertFalse(BuiltInRegistries.ITEM.containsKey(ChiseledBlocks.id("chiseled/oak_slab")), "no item for slabs");
        helper.assertTrue(ChiseledBlocksRegistries.CHISELED_BLOCK.get().asItem() == ChiseledBlocksRegistries.CHISELED_BLOCK_ITEM.get(),
                "the carved block's own item stays the generic one");
        helper.assertTrue(ChiseledItems.all().size() > 300, "hundreds of materials: " + ChiseledItems.all().size());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void mainMaterialPicksTheItem(GameTestHelper helper) {
        int oak = Materials.id(Blocks.OAK_PLANKS.defaultBlockState());
        PixelGrid grid = new PixelGridBuilder().fill(0, 0, 0, 16, 10, 16, oak).fill(0, 10, 0, 16, 16, 16, STONE).build();
        placeCarved(helper, grid);
        BlockPos pos = helper.absolutePos(TARGET);
        ItemStack picked = helper.getLevel().getBlockState(pos).getCloneItemStack(
                new BlockHitResult(pos.getCenter(), Direction.UP, pos, false), helper.getLevel(), pos, null);
        helper.assertTrue(picked.is(carved("oak_planks")), "pick block gives chiseledblocks:chiseled/oak_planks, got " + picked);
        helper.assertTrue(grid.equals(picked.get(ChiseledBlocksComponents.GRID.get())), "with the carving");
        helper.assertTrue(ChiseledItems.itemFor(PixelGrid.filled(STONE).toBuilder().set(0, PixelGrid.AIR).build()) == carved("stone"),
                "a stone carving is chiseledblocks:chiseled/stone");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void carvedItemTagHoldsEveryCarvedItem(GameTestHelper helper) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, ChiseledBlocks.id("chiseled_blocks"));
        helper.assertTrue(new ItemStack(carved("stone")).is(tag), "carved stone is in #chiseledblocks:chiseled_blocks");
        helper.assertTrue(new ItemStack(carved("glass")).is(tag), "carved glass is in #chiseledblocks:chiseled_blocks");
        helper.assertTrue(new ItemStack(ChiseledBlocksRegistries.CHISELED_BLOCK_ITEM.get()).is(tag), "the generic item is in the tag");
        helper.assertFalse(new ItemStack(Blocks.STONE).is(tag), "plain stone is not");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void materialItemWithoutCarvingPlacesItsExample(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        ServerPlayer player = creative(helper, 1.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(carved("oak_planks")));
        useOnTop(player, helper, TARGET);
        ChiseledBlockEntity placed = helper.getBlockEntity(TARGET.above());
        helper.assertTrue(placed.getGrid().filledCount() == 3072, "the example step is placed: " + placed.getGrid().filledCount());
        helper.assertTrue(placed.getGrid().contains(Materials.id(Blocks.OAK_PLANKS.defaultBlockState())), "in oak planks");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void genericCarvingInInventoryBecomesMaterialItem(GameTestHelper helper) {
        ServerPlayer player = survival(helper);
        ItemStack generic = new ItemStack(ChiseledBlocksRegistries.CHISELED_BLOCK_ITEM.get(), 3);
        PixelGrid grid = PixelGrid.filled(STONE).toBuilder().set(0, PixelGrid.AIR).build();
        generic.set(ChiseledBlocksComponents.GRID.get(), grid);
        player.getInventory().items.set(5, generic);
        player.getInventory().tick();
        ItemStack after = player.getInventory().items.get(5);
        helper.assertTrue(after.is(carvedStone()) && after.getCount() == 3 && grid.equals(after.get(ChiseledBlocksComponents.GRID.get())),
                "converted to 3 chiseledblocks:chiseled/stone with the same carving, got " + after);
        helper.succeed();
    }

    // Performance goals measured without a client

    @GameTest(template = TEMPLATE)
    public static void singleBitEditFitsInASmallPacket(GameTestHelper helper) {
        // P7: one bit changed is sent as a delta. Payload bytes, plus the custom payload framing:
        // packet id (1 byte) and the channel name with its length prefix.
        GridDeltaPayload delta = new GridDeltaPayload(helper.absolutePos(TARGET), new int[]{PixelGrid.index(3, 4, 5)},
                new int[]{STONE}, 123456789);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        GridDeltaPayload.CODEC.encode(buffer, delta);
        int payloadBytes = buffer.readableBytes();
        int framing = 1 + 1 + GridDeltaPayload.TYPE.id().toString().length();
        int total = payloadBytes + framing;
        ChiseledBlocks.LOGGER.info("[gametest] single bit edit: {} payload bytes + {} framing = {} bytes", payloadBytes, framing, total);
        helper.assertTrue(total < 128, "a single bit edit takes " + total + " bytes, goal under 128");
        helper.succeed();
    }

    // Chisels & Bits import

    @GameTest(template = TEMPLATE)
    public static void readsARealChiselsAndBitsBlock(GameTestHelper helper) throws Exception {
        // Block entity data saved by Chisels & Bits 21.1.33 for planks holding one stone bit, and the
        // occupancy Chisels & Bits computed for it (see runClientCnbSource).
        List<String> fixture;
        try (var in = CarvingGameTests.class.getResourceAsStream("/chiseledblocks_test/cnb_planks_with_stone_bit.txt")) {
            fixture = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
        }
        CompoundTag tag = TagParser.parseTag(fixture.get(0));
        String[] expected = fixture.get(1).split(" ");

        PixelGrid grid = ChiselsAndBitsImport.read(tag, helper.getLevel().registryAccess());
        helper.assertTrue(grid != null, "the data is read");
        helper.assertTrue(grid.filledCount() == PixelGrid.VOLUME - 3, "3 bits carved out");
        helper.assertTrue(grid.contains(Materials.id(Blocks.OAK_PLANKS.defaultBlockState())), "planks");
        helper.assertTrue(grid.contains(STONE), "the stone bit");
        BitSet occupied = BitSet.valueOf(HexFormat.of().parseHex(expected[4]));
        for (int i = 0; i < PixelGrid.VOLUME; i++) {
            helper.assertTrue(occupied.get(i) == (grid.get(i) != PixelGrid.AIR), "voxel " + i + " matches Chisels & Bits");
        }
        helper.succeed();
    }

    // Blocks and items

    @GameTest(template = TEMPLATE)
    public static void turnedBlockStateTurnsTheCarving(GameTestHelper helper) {
        // What WorldEdit, structure blocks and Create do: a turned state with the original data.
        PixelGrid north = new PixelGridBuilder().fill(0, 0, 0, 16, 1, 16, STONE).set(8, 3, 0, STONE).build();
        ChiseledBlockEntity original = placeCarved(helper, north);
        CompoundTag tag = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        BlockState east = ChiseledBlocksRegistries.CHISELED_BLOCK.get().defaultBlockState()
                .setValue(ChiseledBlock.FACING, Direction.EAST);
        ChiseledBlockEntity turned = new ChiseledBlockEntity(helper.absolutePos(TARGET), east);
        turned.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(turned.getGrid().get(15, 3, 8) == STONE, "the carving turned with the block");
        helper.assertTrue(turned.getGrid().get(8, 3, 0) == PixelGrid.AIR, "nothing left at the old place");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void emptiedBlockBecomesAir(GameTestHelper helper) {
        int voxel = PixelGrid.index(4, 4, 4);
        ChiseledBlockEntity carved = placeCarved(helper, new PixelGridBuilder().set(voxel, STONE).build());
        carved.edit(builder -> builder.set(voxel, PixelGrid.AIR));
        helper.runAfterDelay(1, () -> {
            helper.assertBlockPresent(Blocks.AIR, TARGET);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void uniformBlockRevertsToPlainBlock(GameTestHelper helper) {
        int voxel = PixelGrid.index(7, 7, 7);
        ChiseledBlockEntity carved = placeCarved(helper, PixelGrid.filled(STONE).toBuilder().set(voxel, PixelGrid.AIR).build());
        carved.edit(builder -> builder.set(voxel, STONE));
        helper.runAfterDelay(1, () -> {
            helper.assertBlockPresent(Blocks.STONE, TARGET);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void brokenCarvedBlockDropsItsCarving(GameTestHelper helper) {
        PixelGrid slab = new PixelGridBuilder().fill(0, 0, 0, 16, 8, 16, STONE).build();
        ChiseledBlockEntity carved = placeCarved(helper, slab);
        BlockPos pos = helper.absolutePos(TARGET);
        List<ItemStack> drops = Block.getDrops(helper.getLevel().getBlockState(pos), helper.getLevel(), pos, carved);

        helper.assertTrue(drops.size() == 1, "one item dropped");
        ItemStack drop = drops.get(0);
        helper.assertTrue(drop.is(carvedStone()), "the drop is the carved stone item");
        helper.assertTrue(slab.equals(drop.get(ChiseledBlocksComponents.GRID.get())), "the drop keeps the carving");

        ChiseledBlockEntity placed = new ChiseledBlockEntity(pos, ChiseledBlocksRegistries.CHISELED_BLOCK.get().defaultBlockState());
        placed.applyComponentsFromItemStack(drop);
        helper.assertTrue(placed.getGrid() == carved.getGrid(), "placing the item restores the same grid");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void gridSurvivesSaveAndLoad(GameTestHelper helper) {
        PixelGrid grid = new PixelGridBuilder()
                .fill(0, 0, 0, 16, 8, 16, STONE)
                .fill(0, 8, 0, 8, 16, 16, Materials.id(Blocks.GLASS.defaultBlockState()))
                .build();
        ChiseledBlockEntity carved = placeCarved(helper, grid);
        CompoundTag tag = carved.saveWithoutMetadata(helper.getLevel().registryAccess());

        BlockState state = ChiseledBlocksRegistries.CHISELED_BLOCK.get().defaultBlockState();
        ChiseledBlockEntity loaded = new ChiseledBlockEntity(helper.absolutePos(TARGET), state);
        loaded.loadWithComponents(tag, helper.getLevel().registryAccess());

        helper.assertTrue(loaded.getGrid().equals(grid), "grid content survives a save");
        helper.assertTrue(loaded.getGrid() == carved.getGrid(), "identical grids are shared after load");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void savedGridsAreDecodedOnce(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        PixelGrid half = new PixelGridBuilder().fill(0, 0, 0, 16, 8, 16, STONE).build();
        PixelGrid quarter = new PixelGridBuilder().fill(0, 0, 0, 16, 4, 16, STONE).build();
        PixelGrid first = GridNbt.read(GridNbt.write(half), registries);
        PixelGrid second = GridNbt.read(GridNbt.write(half), registries);
        PixelGrid other = GridNbt.read(GridNbt.write(quarter), registries);
        helper.assertTrue(first.equals(half), "the grid reads back unchanged");
        helper.assertTrue(first == second, "the same saved grid gives the same instance");
        helper.assertTrue(other.equals(quarter) && other != first, "a different grid of the same material reads as itself");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void shapeFollowsGrid(GameTestHelper helper) {
        PixelGrid slab = new PixelGridBuilder().fill(0, 0, 0, 16, 8, 16, STONE).build();
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
