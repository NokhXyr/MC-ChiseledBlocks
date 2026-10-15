/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.network;

import com.nokhxyr.bitsmith.Bitsmith;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Messages of the admin screen. Every request is checked on the server for operator rights
 * (level 2); answers carry plain data that the screen lays out.
 */
public final class AdminPayloads {
    /** Most rows sent back for a search or a profiling report. */
    public static final int MAX_ROWS = 300;
    private static final int MAX_MATERIALS_PER_ROW = 8;

    private AdminPayloads() {
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> newType(String path) {
        return new CustomPacketPayload.Type<>(Bitsmith.id("admin/" + path));
    }

    // Opening

    /** Server to client: open the admin screen. */
    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE = newType("open");
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.unit(new Open());

        @Override
        public Type<Open> type() {
            return TYPE;
        }
    }

    /** Client to server: the admin key was pressed, open the screen if allowed. */
    public record RequestOpen() implements CustomPacketPayload {
        public static final Type<RequestOpen> TYPE = newType("request_open");
        public static final StreamCodec<RegistryFriendlyByteBuf, RequestOpen> CODEC = StreamCodec.unit(new RequestOpen());

        @Override
        public Type<RequestOpen> type() {
            return TYPE;
        }
    }

    // Search

    /**
     * Client to server. {@code scope} is a {@code CarvedSearch.Scope} ordinal, {@code material}
     * a block id or empty, {@code target} is echoed back to tell apart the screen's lists.
     */
    public record FindRequest(int target, int scope, int radius, int chunkX, int chunkZ, String material)
            implements CustomPacketPayload {
        public static final Type<FindRequest> TYPE = newType("find");
        public static final StreamCodec<RegistryFriendlyByteBuf, FindRequest> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.target);
                    buf.writeVarInt(p.scope);
                    buf.writeVarInt(p.radius);
                    buf.writeVarInt(p.chunkX);
                    buf.writeVarInt(p.chunkZ);
                    buf.writeUtf(p.material, 256);
                },
                buf -> new FindRequest(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readUtf(256)));

        @Override
        public Type<FindRequest> type() {
            return TYPE;
        }
    }

    /** One carved block found: where, how far, how many bits, made of what. */
    public record Hit(BlockPos pos, float distance, int bits, List<ResourceLocation> materials) {
        static final StreamCodec<RegistryFriendlyByteBuf, Hit> CODEC = StreamCodec.of(
                (buf, hit) -> {
                    BlockPos.STREAM_CODEC.encode(buf, hit.pos);
                    buf.writeFloat(hit.distance);
                    buf.writeVarInt(hit.bits);
                    int count = Math.min(MAX_MATERIALS_PER_ROW, hit.materials.size());
                    buf.writeVarInt(count);
                    for (int i = 0; i < count; i++) {
                        ResourceLocation.STREAM_CODEC.encode(buf, hit.materials.get(i));
                    }
                },
                buf -> {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    float distance = buf.readFloat();
                    int bits = buf.readVarInt();
                    int count = Math.min(MAX_MATERIALS_PER_ROW, buf.readVarInt());
                    ResourceLocation[] materials = new ResourceLocation[count];
                    for (int i = 0; i < count; i++) {
                        materials[i] = ResourceLocation.STREAM_CODEC.decode(buf);
                    }
                    return new Hit(pos, distance, bits, List.of(materials));
                });
    }

    /** Server to client: the answer to a {@link FindRequest}. */
    public record FindResult(int target, ResourceLocation dimension, boolean unknownMaterial, int total, int chunks,
            List<Hit> hits) implements CustomPacketPayload {
        public static final Type<FindResult> TYPE = newType("find_result");
        public static final StreamCodec<RegistryFriendlyByteBuf, FindResult> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.target);
                    buf.writeResourceLocation(p.dimension);
                    buf.writeBoolean(p.unknownMaterial);
                    buf.writeVarInt(p.total);
                    buf.writeVarInt(p.chunks);
                    buf.writeVarInt(Math.min(MAX_ROWS, p.hits.size()));
                    for (int i = 0; i < Math.min(MAX_ROWS, p.hits.size()); i++) {
                        Hit.CODEC.encode(buf, p.hits.get(i));
                    }
                },
                buf -> {
                    int target = buf.readVarInt();
                    ResourceLocation dimension = buf.readResourceLocation();
                    boolean unknown = buf.readBoolean();
                    int total = buf.readVarInt();
                    int chunks = buf.readVarInt();
                    int count = Math.min(MAX_ROWS, buf.readVarInt());
                    Hit[] hits = new Hit[count];
                    for (int i = 0; i < count; i++) {
                        hits[i] = Hit.CODEC.decode(buf);
                    }
                    return new FindResult(target, dimension, unknown, total, chunks, List.of(hits));
                });

        @Override
        public Type<FindResult> type() {
            return TYPE;
        }
    }

    // Chunk map

    public record MapRequest(int radius) implements CustomPacketPayload {
        public static final Type<MapRequest> TYPE = newType("map");
        public static final StreamCodec<RegistryFriendlyByteBuf, MapRequest> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeVarInt(p.radius), buf -> new MapRequest(buf.readVarInt()));

        @Override
        public Type<MapRequest> type() {
            return TYPE;
        }
    }

    /** Chunks around the player, row by row from the north west corner. */
    public record MapResult(int centreX, int centreZ, int radius, boolean[] loaded, int[] counts)
            implements CustomPacketPayload {
        public static final Type<MapResult> TYPE = newType("map_result");
        public static final StreamCodec<RegistryFriendlyByteBuf, MapResult> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.centreX);
                    buf.writeVarInt(p.centreZ);
                    buf.writeVarInt(p.radius);
                    for (int i = 0; i < p.loaded.length; i++) {
                        buf.writeBoolean(p.loaded[i]);
                        buf.writeVarInt(p.counts[i]);
                    }
                },
                buf -> {
                    int centreX = buf.readVarInt();
                    int centreZ = buf.readVarInt();
                    int radius = Math.clamp(buf.readVarInt(), 1, 64);
                    int cells = (radius * 2 + 1) * (radius * 2 + 1);
                    boolean[] loaded = new boolean[cells];
                    int[] counts = new int[cells];
                    for (int i = 0; i < cells; i++) {
                        loaded[i] = buf.readBoolean();
                        counts[i] = buf.readVarInt();
                    }
                    return new MapResult(centreX, centreZ, radius, loaded, counts);
                });

        public int side() {
            return radius * 2 + 1;
        }

        @Override
        public Type<MapResult> type() {
            return TYPE;
        }
    }

    // Profiling

    /** Client to server: start a window of that many seconds, or stop the current one with 0. */
    public record PerfRequest(int seconds) implements CustomPacketPayload {
        public static final Type<PerfRequest> TYPE = newType("perf");
        public static final StreamCodec<RegistryFriendlyByteBuf, PerfRequest> CODEC = StreamCodec.of(
                (buf, p) -> buf.writeVarInt(p.seconds), buf -> new PerfRequest(buf.readVarInt()));

        @Override
        public Type<PerfRequest> type() {
            return TYPE;
        }
    }

    /** Server to client: whether a window runs, its length and what is left, in ticks. */
    public record PerfState(boolean running, int totalTicks, int ticksLeft, boolean busy) implements CustomPacketPayload {
        public static final Type<PerfState> TYPE = newType("perf_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, PerfState> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeBoolean(p.running);
                    buf.writeVarInt(p.totalTicks);
                    buf.writeVarInt(p.ticksLeft);
                    buf.writeBoolean(p.busy);
                },
                buf -> new PerfState(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean()));

        @Override
        public Type<PerfState> type() {
            return TYPE;
        }
    }

    /** One block of a report: nanoseconds and calls per kind of work, in {@code Kind} order. */
    public record PerfRow(ResourceLocation dimension, BlockPos pos, long[] nanos, long[] calls) {
        static final StreamCodec<RegistryFriendlyByteBuf, PerfRow> CODEC = StreamCodec.of(
                (buf, row) -> {
                    ResourceLocation.STREAM_CODEC.encode(buf, row.dimension);
                    BlockPos.STREAM_CODEC.encode(buf, row.pos);
                    buf.writeVarInt(row.nanos.length);
                    for (int i = 0; i < row.nanos.length; i++) {
                        buf.writeVarLong(row.nanos[i]);
                        buf.writeVarLong(row.calls[i]);
                    }
                },
                buf -> {
                    ResourceLocation dimension = ResourceLocation.STREAM_CODEC.decode(buf);
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    int kinds = Math.min(16, buf.readVarInt());
                    long[] nanos = new long[kinds];
                    long[] calls = new long[kinds];
                    for (int i = 0; i < kinds; i++) {
                        nanos[i] = buf.readVarLong();
                        calls[i] = buf.readVarLong();
                    }
                    return new PerfRow(dimension, pos, nanos, calls);
                });

        public long totalNanos() {
            long total = 0;
            for (long value : nanos) {
                total += value;
            }
            return total;
        }
    }

    public record PerfReport(int ticks, int blocks, long totalNanos, List<PerfRow> rows) implements CustomPacketPayload {
        public static final Type<PerfReport> TYPE = newType("perf_report");
        public static final StreamCodec<RegistryFriendlyByteBuf, PerfReport> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.ticks);
                    buf.writeVarInt(p.blocks);
                    buf.writeVarLong(p.totalNanos);
                    buf.writeVarInt(Math.min(MAX_ROWS, p.rows.size()));
                    for (int i = 0; i < Math.min(MAX_ROWS, p.rows.size()); i++) {
                        PerfRow.CODEC.encode(buf, p.rows.get(i));
                    }
                },
                buf -> {
                    int ticks = buf.readVarInt();
                    int blocks = buf.readVarInt();
                    long total = buf.readVarLong();
                    int count = Math.min(MAX_ROWS, buf.readVarInt());
                    PerfRow[] rows = new PerfRow[count];
                    for (int i = 0; i < count; i++) {
                        rows[i] = PerfRow.CODEC.decode(buf);
                    }
                    return new PerfReport(ticks, blocks, total, List.of(rows));
                });

        /** Microseconds per tick over the window. */
        public double perTick(long nanos) {
            return ticks == 0 ? 0 : nanos / 1000.0 / ticks;
        }

        @Override
        public Type<PerfReport> type() {
            return TYPE;
        }
    }

    // Teleport

    /** Client to server: teleport on top of a block. */
    public record Teleport(ResourceLocation dimension, BlockPos pos) implements CustomPacketPayload {
        public static final Type<Teleport> TYPE = newType("teleport");
        public static final StreamCodec<RegistryFriendlyByteBuf, Teleport> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeResourceLocation(p.dimension);
                    buf.writeBlockPos(p.pos);
                },
                buf -> new Teleport(buf.readResourceLocation(), buf.readBlockPos()));

        @Override
        public Type<Teleport> type() {
            return TYPE;
        }
    }
}
