/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.admin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * {@code /bitsmith}: finds carved blocks (in a radius of blocks or chunks, or in every loaded
 * chunk), draws a map of the loaded chunks around the player, and measures Bitsmith's server time
 * per block. Every result carries a link that teleports to it. Operators only (level 2).
 */
public final class BitsmithCommand {
    /** Results listed in the chat, nearest first. */
    static final int LISTED = 10;
    private static final int MAX_RADIUS_BLOCKS = 512;
    private static final int MAX_RADIUS_CHUNKS = 32;
    private static final int MAX_MAP_RADIUS = 12;
    private static final int DEFAULT_MAP_RADIUS = 7;
    private static final int DEFAULT_PERF_SECONDS = 10;
    private static final int MAX_PERF_SECONDS = 300;
    private static final String CELL = "█";

    private BitsmithCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher(), event.getBuildContext());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("bitsmith")
                .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("find")
                        .then(Commands.literal("radius").then(withMaterial(context,
                                Commands.argument("blocks", IntegerArgumentType.integer(1, MAX_RADIUS_BLOCKS)),
                                (ctx, material) -> find(ctx.getSource(), Area.blocks(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "blocks")), material))))
                        .then(Commands.literal("chunks").then(withMaterial(context,
                                Commands.argument("chunks", IntegerArgumentType.integer(0, MAX_RADIUS_CHUNKS)),
                                (ctx, material) -> find(ctx.getSource(), Area.chunks(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "chunks")), material))))
                        .then(withMaterial(context, Commands.literal("loaded"),
                                (ctx, material) -> find(ctx.getSource(), Area.LOADED, material))))
                .then(Commands.literal("map")
                        .executes(ctx -> map(ctx.getSource(), DEFAULT_MAP_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, MAX_MAP_RADIUS))
                                .executes(ctx -> map(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("perf")
                        .executes(ctx -> perfStart(ctx.getSource(), DEFAULT_PERF_SECONDS))
                        .then(Commands.literal("stop").executes(ctx -> perfStop(ctx.getSource())))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, MAX_PERF_SECONDS))
                                .executes(ctx -> perfStart(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds"))))));
    }

    private interface FindAction {
        int run(CommandContext<CommandSourceStack> context, @Nullable Block material) throws CommandSyntaxException;
    }

    /** Makes a find node runnable as is, and with a trailing material filter. */
    private static <T extends ArgumentBuilder<CommandSourceStack, T>> T withMaterial(CommandBuildContext context,
            T node, FindAction action) {
        return node.executes(ctx -> action.run(ctx, null))
                .then(Commands.argument("material", ResourceArgument.resource(context, Registries.BLOCK))
                        .executes(ctx -> action.run(ctx,
                                ResourceArgument.getResource(ctx, "material", Registries.BLOCK).value())));
    }

    // Searching

    /** Where to search. Positions are tested against carved blocks of the source's dimension. */
    record Area(Component description, Predicate<BlockPos> contains) {
        static final Area LOADED = new Area(Component.translatable("bitsmith.find.area.loaded"), pos -> true);

        static Area blocks(CommandSourceStack source, int radius) {
            Vec3 centre = source.getPosition();
            double limit = (double) radius * radius;
            return new Area(Component.translatable("bitsmith.find.area.blocks", radius),
                    pos -> pos.getCenter().distanceToSqr(centre) <= limit);
        }

        static Area chunks(CommandSourceStack source, int radius) {
            ChunkPos centre = new ChunkPos(BlockPos.containing(source.getPosition()));
            return new Area(Component.translatable("bitsmith.find.area.chunks", radius),
                    pos -> Math.abs((pos.getX() >> 4) - centre.x) <= radius
                            && Math.abs((pos.getZ() >> 4) - centre.z) <= radius);
        }
    }

    /** Carved blocks of the source's dimension in the area, nearest first. */
    static List<CarvedBlockEntity> search(CommandSourceStack source, Area area, @Nullable Block material) {
        Vec3 centre = source.getPosition();
        List<CarvedBlockEntity> found = new ArrayList<>();
        for (CarvedBlockEntity blockEntity : CarvedIndex.loaded(source.getLevel())) {
            if (area.contains().test(blockEntity.getBlockPos())
                    && (material == null || contains(blockEntity.getGrid(), material))) {
                found.add(blockEntity);
            }
        }
        found.sort(Comparator.comparingDouble(blockEntity -> blockEntity.getBlockPos().getCenter().distanceToSqr(centre)));
        return found;
    }

    private static boolean contains(BitGrid grid, Block material) {
        for (int i = 0; i < grid.paletteSize(); i++) {
            int entry = grid.paletteEntry(i);
            if (entry != BitGrid.AIR && Materials.state(entry).is(material)) {
                return true;
            }
        }
        return false;
    }

    private static int find(CommandSourceStack source, Area area, @Nullable Block material) {
        List<CarvedBlockEntity> found = search(source, area, material);
        Long2IntOpenHashMap perChunk = new Long2IntOpenHashMap();
        for (CarvedBlockEntity blockEntity : found) {
            perChunk.addTo(ChunkPos.asLong(blockEntity.getBlockPos()), 1);
        }
        MutableComponent where = area.description().copy();
        if (material != null) {
            where = Component.translatable("bitsmith.find.area.material", where, material.getName());
        }
        Component header = Component.translatable("bitsmith.find.header", where, found.size(), perChunk.size())
                .withStyle(ChatFormatting.GOLD);
        source.sendSuccess(() -> header, false);

        Vec3 centre = source.getPosition();
        for (int i = 0; i < Math.min(LISTED, found.size()); i++) {
            CarvedBlockEntity blockEntity = found.get(i);
            BlockPos pos = blockEntity.getBlockPos();
            Component line = Component.empty()
                    .append(teleportLink(source.getLevel().dimension(), pos))
                    .append(" ")
                    .append(Component.translatable("bitsmith.find.entry", pos.getX(), pos.getY(), pos.getZ(),
                            Math.round(Math.sqrt(pos.getCenter().distanceToSqr(centre))), materials(blockEntity.getGrid())));
            source.sendSuccess(() -> line, false);
        }
        if (found.size() > LISTED) {
            source.sendSuccess(() -> Component.translatable("bitsmith.find.more", found.size() - LISTED)
                    .withStyle(ChatFormatting.GRAY), false);
        }
        if (area == Area.LOADED && !perChunk.isEmpty()) {
            sendBusiestChunks(source, found, perChunk);
        }
        return found.size();
    }

    /** The chunks holding the most carved blocks, each with a link to its nearest one. */
    private static void sendBusiestChunks(CommandSourceStack source, List<CarvedBlockEntity> found, Long2IntOpenHashMap perChunk) {
        Long2ObjectOpenHashMap<BlockPos> nearest = new Long2ObjectOpenHashMap<>();
        for (CarvedBlockEntity blockEntity : found) {
            nearest.putIfAbsent(ChunkPos.asLong(blockEntity.getBlockPos()), blockEntity.getBlockPos());
        }
        List<Long> chunks = new ArrayList<>(perChunk.keySet());
        chunks.sort(Comparator.comparingInt((Long chunk) -> perChunk.get((long) chunk)).reversed());
        source.sendSuccess(() -> Component.translatable("bitsmith.find.chunks").withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < Math.min(LISTED, chunks.size()); i++) {
            long chunk = chunks.get(i);
            Component line = Component.empty()
                    .append(teleportLink(source.getLevel().dimension(), nearest.get(chunk)))
                    .append(" ")
                    .append(Component.translatable("bitsmith.find.chunk", ChunkPos.getX(chunk), ChunkPos.getZ(chunk),
                            perChunk.get(chunk)));
            source.sendSuccess(() -> line, false);
        }
    }

    /** Up to three material names, then how many more, and the number of bits. */
    private static Component materials(BitGrid grid) {
        MutableComponent names = Component.empty();
        int shown = 0;
        int materials = 0;
        for (int i = 0; i < grid.paletteSize(); i++) {
            int entry = grid.paletteEntry(i);
            if (entry == BitGrid.AIR) {
                continue;
            }
            materials++;
            if (shown < 3) {
                if (shown > 0) {
                    names.append(", ");
                }
                names.append(Materials.state(entry).getBlock().getName());
                shown++;
            }
        }
        if (materials > shown) {
            names.append(" +" + (materials - shown));
        }
        return Component.translatable("bitsmith.find.materials", names, grid.filledCount());
    }

    // Map of the loaded chunks

    private static int map(CommandSourceStack source, int radius) {
        ServerLevel level = source.getLevel();
        ChunkPos centre = new ChunkPos(BlockPos.containing(source.getPosition()));
        Long2IntOpenHashMap perChunk = new Long2IntOpenHashMap();
        Long2ObjectOpenHashMap<BlockPos> nearest = new Long2ObjectOpenHashMap<>();
        for (CarvedBlockEntity blockEntity : search(source, Area.chunks(source, radius), null)) {
            long chunk = ChunkPos.asLong(blockEntity.getBlockPos());
            perChunk.addTo(chunk, 1);
            nearest.putIfAbsent(chunk, blockEntity.getBlockPos());
        }
        int loaded = 0;
        source.sendSuccess(() -> Component.translatable("bitsmith.map.header", centre.x, centre.z, radius)
                .withStyle(ChatFormatting.GOLD), false);
        for (int z = centre.z - radius; z <= centre.z + radius; z++) {
            MutableComponent row = Component.empty();
            for (int x = centre.x - radius; x <= centre.x + radius; x++) {
                long chunk = ChunkPos.asLong(x, z);
                boolean isLoaded = level.hasChunk(x, z);
                if (isLoaded) {
                    loaded++;
                }
                int count = perChunk.get(chunk);
                boolean here = x == centre.x && z == centre.z;
                Component hover = Component.translatable(isLoaded ? "bitsmith.map.cell.loaded" : "bitsmith.map.cell.unloaded",
                        x, z, count);
                Style style = Style.EMPTY.withColor(cellColour(isLoaded, count, here))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover));
                BlockPos target = nearest.get(chunk);
                if (target != null) {
                    style = style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                            teleportCommand(level.dimension(), target)));
                }
                row.append(Component.literal(CELL).withStyle(style));
            }
            source.sendSuccess(() -> row, false);
        }
        int loadedChunks = loaded;
        int total = perChunk.values().intStream().sum();
        source.sendSuccess(() -> Component.translatable("bitsmith.map.legend",
                Component.literal(CELL).withStyle(ChatFormatting.AQUA),
                Component.literal(CELL).withStyle(ChatFormatting.DARK_GRAY),
                Component.literal(CELL).withStyle(ChatFormatting.GRAY),
                Component.literal(CELL).withStyle(ChatFormatting.GREEN),
                Component.literal(CELL).withStyle(ChatFormatting.YELLOW),
                Component.literal(CELL).withStyle(ChatFormatting.RED)).withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.translatable("bitsmith.map.summary", loadedChunks, total, perChunk.size())
                .withStyle(ChatFormatting.GRAY), false);
        return total;
    }

    static ChatFormatting cellColour(boolean loaded, int count, boolean here) {
        if (here) {
            return ChatFormatting.AQUA;
        }
        if (!loaded) {
            return ChatFormatting.DARK_GRAY;
        }
        if (count == 0) {
            return ChatFormatting.GRAY;
        }
        return count < 10 ? ChatFormatting.GREEN : count < 64 ? ChatFormatting.YELLOW : ChatFormatting.RED;
    }

    // Performance

    private static int perfStart(CommandSourceStack source, int seconds) {
        if (BlockProfiler.active) {
            source.sendFailure(Component.translatable("bitsmith.perf.already", (BlockProfiler.ticksLeft() + 19) / 20));
            return 0;
        }
        BlockProfiler.start(seconds * 20, report -> sendReport(source, report));
        source.sendSuccess(() -> Component.translatable("bitsmith.perf.started", seconds).withStyle(ChatFormatting.GOLD), true);
        return 1;
    }

    private static int perfStop(CommandSourceStack source) {
        BlockProfiler.Report report = BlockProfiler.stop();
        if (report == null) {
            source.sendFailure(Component.translatable("bitsmith.perf.none"));
            return 0;
        }
        sendReport(source, report);
        return 1;
    }

    private static void sendReport(CommandSourceStack source, BlockProfiler.Report report) {
        source.sendSuccess(() -> Component.translatable("bitsmith.perf.header", report.ticks(),
                format(report.perTick(report.totalNanos())), report.blocks().size()).withStyle(ChatFormatting.GOLD), false);
        if (report.blocks().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("bitsmith.perf.empty").withStyle(ChatFormatting.GRAY), false);
            return;
        }
        for (int i = 0; i < Math.min(LISTED, report.blocks().size()); i++) {
            BlockProfiler.BlockCost block = report.blocks().get(i);
            MutableComponent breakdown = Component.empty();
            for (BlockProfiler.Kind kind : BlockProfiler.Kind.values()) {
                if (block.calls()[kind.ordinal()] == 0) {
                    continue;
                }
                if (!breakdown.getSiblings().isEmpty()) {
                    breakdown.append(", ");
                }
                breakdown.append(Component.translatable("bitsmith.perf.kind", kindName(kind),
                        format(report.perTick(block.nanos()[kind.ordinal()])), block.calls()[kind.ordinal()]));
            }
            BlockPos pos = block.pos();
            Component line = Component.empty()
                    .append(teleportLink(block.dimension(), pos))
                    .append(" ")
                    .append(Component.translatable("bitsmith.perf.entry", pos.getX(), pos.getY(), pos.getZ(),
                            format(report.perTick(block.totalNanos())), breakdown));
            source.sendSuccess(() -> line, false);
        }
        if (report.blocks().size() > LISTED) {
            source.sendSuccess(() -> Component.translatable("bitsmith.find.more", report.blocks().size() - LISTED)
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }

    private static Component kindName(BlockProfiler.Kind kind) {
        return Component.translatable("bitsmith.perf.kind." + kind.name().toLowerCase(Locale.ROOT));
    }

    private static String format(double microseconds) {
        return String.format(Locale.ROOT, microseconds < 10 ? "%.2f" : "%.1f", microseconds);
    }

    // Links

    static String teleportCommand(ResourceKey<Level> dimension, BlockPos pos) {
        // On top of the block, so the player does not land inside it.
        return "/execute in " + dimension.location() + " run tp @s " + (pos.getX() + 0.5) + " " + (pos.getY() + 1) + " "
                + (pos.getZ() + 0.5);
    }

    static Component teleportLink(ResourceKey<Level> dimension, BlockPos pos) {
        return Component.translatable("bitsmith.link.tp").withStyle(Style.EMPTY
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, teleportCommand(dimension, pos)))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("bitsmith.link.tp.hover", pos.getX(), pos.getY(), pos.getZ()))));
    }
}
