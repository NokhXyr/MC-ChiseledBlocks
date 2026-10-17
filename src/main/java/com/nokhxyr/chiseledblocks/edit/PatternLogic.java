/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.edit;

import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import com.nokhxyr.chiseledblocks.block.Materials;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGridBuilder;
import com.nokhxyr.chiseledblocks.core.voxel.BoxMerger;
import com.nokhxyr.chiseledblocks.item.PatternMode;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksRegistries;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * Server side use of a pattern: printing it (positive) or carving its shape (negative). Every
 * change goes through {@link BlockSnapshot} and {@link EditHistory}, so it can be undone, and
 * in survival bits are taken or given exactly like chiseling and placing.
 */
public final class PatternLogic {
    private static final double UNIT = 1.0 / PixelGrid.SIZE;

    private PatternLogic() {
    }

    /** Where a pattern used through this click acts. Positive prints next to the face unless sneaking. */
    public static BlockPos target(BlockPos clicked, Direction face, PatternMode mode, boolean sneaking) {
        return mode == PatternMode.POSITIVE && !sneaking ? clicked.relative(face) : clicked;
    }

    public static InteractionResult use(UseOnContext context, PixelGrid pattern, PatternMode mode) {
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.FAIL;
        }
        BlockPos pos = target(context.getClickedPos(), context.getClickedFace(), mode, player.isShiftKeyDown());
        if (!level.isInWorldBounds(pos) || !player.mayBuild() || !level.mayInteract(player, pos)
                || !(mode == PatternMode.POSITIVE ? EditPermission.mayAdd(player, pos) : EditPermission.mayRemove(player, pos))) {
            return InteractionResult.FAIL;
        }
        boolean done = mode == PatternMode.POSITIVE ? print(player, level, pos, pattern) : carve(player, level, pos, pattern);
        return done ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }

    /** The grid at {@code pos} a pattern can work on, or null when the block cannot hold bits. */
    private static @Nullable PixelGrid baseAt(ServerLevel level, BlockPos pos, boolean allowEmpty) {
        BlockState state = level.getBlockState(pos);
        if (level.getBlockEntity(pos) instanceof ChiseledBlockEntity carved) {
            return carved.effectiveGrid();
        }
        if (allowEmpty && state.canBeReplaced() && state.getFluidState().isEmpty()) {
            return PixelGrid.filled(PixelGrid.AIR);
        }
        if (Materials.isCarvable(state, level, pos)) {
            return PixelGrid.filled(Materials.id(state));
        }
        return null;
    }

    static boolean print(ServerPlayer player, ServerLevel level, BlockPos pos, PixelGrid pattern) {
        PixelGrid base = baseAt(level, pos, true);
        if (base == null) {
            player.displayClientMessage(Component.translatable("chiseledblocks.pattern.cannot_print"), true);
            return false;
        }

        PixelGridBuilder result = base.toBuilder();
        PixelGridBuilder added = new PixelGridBuilder();
        Int2IntOpenHashMap needed = new Int2IntOpenHashMap();
        for (int i = 0; i < PixelGrid.VOLUME; i++) {
            int material = pattern.get(i);
            if (material != PixelGrid.AIR && result.get(i) == PixelGrid.AIR && Materials.isValidMaterial(Materials.state(material))) {
                result.set(i, material);
                added.set(i, material);
                needed.addTo(material, 1);
            }
        }
        if (needed.isEmpty()) {
            player.displayClientMessage(Component.translatable("chiseledblocks.pattern.nothing_to_print"), true);
            return false;
        }
        if (blockedByEntity(level, pos, added.build())) {
            return false;
        }

        boolean survival = !player.getAbilities().instabuild;
        if (survival) {
            MutableComponent missing = missingBits(player, needed);
            if (missing != null) {
                player.displayClientMessage(Component.translatable("chiseledblocks.pattern.missing_pixels", missing), true);
                return false;
            }
            for (Int2IntMap.Entry entry : needed.int2IntEntrySet()) {
                PixelInventory.take(player, Materials.state(entry.getIntKey()), entry.getIntValue());
            }
        }

        BlockSnapshot before = BlockSnapshot.current(level, pos);
        BlockSnapshot after = BlockSnapshot.of(result.build());
        after.apply(level, pos);
        Int2IntOpenHashMap gained = new Int2IntOpenHashMap();
        if (survival) {
            needed.int2IntEntrySet().forEach(e -> gained.put(e.getIntKey(), -e.getIntValue()));
        }
        EditHistory.record(player, pos, before, after, gained);

        SoundType sound = Materials.state(needed.keySet().iterator().nextInt()).getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, 0.8F, sound.getPitch());
        return true;
    }

    static boolean carve(ServerPlayer player, ServerLevel level, BlockPos pos, PixelGrid pattern) {
        PixelGrid base = baseAt(level, pos, false);
        if (base == null) {
            return false;
        }
        PixelGridBuilder result = base.toBuilder();
        Int2IntOpenHashMap removed = new Int2IntOpenHashMap();
        for (int i = 0; i < PixelGrid.VOLUME; i++) {
            int material = result.get(i);
            if (pattern.get(i) != PixelGrid.AIR && material != PixelGrid.AIR
                    && ChiseledBlocksRegistries.IRON_CHISEL.get().canCut(material)) {
                result.set(i, PixelGrid.AIR);
                removed.addTo(material, 1);
            }
        }
        if (removed.isEmpty()) {
            return false;
        }

        BlockSnapshot before = BlockSnapshot.current(level, pos);
        BlockSnapshot after = BlockSnapshot.of(result.build());
        after.apply(level, pos);
        boolean survival = !player.getAbilities().instabuild;
        if (survival) {
            PixelInventory.give(player, removed);
        }
        EditHistory.record(player, pos, before, after, survival ? removed : new Int2IntOpenHashMap());

        SoundType sound = Materials.state(removed.keySet().iterator().nextInt()).getSoundType();
        level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, 0.8F, sound.getPitch());
        return true;
    }

    /** Whether an entity stands where the printed bits would go. */
    private static boolean blockedByEntity(ServerLevel level, BlockPos pos, PixelGrid added) {
        for (BoxMerger.Box box : BoxMerger.merge(added, material -> true)) {
            AABB world = new AABB(
                    pos.getX() + box.minX() * UNIT, pos.getY() + box.minY() * UNIT, pos.getZ() + box.minZ() * UNIT,
                    pos.getX() + box.maxX() * UNIT, pos.getY() + box.maxY() * UNIT, pos.getZ() + box.maxZ() * UNIT);
            if (!level.getEntities((Entity) null, world, entity -> entity.blocksBuilding).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** A readable list of the bits the player lacks, or null when they have everything. */
    private static @Nullable MutableComponent missingBits(ServerPlayer player, Int2IntMap needed) {
        MutableComponent list = null;
        for (Int2IntMap.Entry entry : needed.int2IntEntrySet()) {
            BlockState material = Materials.state(entry.getIntKey());
            int lacking = entry.getIntValue() - PixelInventory.count(player, material);
            if (lacking > 0) {
                Component part = Component.translatable("chiseledblocks.pixels.amount", lacking, material.getBlock().getName());
                list = list == null ? part.copy() : list.append(", ").append(part);
            }
        }
        return list;
    }
}
