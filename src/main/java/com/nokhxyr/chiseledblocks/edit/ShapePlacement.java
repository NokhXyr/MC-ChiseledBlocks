/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.edit;

import com.nokhxyr.chiseledblocks.block.Materials;
import com.nokhxyr.chiseledblocks.core.edit.PlacementMode;
import com.nokhxyr.chiseledblocks.core.edit.PlacementShape;
import com.nokhxyr.chiseledblocks.core.edit.Symmetry;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGridBuilder;
import com.nokhxyr.chiseledblocks.item.BagContents;
import com.nokhxyr.chiseledblocks.item.PixelBagItem;
import com.nokhxyr.chiseledblocks.item.PixelItem;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Server side of the placement modes: puts bits of the held material on every empty bit of a
 * shape (a line, a rectangle, a box, or one bit while drawing), across as many blocks as it
 * crosses, as one action for undo. The bits come from the inventory and bags like any other
 * placement; in survival the whole shape is refused when bits are missing.
 */
public final class ShapePlacement {
    /** Placements one player may send per tick (drawing sends one per tick). */
    private static final int MAX_PER_TICK = 4;
    /** Extra reach for the ends of a shape, in blocks. */
    private static final double REACH_MARGIN = 2.0;
    private static final Map<ServerPlayer, long[]> BUDGET = new WeakHashMap<>();

    private ShapePlacement() {
    }

    /** The material placed with the item in hand: the bits' own, or the bag's selected one. */
    static @Nullable BlockState materialOf(ItemStack held) {
        if (held.getItem() instanceof PixelItem) {
            return PixelItem.material(held);
        }
        if (held.getItem() instanceof PixelBagItem) {
            BagContents.Entry entry = PixelBagItem.contents(held).selectedEntry();
            return entry == null ? null : entry.material();
        }
        return null;
    }

    public static void place(ServerPlayer player, PlacementMode mode, int[] start, int[] end, int axis) {
        BlockState material = materialOf(player.getMainHandItem());
        ServerLevel level = player.serverLevel();
        if (material == null || !Materials.isValidMaterial(material) || !player.mayBuild() || !takeBudget(player)) {
            return;
        }
        if (!inReach(player, start) || (mode.twoPoints() && !inReach(player, end))) {
            return;
        }
        if (!PlacementShape.fits(mode, start, end, Math.floorMod(axis, 3))) {
            player.displayClientMessage(Component.translatable("chiseledblocks.place.too_big", PlacementShape.MAX_PIXELS), true);
            return;
        }

        // The shape's empty bits, block by block, with their mirror images inside each block.
        Symmetry symmetry = PlayerSymmetry.of(player);
        Long2ObjectLinkedOpenHashMap<IntLinkedOpenHashSet> bitsByBlock = new Long2ObjectLinkedOpenHashMap<>();
        Long2ObjectLinkedOpenHashMap<PixelGrid> bases = new Long2ObjectLinkedOpenHashMap<>();
        PlacementShape.forEach(mode, start, end, Math.floorMod(axis, 3), (x, y, z) -> {
            BlockPos pos = new BlockPos(x >> 4, y >> 4, z >> 4);
            long key = pos.asLong();
            if (!bases.containsKey(key)) {
                boolean usable = level.isInWorldBounds(pos) && level.isLoaded(pos) && level.mayInteract(player, pos);
                bases.put(key, usable ? PixelPlacement.baseAt(level, pos) : null);
            }
            PixelGrid base = bases.get(key);
            if (base == null) {
                return;
            }
            int voxel = PixelGrid.index(x & 15, y & 15, z & 15);
            symmetry.forEachImage(voxel, image -> {
                if (base.get(image) == PixelGrid.AIR && !PixelPlacement.blockedByEntity(level, pos, image)) {
                    bitsByBlock.computeIfAbsent(key, k -> new IntLinkedOpenHashSet()).add(image);
                }
            });
        });
        int total = 0;
        for (IntLinkedOpenHashSet bits : bitsByBlock.values()) {
            total += bits.size();
        }
        if (total == 0) {
            return;
        }

        boolean survival = !player.getAbilities().instabuild;
        if (survival) {
            int available = PixelInventory.count(player, material);
            if (available < total) {
                player.displayClientMessage(Component.translatable("chiseledblocks.place.missing", total - available,
                        material.getBlock().getName(), total), true);
                return;
            }
        }

        int id = Materials.id(material);
        List<EditHistory.Change> changes = new ArrayList<>();
        for (Long2ObjectLinkedOpenHashMap.Entry<IntLinkedOpenHashSet> entry : bitsByBlock.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.of(entry.getLongKey());
            PixelGridBuilder result = bases.get(entry.getLongKey()).toBuilder();
            entry.getValue().forEach(index -> result.set(index, id));
            BlockSnapshot before = BlockSnapshot.current(level, pos);
            BlockSnapshot after = BlockSnapshot.of(result.build());
            after.apply(level, pos);
            changes.add(new EditHistory.Change(pos, before, after));
        }

        Int2IntOpenHashMap spent = new Int2IntOpenHashMap();
        if (survival) {
            PixelInventory.take(player, material, total);
            spent.put(id, -total);
        }
        EditHistory.record(player, changes, spent);
        SoundType sound = material.getSoundType();
        BlockPos at = new BlockPos(end[0] >> 4, end[1] >> 4, end[2] >> 4);
        level.playSound(null, mode.twoPoints() ? at : changes.get(0).pos(), sound.getPlaceSound(), SoundSource.BLOCKS,
                0.5F, sound.getPitch() * 1.4F);
    }

    private static boolean inReach(ServerPlayer player, int[] bit) {
        return player.canInteractWithBlock(new BlockPos(bit[0] >> 4, bit[1] >> 4, bit[2] >> 4), REACH_MARGIN);
    }

    private static boolean takeBudget(ServerPlayer player) {
        long[] budget = BUDGET.computeIfAbsent(player, p -> new long[2]);
        long now = player.level().getGameTime();
        if (budget[0] != now) {
            budget[0] = now;
            budget[1] = 0;
        }
        return budget[1]++ < MAX_PER_TICK;
    }
}
