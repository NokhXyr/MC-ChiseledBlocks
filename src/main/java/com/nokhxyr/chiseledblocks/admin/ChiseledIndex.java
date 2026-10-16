/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.admin;

import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The carved blocks currently loaded on the server, per dimension. Kept up to date as block
 * entities load and unload, so a search only ever looks at carved blocks, never at the world block
 * by block, and never loads a chunk.
 */
public final class ChiseledIndex {
    private static final Map<ResourceKey<Level>, Long2ObjectMap<ChiseledBlockEntity>> LOADED = new HashMap<>();

    private ChiseledIndex() {
    }

    public static void add(ChiseledBlockEntity blockEntity) {
        if (blockEntity.getLevel() instanceof ServerLevel level) {
            LOADED.computeIfAbsent(level.dimension(), key -> new Long2ObjectOpenHashMap<>())
                    .put(blockEntity.getBlockPos().asLong(), blockEntity);
        }
    }

    public static void remove(ChiseledBlockEntity blockEntity) {
        if (blockEntity.getLevel() instanceof ServerLevel level) {
            Long2ObjectMap<ChiseledBlockEntity> blocks = LOADED.get(level.dimension());
            // Only if still this one: a new block entity may already have taken the position.
            if (blocks != null && blocks.get(blockEntity.getBlockPos().asLong()) == blockEntity) {
                blocks.remove(blockEntity.getBlockPos().asLong());
            }
        }
    }

    /** A snapshot of the loaded carved blocks of a dimension. */
    public static List<ChiseledBlockEntity> loaded(ServerLevel level) {
        Long2ObjectMap<ChiseledBlockEntity> blocks = LOADED.get(level.dimension());
        List<ChiseledBlockEntity> result = new ArrayList<>(blocks == null ? 0 : blocks.size());
        if (blocks != null) {
            for (ChiseledBlockEntity blockEntity : blocks.values()) {
                if (!blockEntity.isRemoved()) {
                    result.add(blockEntity);
                }
            }
        }
        return result;
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LOADED.clear();
    }
}
