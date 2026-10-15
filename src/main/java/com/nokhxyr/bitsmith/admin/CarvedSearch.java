/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.admin;

import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Searches among the loaded carved blocks of a dimension ({@link CarvedIndex}), shared by the
 * {@code /bitsmith} command and the admin screen. Never reads the world block by block and never
 * loads a chunk.
 */
public final class CarvedSearch {
    public static final int MAX_RADIUS_BLOCKS = 512;
    public static final int MAX_RADIUS_CHUNKS = 32;
    public static final int MAX_MAP_RADIUS = 16;

    /** Where to look around the centre. */
    public enum Scope {
        /** Within {@code radius} blocks. */
        BLOCKS,
        /** Within {@code radius} chunks of the centre's chunk. */
        CHUNKS,
        /** Every loaded chunk. */
        LOADED,
        /** One chunk, {@code chunkX}, {@code chunkZ}. */
        CHUNK;

        public static Scope byOrdinal(int ordinal) {
            Scope[] values = values();
            return values[Math.floorMod(ordinal, values.length)];
        }
    }

    public record Query(Scope scope, int radius, int chunkX, int chunkZ, @Nullable Block material) {
        public static Query blocks(int radius, @Nullable Block material) {
            return new Query(Scope.BLOCKS, radius, 0, 0, material);
        }

        public static Query chunks(int radius, @Nullable Block material) {
            return new Query(Scope.CHUNKS, radius, 0, 0, material);
        }

        public static Query loaded(@Nullable Block material) {
            return new Query(Scope.LOADED, 0, 0, 0, material);
        }
    }

    private CarvedSearch() {
    }

    /** Carved blocks matching the query, nearest to the centre first. */
    public static List<CarvedBlockEntity> run(ServerLevel level, Vec3 centre, Query query) {
        ChunkPos centreChunk = new ChunkPos(BlockPos.containing(centre));
        int radius = switch (query.scope()) {
            case BLOCKS -> Math.clamp(query.radius(), 1, MAX_RADIUS_BLOCKS);
            case CHUNKS -> Math.clamp(query.radius(), 0, MAX_RADIUS_CHUNKS);
            default -> 0;
        };
        double limit = (double) radius * radius;
        List<CarvedBlockEntity> found = new ArrayList<>();
        for (CarvedBlockEntity blockEntity : CarvedIndex.loaded(level)) {
            BlockPos pos = blockEntity.getBlockPos();
            boolean inside = switch (query.scope()) {
                case BLOCKS -> pos.getCenter().distanceToSqr(centre) <= limit;
                case CHUNKS -> Math.abs((pos.getX() >> 4) - centreChunk.x) <= radius
                        && Math.abs((pos.getZ() >> 4) - centreChunk.z) <= radius;
                case LOADED -> true;
                case CHUNK -> pos.getX() >> 4 == query.chunkX() && pos.getZ() >> 4 == query.chunkZ();
            };
            if (inside && (query.material() == null || contains(blockEntity.getGrid(), query.material()))) {
                found.add(blockEntity);
            }
        }
        found.sort(Comparator.comparingDouble(blockEntity -> blockEntity.getBlockPos().getCenter().distanceToSqr(centre)));
        return found;
    }

    public static boolean contains(BitGrid grid, Block material) {
        for (int i = 0; i < grid.paletteSize(); i++) {
            int entry = grid.paletteEntry(i);
            if (entry != BitGrid.AIR && Materials.state(entry).is(material)) {
                return true;
            }
        }
        return false;
    }

    /** The materials of a grid other than air, in palette order. */
    public static List<Block> materials(BitGrid grid) {
        List<Block> blocks = new ArrayList<>(grid.paletteSize());
        for (int i = 0; i < grid.paletteSize(); i++) {
            int entry = grid.paletteEntry(i);
            if (entry != BitGrid.AIR) {
                Block block = Materials.state(entry).getBlock();
                if (!blocks.contains(block)) {
                    blocks.add(block);
                }
            }
        }
        return blocks;
    }

    /**
     * The chunks around a centre: whether each is loaded, how many carved blocks it holds and the
     * one nearest to the centre. Cells are row by row, north to south, west to east.
     */
    public record ChunkMap(int centreX, int centreZ, int radius, boolean[] loaded, int[] counts, BlockPos[] nearest) {
        public int side() {
            return radius * 2 + 1;
        }

        public int cell(int chunkX, int chunkZ) {
            return (chunkZ - centreZ + radius) * side() + (chunkX - centreX + radius);
        }

        public int total() {
            int total = 0;
            for (int count : counts) {
                total += count;
            }
            return total;
        }
    }

    public static ChunkMap map(ServerLevel level, Vec3 centre, int requestedRadius) {
        int radius = Math.clamp(requestedRadius, 1, MAX_MAP_RADIUS);
        ChunkPos centreChunk = new ChunkPos(BlockPos.containing(centre));
        int side = radius * 2 + 1;
        ChunkMap map = new ChunkMap(centreChunk.x, centreChunk.z, radius, new boolean[side * side], new int[side * side],
                new BlockPos[side * side]);
        for (int z = 0; z < side; z++) {
            for (int x = 0; x < side; x++) {
                map.loaded()[z * side + x] = level.hasChunk(centreChunk.x - radius + x, centreChunk.z - radius + z);
            }
        }
        for (CarvedBlockEntity blockEntity : run(level, centre, Query.chunks(radius, null))) {
            BlockPos pos = blockEntity.getBlockPos();
            int cell = map.cell(pos.getX() >> 4, pos.getZ() >> 4);
            map.counts()[cell]++;
            if (map.nearest()[cell] == null) {
                map.nearest()[cell] = pos;
            }
        }
        return map;
    }
}
