/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.edit;

import com.nokhxyr.chiseledblocks.block.Materials;
import com.nokhxyr.chiseledblocks.core.edit.Symmetry;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGridBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.IntConsumer;

/**
 * Places a bit against the clicked face, plus its mirror images when the player uses a mirror:
 * in the same carved block when the face is inside it, otherwise in the neighbouring block, which
 * becomes a carved block if it was empty.
 */
public final class PixelPlacement {
    private static final double INSET = 1.0 / 64.0;
    private static final double UNIT = 1.0 / PixelGrid.SIZE;

    private PixelPlacement() {
    }

    /**
     * @param available bits the source holds (ignored in creative)
     * @param consume   removes that many bits from the source
     */
    public static InteractionResult place(UseOnContext context, BlockState material, int available, IntConsumer consume) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!Materials.isValidMaterial(material)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            // The server decides; the client only swings the arm.
            return InteractionResult.SUCCESS;
        }
        ServerLevel serverLevel = (ServerLevel) level;

        Direction face = context.getClickedFace();
        Vec3 target = context.getClickLocation().add(Vec3.atLowerCornerOf(face.getNormal()).scale(INSET));
        BlockPos pos = BlockPos.containing(target);
        if (!level.isInWorldBounds(pos)
                || (player != null && (!player.mayBuild() || !level.mayInteract(player, pos)))
                || (player instanceof ServerPlayer serverPlayer && !EditPermission.mayAdd(serverPlayer, pos))) {
            return InteractionResult.FAIL;
        }

        int x = Mth.clamp(Mth.floor((target.x - pos.getX()) * PixelGrid.SIZE), 0, PixelGrid.SIZE - 1);
        int y = Mth.clamp(Mth.floor((target.y - pos.getY()) * PixelGrid.SIZE), 0, PixelGrid.SIZE - 1);
        int z = Mth.clamp(Mth.floor((target.z - pos.getZ()) * PixelGrid.SIZE), 0, PixelGrid.SIZE - 1);
        int voxel = PixelGrid.index(x, y, z);

        PixelGrid base = baseAt(serverLevel, pos);
        if (base == null) {
            return InteractionResult.FAIL;
        }
        boolean survival = player == null || !player.getAbilities().instabuild;
        int budget = survival ? available : Integer.MAX_VALUE;
        Symmetry symmetry = player == null ? Symmetry.NONE : PlayerSymmetry.of(player);

        // The clicked voxel first, then its mirror images, as long as there are bits to place.
        IntArrayList placed = new IntArrayList();
        symmetry.forEachImage(voxel, image -> {
            if (placed.size() < budget && base.get(image) == PixelGrid.AIR && !blockedByEntity(serverLevel, pos, image)) {
                placed.add(image);
            }
        });
        if (placed.isEmpty() || !placed.contains(voxel)) {
            return InteractionResult.FAIL;
        }

        int id = Materials.id(material);
        PixelGridBuilder result = base.toBuilder();
        placed.forEach(index -> result.set(index, id));
        BlockSnapshot before = BlockSnapshot.current(serverLevel, pos);
        BlockSnapshot after = BlockSnapshot.of(result.build());
        after.apply(serverLevel, pos);

        if (survival) {
            consume.accept(placed.size());
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Int2IntOpenHashMap spent = new Int2IntOpenHashMap();
            if (survival) {
                spent.put(id, -placed.size());
            }
            EditHistory.record(serverPlayer, pos, before, after, spent);
        }
        SoundType sound = material.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, 0.4F, sound.getPitch() * 1.4F);
        return InteractionResult.CONSUME;
    }

    /** The grid bits can be added to at {@code pos}: a carving, or an empty grid for a replaceable block. */
    static PixelGrid baseAt(ServerLevel level, BlockPos pos) {
        BlockSnapshot current = BlockSnapshot.current(level, pos);
        if (current.grid() != null) {
            return current.grid();
        }
        BlockState state = current.plain();
        return state.canBeReplaced() && state.getFluidState().isEmpty() ? PixelGrid.filled(PixelGrid.AIR) : null;
    }

    static boolean blockedByEntity(ServerLevel level, BlockPos pos, int voxel) {
        int x = voxel & 15;
        int z = (voxel >> 4) & 15;
        int y = voxel >> 8;
        AABB box = new AABB(pos.getX() + x * UNIT, pos.getY() + y * UNIT, pos.getZ() + z * UNIT,
                pos.getX() + (x + 1) * UNIT, pos.getY() + (y + 1) * UNIT, pos.getZ() + (z + 1) * UNIT);
        return !level.getEntities((Entity) null, box, entity -> entity.blocksBuilding).isEmpty();
    }
}
