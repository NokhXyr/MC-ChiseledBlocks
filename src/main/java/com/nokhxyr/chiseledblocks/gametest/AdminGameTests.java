/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.admin.ChiseledBlocksPermissions;
import com.nokhxyr.chiseledblocks.admin.BlockProfiler;
import com.nokhxyr.chiseledblocks.admin.ChiseledIndex;
import com.nokhxyr.chiseledblocks.admin.ChiseledSearch;
import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import com.nokhxyr.chiseledblocks.block.Materials;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGridBuilder;
import com.nokhxyr.chiseledblocks.core.voxel.Face;
import com.nokhxyr.chiseledblocks.edit.ChiselLogic;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.server.permission.PermissionAPI;

import java.util.List;

/** The admin panel's server side: carved block index, searches, permissions and profiling. */
@GameTestHolder(ChiseledBlocks.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdminGameTests {
    private static final String TEMPLATE = "empty";
    private static final BlockPos TARGET = new BlockPos(2, 2, 2);
    /** A material no other test uses, so parallel tests never show up in the searches. */
    private static final int AMETHYST = Materials.id(Blocks.AMETHYST_BLOCK.defaultBlockState());
    private static final int STONE = Materials.id(Blocks.STONE.defaultBlockState());

    private AdminGameTests() {
    }

    private static ChiseledBlockEntity placeCarved(GameTestHelper helper, PixelGrid grid) {
        helper.setBlock(TARGET, ChiseledBlocksRegistries.CHISELED_BLOCK.get());
        ChiseledBlockEntity carved = helper.getBlockEntity(TARGET);
        carved.setGrid(grid);
        return carved;
    }

    private static int count(GameTestHelper helper, ChiseledSearch.Query query) {
        Vec3 centre = helper.absolutePos(TARGET).getCenter();
        return ChiseledSearch.run(helper.getLevel(), centre, query).size();
    }

    @GameTest(template = TEMPLATE)
    public static void indexAndSearchFollowCarvedBlocks(GameTestHelper helper) {
        PixelGrid grid = new PixelGridBuilder()
                .fill(0, 0, 0, 16, 8, 16, AMETHYST)
                .fill(0, 8, 0, 8, 16, 16, STONE)
                .build();
        ChiseledBlockEntity carved = placeCarved(helper, grid);

        // The level calls onLoad, which indexes the block, at its next block entity tick.
        helper.runAfterDelay(1, () -> {
            BlockPos pos = helper.absolutePos(TARGET);
            helper.assertTrue(ChiseledIndex.loaded(helper.getLevel()).contains(carved), "a placed carved block is indexed");
            helper.assertTrue(count(helper, ChiseledSearch.Query.blocks(2, Blocks.AMETHYST_BLOCK)) == 1, "found by radius and material");
            helper.assertTrue(count(helper, ChiseledSearch.Query.chunks(0, Blocks.AMETHYST_BLOCK)) == 1, "found in its own chunk");
            helper.assertTrue(count(helper, ChiseledSearch.Query.loaded(Blocks.AMETHYST_BLOCK)) >= 1, "found in the loaded chunks");
            helper.assertTrue(count(helper, new ChiseledSearch.Query(ChiseledSearch.Scope.CHUNK, 0, pos.getX() >> 4, pos.getZ() >> 4,
                    Blocks.AMETHYST_BLOCK)) == 1, "found in the chunk picked on the map");
            helper.assertTrue(count(helper, ChiseledSearch.Query.blocks(2, Blocks.EMERALD_BLOCK)) == 0, "the material filter excludes");
            ChiseledSearch.ChunkMap map = ChiseledSearch.map(helper.getLevel(), pos.getCenter(), 1);
            helper.assertTrue(map.counts()[map.cell(pos.getX() >> 4, pos.getZ() >> 4)] >= 1, "the map counts it");
            helper.assertTrue(map.loaded()[map.cell(new ChunkPos(pos).x, new ChunkPos(pos).z)], "its chunk shows as loaded");

            helper.setBlock(TARGET, Blocks.AIR);
            helper.assertFalse(ChiseledIndex.loaded(helper.getLevel()).contains(carved), "a removed carved block leaves the index");
            helper.assertTrue(count(helper, ChiseledSearch.Query.blocks(2, Blocks.AMETHYST_BLOCK)) == 0, "no longer found");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    @SuppressWarnings("removal")
    public static void panelNeedsItsPermission(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        List<String> nodes = PermissionAPI.getRegisteredNodes().stream().map(node -> node.getNodeName()).toList();
        helper.assertTrue(nodes.containsAll(List.of("chiseledblocks.panel", "chiseledblocks.panel.teleport", "chiseledblocks.panel.perf")),
                "the three nodes are registered for permission mods: " + nodes);

        helper.assertFalse(ChiseledBlocksPermissions.has(player, ChiseledBlocksPermissions.PANEL), "a plain player may not open the panel");
        helper.assertFalse(ChiseledBlocksPermissions.has(player, ChiseledBlocksPermissions.TELEPORT), "nor teleport");
        try {
            helper.getLevel().getServer().getCommands().getDispatcher().execute("chiseledblocks", player.createCommandSourceStack());
            helper.fail("a plain player could run /chiseledblocks");
        } catch (CommandSyntaxException expected) {
            // Unknown or not allowed: the command is not offered to this player.
        }

        // An explicit level: the game test server gives plain operators level 0.
        helper.getLevel().getServer().getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 2, false));
        helper.assertTrue(ChiseledBlocksPermissions.has(player, ChiseledBlocksPermissions.PANEL), "an operator may open the panel");
        helper.assertTrue(ChiseledBlocksPermissions.has(player, ChiseledBlocksPermissions.TELEPORT), "and teleport");
        helper.assertTrue(ChiseledBlocksPermissions.has(player, ChiseledBlocksPermissions.PERF), "and measure");
        helper.getLevel().getServer().getPlayerList().deop(player.getGameProfile());
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    @SuppressWarnings("removal")
    public static void profilerAttributesTimeToTheBlock(GameTestHelper helper) {
        placeCarved(helper, PixelGrid.filled(STONE).toBuilder().set(PixelGrid.index(0, 0, 0), PixelGrid.AIR).build());
        BlockPos target = helper.absolutePos(TARGET);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.moveTo(target.getX() + 0.5, target.getY(), target.getZ() - 1.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ChiseledBlocksRegistries.IRON_CHISEL.get()));

        BlockProfiler.start(1000, report -> { });
        ChiselLogic.chisel(player, target, PixelGrid.index(7, 15, 0), Face.UP);
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
