/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.edit;

import com.nokhxyr.chiseledblocks.block.Materials;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per player undo and redo of carving actions, kept in memory for the server session.
 *
 * <p>An entry is applied only if the block still holds exactly what the action left there, so an
 * undo never overwrites someone else's later work. In survival the bits follow: undoing a stroke
 * takes back the bits it gave, undoing a placement gives the bit back, and redo does the reverse.
 */
public final class EditHistory {
    private static final int MAX_ENTRIES = 64;
    private static final double MAX_DISTANCE_SQUARED = 64 * 64;
    private static final Map<UUID, EditHistory> HISTORIES = new HashMap<>();

    /** What one block held before and after an action. */
    public record Change(BlockPos pos, BlockSnapshot before, BlockSnapshot after) {
    }

    /**
     * One action, on one block or on several (a line or a box of bits) undone together.
     *
     * @param bitsGained bits the player received from the action, by material id; negative when
     *                   the action consumed bits
     */
    public record Entry(ResourceKey<Level> dimension, List<Change> changes, Int2IntMap bitsGained) {
        public BlockPos pos() {
            return changes.get(0).pos();
        }
    }

    private final Deque<Entry> undo = new ArrayDeque<>();
    private final Deque<Entry> redo = new ArrayDeque<>();

    private EditHistory() {
    }

    public static EditHistory of(ServerPlayer player) {
        return HISTORIES.computeIfAbsent(player.getUUID(), id -> new EditHistory());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        HISTORIES.clear();
    }

    /** Records a finished action on one block. A new action makes the redo history obsolete. */
    public static void record(ServerPlayer player, BlockPos pos, BlockSnapshot before, BlockSnapshot after,
                              Int2IntMap bitsGained) {
        record(player, List.of(new Change(pos.immutable(), before, after)), bitsGained);
    }

    /** Records a finished action on several blocks, undone and redone as one. */
    public static void record(ServerPlayer player, List<Change> changes, Int2IntMap bitsGained) {
        List<Change> real = changes.stream().filter(change -> !change.before().equals(change.after())).toList();
        if (real.isEmpty()) {
            return;
        }
        EditHistory history = of(player);
        history.redo.clear();
        history.undo.push(new Entry(player.level().dimension(), real, new Int2IntOpenHashMap(bitsGained)));
        while (history.undo.size() > MAX_ENTRIES) {
            history.undo.removeLast();
        }
    }

    public static void undo(ServerPlayer player) {
        EditHistory history = of(player);
        step(player, history.undo, history.redo, true);
    }

    public static void redo(ServerPlayer player) {
        EditHistory history = of(player);
        step(player, history.redo, history.undo, false);
    }

    public int undoSize() {
        return undo.size();
    }

    public int redoSize() {
        return redo.size();
    }

    private static void step(ServerPlayer player, Deque<Entry> from, Deque<Entry> to, boolean undoing) {
        Entry entry = from.peek();
        String key = undoing ? "undo" : "redo";
        if (entry == null) {
            player.displayClientMessage(Component.translatable("chiseledblocks." + key + ".nothing"), true);
            return;
        }
        ServerLevel level = player.server.getLevel(entry.dimension());
        if (level == null || level != player.level()
                || !level.isLoaded(entry.pos())
                || player.distanceToSqr(entry.pos().getCenter()) > MAX_DISTANCE_SQUARED
                || !player.mayBuild()
                || !level.mayInteract(player, entry.pos())) {
            player.displayClientMessage(Component.translatable("chiseledblocks." + key + ".too_far"), true);
            return;
        }

        // Every block must still hold what the action left there, or nothing is touched.
        for (Change change : entry.changes()) {
            BlockSnapshot expected = undoing ? change.after() : change.before();
            if (!level.isLoaded(change.pos()) || !level.mayInteract(player, change.pos())
                    || !BlockSnapshot.current(level, change.pos()).equals(expected)) {
                from.pop();
                player.displayClientMessage(Component.translatable("chiseledblocks." + key + ".changed"), true);
                return;
            }
        }

        // Undo gives back what the action took and takes back what it gave; redo the opposite.
        int sign = undoing ? -1 : 1;
        if (!player.getAbilities().instabuild) {
            for (Int2IntMap.Entry bits : entry.bitsGained().int2IntEntrySet()) {
                int change = sign * bits.getIntValue();
                if (change < 0 && PixelInventory.count(player, Materials.state(bits.getIntKey())) < -change) {
                    BlockState material = Materials.state(bits.getIntKey());
                    player.displayClientMessage(Component.translatable("chiseledblocks." + key + ".missing_bits",
                            -change, material.getBlock().getName()), true);
                    return;
                }
            }
            for (Int2IntMap.Entry bits : entry.bitsGained().int2IntEntrySet()) {
                int change = sign * bits.getIntValue();
                BlockState material = Materials.state(bits.getIntKey());
                if (change > 0) {
                    PixelInventory.give(player, material, change);
                } else if (change < 0) {
                    PixelInventory.take(player, material, -change);
                }
            }
        }

        for (Change change : entry.changes()) {
            (undoing ? change.before() : change.after()).apply(level, change.pos());
        }
        to.push(from.pop());
        player.displayClientMessage(Component.translatable("chiseledblocks." + key + ".done", from.size()), true);
    }
}
