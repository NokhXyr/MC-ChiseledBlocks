/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.edit;

import com.nokhxyr.bitsmith.admin.BlockProfiler;
import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.metrics.Metrics;
import com.nokhxyr.bitsmith.network.GridDeltaPayload;
import com.nokhxyr.bitsmith.network.GridFullPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Applies every pending carved block edit once per server tick: one pack, one save mark and
 * one network update per block, however many bits changed during the tick.
 */
public final class EditBatcher {
    private static final Set<CarvedBlockEntity> DIRTY = new LinkedHashSet<>();
    private static final Set<CarvedBlockEntity> EMPTY_CHECKS = new LinkedHashSet<>();

    private EditBatcher() {
    }

    public static void enqueue(CarvedBlockEntity blockEntity) {
        DIRTY.add(blockEntity);
    }

    /** Asks for the block to be removed at the end of the tick if it is still empty by then. */
    public static void checkEmpty(CarvedBlockEntity blockEntity) {
        EMPTY_CHECKS.add(blockEntity);
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (!Metrics.ENABLED) {
            flushTick();
            return;
        }
        long start = System.nanoTime();
        flushTick();
        Metrics.FLUSH_NANOS.addAndGet(System.nanoTime() - start);
        Metrics.FLUSH_TICKS.incrementAndGet();
    }

    private static void flushTick() {
        if (!EMPTY_CHECKS.isEmpty()) {
            CarvedBlockEntity[] checks = EMPTY_CHECKS.toArray(CarvedBlockEntity[]::new);
            EMPTY_CHECKS.clear();
            for (CarvedBlockEntity blockEntity : checks) {
                if (!blockEntity.isRemoved() && blockEntity.effectiveGrid().isEmpty()
                        && blockEntity.getLevel() instanceof ServerLevel level) {
                    level.removeBlock(blockEntity.getBlockPos(), false);
                }
            }
        }
        if (DIRTY.isEmpty()) {
            return;
        }
        CarvedBlockEntity[] batch = DIRTY.toArray(CarvedBlockEntity[]::new);
        DIRTY.clear();
        for (CarvedBlockEntity blockEntity : batch) {
            if (BlockProfiler.active && blockEntity.getLevel() != null) {
                long start = System.nanoTime();
                flush(blockEntity);
                BlockProfiler.record(blockEntity.getLevel(), blockEntity.getBlockPos(), BlockProfiler.Kind.APPLY,
                        System.nanoTime() - start);
            } else {
                flush(blockEntity);
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        DIRTY.clear();
        EMPTY_CHECKS.clear();
    }

    private static void flush(CarvedBlockEntity blockEntity) {
        BitGridBuilder builder = blockEntity.takePending();
        if (builder == null || blockEntity.isRemoved() || !(blockEntity.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BitGrid before = blockEntity.getGrid();
        BitGrid after = builder.build();
        if (after.equals(before)) {
            return;
        }

        BlockPos pos = blockEntity.getBlockPos();
        if (after.isEmpty()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return;
        }
        if (after.isUniformSolid()) {
            level.setBlock(pos, Materials.state(after.paletteEntry(0)), Block.UPDATE_ALL);
            return;
        }

        blockEntity.setGrid(after);
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos), update(pos, builder, after));
    }

    /** A delta when it is smaller than the whole grid, the whole grid otherwise. */
    private static CustomPacketPayload update(BlockPos pos, BitGridBuilder builder, BitGrid after) {
        int[] indices = builder.changedIndices();
        // A delta entry costs about 4 bytes, the full grid its packed payload.
        if (indices.length * 4 < after.payloadBytes()) {
            int[] materials = new int[indices.length];
            for (int i = 0; i < indices.length; i++) {
                materials[i] = builder.get(indices[i]);
            }
            return new GridDeltaPayload(pos, indices, materials, after.hashCode());
        }
        return GridFullPayload.of(pos, after);
    }
}
