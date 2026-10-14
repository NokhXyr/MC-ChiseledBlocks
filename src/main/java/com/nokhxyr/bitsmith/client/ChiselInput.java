/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.core.voxel.BitRaycast;
import com.nokhxyr.bitsmith.core.voxel.Face;
import com.nokhxyr.bitsmith.edit.ChiselOperation;
import com.nokhxyr.bitsmith.item.ChiselItem;
import com.nokhxyr.bitsmith.network.ChiselPayload;
import com.nokhxyr.bitsmith.network.CyclePayload;
import com.nokhxyr.bitsmith.network.PredictionTracker;
import com.nokhxyr.bitsmith.network.UndoPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Turns the attack key into chisel strokes while a chisel is held: one stroke per click, and a
 * steady rate while the key is held down. Strokes on an existing carved block are applied
 * locally at once (prediction) and confirmed or corrected by the server.
 */
@EventBusSubscriber(modid = Bitsmith.MOD_ID, value = Dist.CLIENT)
public final class ChiselInput {
    /** Ticks between two strokes while the attack key is held. */
    private static final int REPEAT_TICKS = 3;
    /** How far inside the hit face the targeted point is moved, in blocks. */
    private static final double INSET = 1.0 / 64.0;
    /** Longest view ray traced through a grid, in voxels (eight blocks). */
    private static final double REACH_IN_VOXELS = 8 * BitGrid.SIZE;
    private static final Face[] FACES = Face.values();

    private static int cooldown;

    private ChiselInput() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (cooldown > 0) {
            cooldown--;
        }
        while (BitsmithKeys.MODE.consumeClick()) {
            PacketDistributor.sendToServer(new CyclePayload(false));
        }
        while (BitsmithKeys.SIZE.consumeClick()) {
            PacketDistributor.sendToServer(new CyclePayload(true));
        }
        while (BitsmithKeys.UNDO.consumeClick()) {
            PacketDistributor.sendToServer(new UndoPayload(false));
        }
        while (BitsmithKeys.REDO.consumeClick()) {
            PacketDistributor.sendToServer(new UndoPayload(true));
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && minecraft.player != null && BitsmithKeys.RADIAL.isDown()) {
            RadialMenuScreen menu = RadialMenuScreen.forItem(minecraft.player.getMainHandItem());
            if (menu != null) {
                minecraft.setScreen(menu);
            }
        }
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof ChiselItem chisel)) {
            return;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        event.setCanceled(true);
        event.setSwingHand(false);
        if (cooldown > 0) {
            return;
        }
        cooldown = REPEAT_TICKS;

        int voxel = voxelAt(player, hit);
        Face face = FACES[hit.getDirection().ordinal()];
        predict(player, chisel, player.getMainHandItem(), hit.getBlockPos(), voxel, face);
        PacketDistributor.sendToServer(new ChiselPayload(hit.getBlockPos(), voxel, face));
        player.swing(InteractionHand.MAIN_HAND);
    }

    /** Applies the stroke to the local copy of an already carved block, exactly as the server will. */
    private static void predict(Player player, ChiselItem chisel, ItemStack tool, BlockPos pos, int voxel, Face face) {
        if (!(player.level().getBlockEntity(pos) instanceof CarvedBlockEntity carved)) {
            return;
        }
        BitGridBuilder builder = carved.getGrid().toBuilder();
        int removed = ChiselOperation.apply(ChiselItem.settings(tool), voxel, face, builder::get, chisel::canCut,
                (index, material) -> builder.set(index, BitGrid.AIR));
        if (removed > 0 && builder.changes() < BitGrid.VOLUME) {
            BitGrid predicted = builder.build();
            if (!predicted.isEmpty()) {
                PredictionTracker.record(pos, player.level().getGameTime());
                carved.setGrid(predicted);
            }
        }
    }

    /**
     * The voxel the player is looking at. In a carved block the view ray is traced through the
     * grid, so the answer stays right on the edges and corners of holes, where the hit point is
     * shared by a voxel that is gone and one that is not. Otherwise every voxel is solid and the
     * point just behind the hit face is enough.
     */
    static int voxelAt(Player player, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        if (player.level().getBlockEntity(pos) instanceof CarvedBlockEntity carved) {
            Vec3 eye = player.getEyePosition().subtract(pos.getX(), pos.getY(), pos.getZ()).scale(BitGrid.SIZE);
            Vec3 view = player.getViewVector(1.0F);
            BitRaycast.Hit voxelHit = BitRaycast.cast(carved.getGrid(), eye.x, eye.y, eye.z, view.x, view.y, view.z,
                    REACH_IN_VOXELS, material -> material != BitGrid.AIR);
            if (voxelHit != null) {
                return BitGrid.index(voxelHit.x(), voxelHit.y(), voxelHit.z());
            }
        }
        Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        Vec3 local = hit.getLocation().subtract(normal.scale(INSET)).subtract(pos.getX(), pos.getY(), pos.getZ());
        int x = Mth.clamp(Mth.floor(local.x * BitGrid.SIZE), 0, BitGrid.SIZE - 1);
        int y = Mth.clamp(Mth.floor(local.y * BitGrid.SIZE), 0, BitGrid.SIZE - 1);
        int z = Mth.clamp(Mth.floor(local.z * BitGrid.SIZE), 0, BitGrid.SIZE - 1);
        return BitGrid.index(x, y, z);
    }
}
