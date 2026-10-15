/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.admin;

import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.network.AdminPayloads;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Server side of the admin screen. Every request needs operator rights (level 2). */
public final class AdminService {
    private AdminService() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(AdminPayloads.RequestOpen.TYPE, AdminPayloads.RequestOpen.CODEC,
                (payload, context) -> withOperator(context, player -> PacketDistributor.sendToPlayer(player, new AdminPayloads.Open())));
        registrar.playToServer(AdminPayloads.FindRequest.TYPE, AdminPayloads.FindRequest.CODEC,
                (payload, context) -> withOperator(context, player -> find(player, payload)));
        registrar.playToServer(AdminPayloads.MapRequest.TYPE, AdminPayloads.MapRequest.CODEC,
                (payload, context) -> withOperator(context, player -> map(player, payload)));
        registrar.playToServer(AdminPayloads.PerfRequest.TYPE, AdminPayloads.PerfRequest.CODEC,
                (payload, context) -> withOperator(context, player -> perf(player, payload)));
        registrar.playToServer(AdminPayloads.Teleport.TYPE, AdminPayloads.Teleport.CODEC,
                (payload, context) -> withOperator(context, player -> teleport(player, payload)));
    }

    private static void withOperator(IPayloadContext context, java.util.function.Consumer<ServerPlayer> action) {
        if (context.player() instanceof ServerPlayer player && player.hasPermissions(Commands.LEVEL_GAMEMASTERS)) {
            action.accept(player);
        }
    }

    // Search

    private static void find(ServerPlayer player, AdminPayloads.FindRequest request) {
        Block material = null;
        boolean unknown = false;
        if (!request.material().isBlank()) {
            material = parseBlock(request.material());
            unknown = material == null;
        }
        List<AdminPayloads.Hit> hits = new ArrayList<>();
        int total = 0;
        LongOpenHashSet chunks = new LongOpenHashSet();
        if (!unknown) {
            CarvedSearch.Query query = new CarvedSearch.Query(CarvedSearch.Scope.byOrdinal(request.scope()),
                    request.radius(), request.chunkX(), request.chunkZ(), material);
            List<CarvedBlockEntity> found = CarvedSearch.run(player.serverLevel(), player.position(), query);
            total = found.size();
            for (CarvedBlockEntity blockEntity : found) {
                BlockPos pos = blockEntity.getBlockPos();
                chunks.add(ChunkPos.asLong(pos));
                if (hits.size() < AdminPayloads.MAX_ROWS) {
                    List<ResourceLocation> materials = new ArrayList<>();
                    for (Block block : CarvedSearch.materials(blockEntity.getGrid())) {
                        materials.add(BuiltInRegistries.BLOCK.getKey(block));
                    }
                    hits.add(new AdminPayloads.Hit(pos, (float) Math.sqrt(pos.getCenter().distanceToSqr(player.position())),
                            blockEntity.getGrid().filledCount(), materials));
                }
            }
        }
        PacketDistributor.sendToPlayer(player, new AdminPayloads.FindResult(request.target(),
                player.serverLevel().dimension().location(), unknown, total, chunks.size(), hits));
    }

    /** A block id, with or without the {@code minecraft:} namespace; null when unknown or air. */
    static Block parseBlock(String text) {
        ResourceLocation id = ResourceLocation.tryParse(text.trim().toLowerCase(java.util.Locale.ROOT));
        if (id == null) {
            return null;
        }
        Optional<Block> block = BuiltInRegistries.BLOCK.getOptional(id);
        return block.filter(found -> found != Blocks.AIR).orElse(null);
    }

    // Map

    private static void map(ServerPlayer player, AdminPayloads.MapRequest request) {
        CarvedSearch.ChunkMap map = CarvedSearch.map(player.serverLevel(), player.position(), request.radius());
        PacketDistributor.sendToPlayer(player, new AdminPayloads.MapResult(map.centreX(), map.centreZ(), map.radius(),
                map.loaded(), map.counts()));
    }

    // Profiling

    private static void perf(ServerPlayer player, AdminPayloads.PerfRequest request) {
        if (request.seconds() <= 0) {
            BlockProfiler.Report report = BlockProfiler.stop();
            if (report != null) {
                sendReport(player, report);
            }
            PacketDistributor.sendToPlayer(player, new AdminPayloads.PerfState(false, 0, 0, false));
            return;
        }
        if (BlockProfiler.active) {
            PacketDistributor.sendToPlayer(player, new AdminPayloads.PerfState(true, 0, BlockProfiler.ticksLeft(), true));
            return;
        }
        int ticks = Math.min(request.seconds(), BitsmithCommand.MAX_PERF_SECONDS) * 20;
        BlockProfiler.start(ticks, report -> {
            if (!player.hasDisconnected()) {
                sendReport(player, report);
                PacketDistributor.sendToPlayer(player, new AdminPayloads.PerfState(false, 0, 0, false));
            }
        });
        PacketDistributor.sendToPlayer(player, new AdminPayloads.PerfState(true, ticks, ticks, false));
    }

    private static void sendReport(ServerPlayer player, BlockProfiler.Report report) {
        List<AdminPayloads.PerfRow> rows = new ArrayList<>();
        for (BlockProfiler.BlockCost block : report.blocks()) {
            if (rows.size() >= AdminPayloads.MAX_ROWS) {
                break;
            }
            rows.add(new AdminPayloads.PerfRow(block.dimension().location(), block.pos(), block.nanos(), block.calls()));
        }
        PacketDistributor.sendToPlayer(player, new AdminPayloads.PerfReport(report.ticks(), report.blocks().size(),
                report.totalNanos(), rows));
    }

    // Teleport

    private static void teleport(ServerPlayer player, AdminPayloads.Teleport request) {
        ServerLevel level = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, request.dimension()));
        if (level == null || !level.isInWorldBounds(request.pos())) {
            return;
        }
        BlockPos pos = request.pos();
        // On top of the block, so the player does not land inside it.
        player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, player.getYRot(), player.getXRot());
    }
}
