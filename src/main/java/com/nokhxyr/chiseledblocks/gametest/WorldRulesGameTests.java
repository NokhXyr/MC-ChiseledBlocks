/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.gametest;

import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import com.nokhxyr.chiseledblocks.block.Materials;
import com.nokhxyr.chiseledblocks.core.edit.ChiselMode;
import com.nokhxyr.chiseledblocks.core.edit.PlacementMode;
import com.nokhxyr.chiseledblocks.core.voxel.Face;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGridBuilder;
import com.nokhxyr.chiseledblocks.edit.ChiselLogic;
import com.nokhxyr.chiseledblocks.edit.ShapePlacement;
import com.nokhxyr.chiseledblocks.item.ChiselSettings;
import com.nokhxyr.chiseledblocks.item.PixelItem;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksComponents;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In game checks of how carved blocks behave towards the rest of the world: blocks resting on
 * them, protection mods, tools and explosions.
 */
@GameTestHolder(ChiseledBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldRulesGameTests {
    private static final String TEMPLATE = "empty";
    private static final BlockPos TARGET = new BlockPos(2, 2, 2);

    /** Positions a stand-in protection mod refuses to let players change. */
    private static final Set<BlockPos> PROTECTED = ConcurrentHashMap.newKeySet();
    private static boolean protectionListening;

    private WorldRulesGameTests() {
    }

    /** Behaves like claim mods do: cancels breaking and placing at protected positions. */
    private static void listenAsProtectionMod() {
        if (protectionListening) {
            return;
        }
        protectionListening = true;
        NeoForge.EVENT_BUS.addListener((BlockEvent.BreakEvent event) -> {
            if (PROTECTED.contains(event.getPos())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((BlockEvent.EntityPlaceEvent event) -> {
            if (PROTECTED.contains(event.getPos())) {
                event.setCanceled(true);
            }
        });
    }

    @SuppressWarnings("removal")
    private static ServerPlayer player(GameTestHelper helper, GameType mode) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(mode);
        BlockPos target = helper.absolutePos(TARGET);
        player.moveTo(target.getX() + 0.5, target.getY(), target.getZ() - 1.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ChiseledBlocksRegistries.IRON_CHISEL.get()));
        return player;
    }

    @GameTest(template = TEMPLATE)
    public static void torchStaysWhenItsSupportIsKept(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(TARGET.above(), Blocks.TORCH);
        // A pixel in a bottom corner: the top face under the torch is untouched.
        ChiselLogic.chisel(player(helper, GameType.CREATIVE), helper.absolutePos(TARGET), PixelGrid.index(0, 0, 0), Face.NORTH);
        helper.runAfterDelay(2, () -> {
            helper.assertBlockPresent(Blocks.TORCH, TARGET.above());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void torchDropsWhenItsSupportIsCarvedAway(GameTestHelper helper) {
        helper.setBlock(TARGET, Blocks.STONE);
        helper.setBlock(TARGET.above(), Blocks.TORCH);
        ServerPlayer player = player(helper, GameType.CREATIVE);
        player.getMainHandItem().set(ChiseledBlocksComponents.CHISEL_SETTINGS.get(), new ChiselSettings(ChiselMode.CUBE, 8));
        BlockPos target = helper.absolutePos(TARGET);
        // The four cubes of the upper half: nothing is left to hold the torch.
        for (int[] c : new int[][]{{0, 0}, {8, 0}, {0, 8}, {8, 8}}) {
            ChiselLogic.chisel(player, target, PixelGrid.index(c[0], 12, c[1]), Face.UP);
        }
        helper.runAfterDelay(3, () -> {
            helper.assertBlockNotPresent(Blocks.TORCH, TARGET.above());
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void protectedBlockCannotBeChiseled(GameTestHelper helper) {
        listenAsProtectionMod();
        helper.setBlock(TARGET, Blocks.STONE);
        BlockPos target = helper.absolutePos(TARGET);
        PROTECTED.add(target);
        try {
            ChiselLogic.chisel(player(helper, GameType.SURVIVAL), target, PixelGrid.index(8, 15, 8), Face.UP);
        } finally {
            PROTECTED.remove(target);
        }
        helper.assertBlockPresent(Blocks.STONE, TARGET);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void protectedBlockCannotReceivePixels(GameTestHelper helper) {
        listenAsProtectionMod();
        BlockPos target = helper.absolutePos(TARGET);
        PROTECTED.add(target);
        ServerPlayer player = player(helper, GameType.CREATIVE);
        player.setItemInHand(InteractionHand.MAIN_HAND, PixelItem.of(Blocks.STONE.defaultBlockState(), 64));
        int[] a = {target.getX() * 16, target.getY() * 16, target.getZ() * 16};
        int[] b = {a[0] + 3, a[1], a[2]};
        try {
            ShapePlacement.place(player, PlacementMode.LINE, a, b, 0);
        } finally {
            PROTECTED.remove(target);
        }
        helper.runAfterDelay(1, () -> {
            helper.assertBlockPresent(Blocks.AIR, TARGET);
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    public static void pickaxeBreaksChiseledStoneFaster(GameTestHelper helper) {
        int stone = Materials.id(Blocks.STONE.defaultBlockState());
        helper.setBlock(TARGET, ChiseledBlocksRegistries.CHISELED_BLOCK.get());
        ChiseledBlockEntity carved = helper.getBlockEntity(TARGET);
        carved.setGrid(new PixelGridBuilder().fill(0, 0, 0, 16, 8, 16, stone).build());
        BlockPos target = helper.absolutePos(TARGET);
        BlockState state = helper.getLevel().getBlockState(target);

        ServerPlayer player = player(helper, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        float hand = state.getDestroyProgress(player, helper.getLevel(), target);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
        float pickaxe = state.getDestroyProgress(player, helper.getLevel(), target);
        helper.assertTrue(pickaxe > hand * 4, "a pickaxe should break chiseled stone much faster than a hand: hand "
                + hand + ", pickaxe " + pickaxe);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void explosionResistanceFollowsTheMaterial(GameTestHelper helper) {
        int glass = Materials.id(Blocks.GLASS.defaultBlockState());
        helper.setBlock(TARGET, ChiseledBlocksRegistries.CHISELED_BLOCK.get());
        ChiseledBlockEntity carved = helper.getBlockEntity(TARGET);
        carved.setGrid(new PixelGridBuilder().fill(0, 0, 0, 16, 8, 16, glass).build());
        BlockPos target = helper.absolutePos(TARGET);
        float resistance = helper.getLevel().getBlockState(target).getExplosionResistance(helper.getLevel(), target, null);
        helper.assertTrue(resistance == Blocks.GLASS.getExplosionResistance(),
                "chiseled glass should resist like glass (" + Blocks.GLASS.getExplosionResistance() + "), not " + resistance);
        helper.succeed();
    }
}
