/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.edit;

import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Places one bit against the clicked face: in the same carved block when the face is inside
 * it, otherwise in the neighbouring block, which becomes a carved block if it was empty.
 */
public final class BitPlacement {
    private static final double INSET = 1.0 / 64.0;

    private BitPlacement() {
    }

    public static InteractionResult place(UseOnContext context, BlockState material, Runnable consume) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!Materials.isValidMaterial(material)) {
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            // The server decides; the client only swings the arm.
            return InteractionResult.SUCCESS;
        }

        Direction face = context.getClickedFace();
        Vec3 target = context.getClickLocation().add(Vec3.atLowerCornerOf(face.getNormal()).scale(INSET));
        BlockPos pos = BlockPos.containing(target);
        if (!level.isInWorldBounds(pos)
                || (player != null && (!player.mayBuild() || !level.mayInteract(player, pos)))) {
            return InteractionResult.FAIL;
        }

        int x = Mth.clamp(Mth.floor((target.x - pos.getX()) * BitGrid.SIZE), 0, BitGrid.SIZE - 1);
        int y = Mth.clamp(Mth.floor((target.y - pos.getY()) * BitGrid.SIZE), 0, BitGrid.SIZE - 1);
        int z = Mth.clamp(Mth.floor((target.z - pos.getZ()) * BitGrid.SIZE), 0, BitGrid.SIZE - 1);
        int voxel = BitGrid.index(x, y, z);

        double unit = 1.0 / BitGrid.SIZE;
        AABB bitBox = new AABB(pos.getX() + x * unit, pos.getY() + y * unit, pos.getZ() + z * unit,
                pos.getX() + (x + 1) * unit, pos.getY() + (y + 1) * unit, pos.getZ() + (z + 1) * unit);
        if (!level.getEntities((Entity) null, bitBox, entity -> entity.blocksBuilding).isEmpty()) {
            return InteractionResult.FAIL;
        }

        int id = Materials.id(material);
        BlockState state = level.getBlockState(pos);
        ServerLevel serverLevel = (ServerLevel) level;
        BlockSnapshot before = BlockSnapshot.current(serverLevel, pos);
        if (level.getBlockEntity(pos) instanceof CarvedBlockEntity carved) {
            if (carved.peek(voxel) != BitGrid.AIR) {
                return InteractionResult.FAIL;
            }
            carved.edit(builder -> builder.set(voxel, id));
        } else if (state.canBeReplaced() && state.getFluidState().isEmpty()) {
            BitGrid grid = new BitGridBuilder().set(voxel, id).build();
            level.setBlock(pos, BitsmithRegistries.CARVED_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof CarvedBlockEntity carved) {
                carved.setGrid(grid);
            }
        } else {
            return InteractionResult.FAIL;
        }

        boolean survival = player == null || !player.getAbilities().instabuild;
        if (survival) {
            consume.run();
        }
        if (player instanceof ServerPlayer serverPlayer) {
            Int2IntOpenHashMap spent = new Int2IntOpenHashMap();
            if (survival) {
                spent.put(id, -1);
            }
            EditHistory.record(serverPlayer, pos, before, BlockSnapshot.current(serverLevel, pos), spent);
        }
        SoundType sound = material.getSoundType();
        level.playSound(null, pos, sound.getPlaceSound(), SoundSource.BLOCKS, 0.4F, sound.getPitch() * 1.4F);
        return InteractionResult.CONSUME;
    }
}
