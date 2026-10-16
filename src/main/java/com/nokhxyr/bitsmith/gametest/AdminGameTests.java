/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.gametest;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.admin.BitsmithPermissions;
import com.nokhxyr.bitsmith.admin.BlockProfiler;
import com.nokhxyr.bitsmith.admin.CarvedIndex;
import com.nokhxyr.bitsmith.admin.CarvedSearch;
import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.core.voxel.Face;
import com.nokhxyr.bitsmith.edit.ChiselLogic;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
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

    private static int count(GameTestHelper helper, CarvedSearch.Query query) {
        Vec3 centre = helper.absolutePos(TARGET).getCenter();
        return CarvedSearch.run(helper.getLevel(), centre, query).size();
    }

    @GameTest(template = TEMPLATE)
    public static void indexAndSearchFollowCarvedBlocks(GameTestHelper helper) {
        BitGrid grid = new BitGridBuilder()
                .fill(0, 0, 0, 16, 8, 16, AMETHYST)
                .fill(0, 8, 0, 8, 16, 16, STONE)
                .build();
        CarvedBlockEntity carved = placeCarved(helper, grid);

        // The level calls onLoad, which indexes the block, at its next block entity tick.
        helper.runAfterDelay(1, () -> {
            BlockPos pos = helper.absolutePos(TARGET);
            helper.assertTrue(CarvedIndex.loaded(helper.getLevel()).contains(carved), "a placed carved block is indexed");
            helper.assertTrue(count(helper, CarvedSearch.Query.blocks(2, Blocks.AMETHYST_BLOCK)) == 1, "found by radius and material");
            helper.assertTrue(count(helper, CarvedSearch.Query.chunks(0, Blocks.AMETHYST_BLOCK)) == 1, "found in its own chunk");
            helper.assertTrue(count(helper, CarvedSearch.Query.loaded(Blocks.AMETHYST_BLOCK)) >= 1, "found in the loaded chunks");
            helper.assertTrue(count(helper, new CarvedSearch.Query(CarvedSearch.Scope.CHUNK, 0, pos.getX() >> 4, pos.getZ() >> 4,
                    Blocks.AMETHYST_BLOCK)) == 1, "found in the chunk picked on the map");
            helper.assertTrue(count(helper, CarvedSearch.Query.blocks(2, Blocks.EMERALD_BLOCK)) == 0, "the material filter excludes");
            CarvedSearch.ChunkMap map = CarvedSearch.map(helper.getLevel(), pos.getCenter(), 1);
            helper.assertTrue(map.counts()[map.cell(pos.getX() >> 4, pos.getZ() >> 4)] >= 1, "the map counts it");
            helper.assertTrue(map.loaded()[map.cell(new ChunkPos(pos).x, new ChunkPos(pos).z)], "its chunk shows as loaded");

            helper.setBlock(TARGET, Blocks.AIR);
            helper.assertFalse(CarvedIndex.loaded(helper.getLevel()).contains(carved), "a removed carved block leaves the index");
            helper.assertTrue(count(helper, CarvedSearch.Query.blocks(2, Blocks.AMETHYST_BLOCK)) == 0, "no longer found");
            helper.succeed();
        });
    }

    @GameTest(template = TEMPLATE)
    @SuppressWarnings("removal")
    public static void panelNeedsItsPermission(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        List<String> nodes = PermissionAPI.getRegisteredNodes().stream().map(node -> node.getNodeName()).toList();
        helper.assertTrue(nodes.containsAll(List.of("bitsmith.panel", "bitsmith.panel.teleport", "bitsmith.panel.perf")),
                "the three nodes are registered for permission mods: " + nodes);

        helper.assertFalse(BitsmithPermissions.has(player, BitsmithPermissions.PANEL), "a plain player may not open the panel");
        helper.assertFalse(BitsmithPermissions.has(player, BitsmithPermissions.TELEPORT), "nor teleport");
        try {
            helper.getLevel().getServer().getCommands().getDispatcher().execute("bitsmith", player.createCommandSourceStack());
            helper.fail("a plain player could run /bitsmith");
        } catch (CommandSyntaxException expected) {
            // Unknown or not allowed: the command is not offered to this player.
        }

        // An explicit level: the game test server gives plain operators level 0.
        helper.getLevel().getServer().getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 2, false));
        helper.assertTrue(BitsmithPermissions.has(player, BitsmithPermissions.PANEL), "an operator may open the panel");
        helper.assertTrue(BitsmithPermissions.has(player, BitsmithPermissions.TELEPORT), "and teleport");
        helper.assertTrue(BitsmithPermissions.has(player, BitsmithPermissions.PERF), "and measure");
        helper.getLevel().getServer().getPlayerList().deop(player.getGameProfile());
        helper.succeed();
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
