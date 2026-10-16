/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client;

import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGridBuilder;
import com.nokhxyr.chiseledblocks.core.voxel.PixelRaycast;
import com.nokhxyr.chiseledblocks.core.voxel.Face;
import com.nokhxyr.chiseledblocks.edit.ChiselOperation;
import com.nokhxyr.chiseledblocks.edit.PlayerSymmetry;
import com.nokhxyr.chiseledblocks.item.ChiselItem;
import com.nokhxyr.chiseledblocks.network.AdminPayloads;
import com.nokhxyr.chiseledblocks.network.ChiselPayload;
import com.nokhxyr.chiseledblocks.network.CyclePayload;
import com.nokhxyr.chiseledblocks.network.PredictionTracker;
import com.nokhxyr.chiseledblocks.network.UndoPayload;
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
@EventBusSubscriber(modid = ChiseledBlocks.MOD_ID, value = Dist.CLIENT)
public final class ChiselInput {
    /** Ticks between two strokes while the attack key is held. */
    private static final int REPEAT_TICKS = 3;
    /** How far inside the hit face the targeted point is moved, in blocks. */
    private static final double INSET = 1.0 / 64.0;
    /** Longest view ray traced through a grid, in voxels (eight blocks). */
    private static final double REACH_IN_VOXELS = 8 * PixelGrid.SIZE;
    private static final Face[] FACES = Face.values();

    private static int cooldown;

    private ChiselInput() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (cooldown > 0) {
            cooldown--;
        }
        while (ChiseledBlocksKeys.MODE.consumeClick()) {
            PacketDistributor.sendToServer(new CyclePayload(false));
        }
        while (ChiseledBlocksKeys.SIZE.consumeClick()) {
            PacketDistributor.sendToServer(new CyclePayload(true));
        }
        while (ChiseledBlocksKeys.UNDO.consumeClick()) {
            PacketDistributor.sendToServer(new UndoPayload(false));
        }
        while (ChiseledBlocksKeys.REDO.consumeClick()) {
            PacketDistributor.sendToServer(new UndoPayload(true));
        }
        while (ChiseledBlocksKeys.ADMIN.consumeClick()) {
            PacketDistributor.sendToServer(new AdminPayloads.RequestOpen());
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && minecraft.player != null && ChiseledBlocksKeys.RADIAL.isDown()) {
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
        if (!(player.level().getBlockEntity(pos) instanceof ChiseledBlockEntity carved)) {
            return;
        }
        PixelGridBuilder builder = carved.getGrid().toBuilder();
        int removed = ChiselOperation.apply(ChiselItem.settings(tool), PlayerSymmetry.client(), voxel, face, builder::get, chisel::canCut,
                (index, material) -> builder.set(index, PixelGrid.AIR));
        if (removed > 0 && builder.changes() < PixelGrid.VOLUME) {
            PixelGrid predicted = builder.build();
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
        if (player.level().getBlockEntity(pos) instanceof ChiseledBlockEntity carved) {
            Vec3 eye = player.getEyePosition().subtract(pos.getX(), pos.getY(), pos.getZ()).scale(PixelGrid.SIZE);
            Vec3 view = player.getViewVector(1.0F);
            PixelRaycast.Hit voxelHit = PixelRaycast.cast(carved.getGrid(), eye.x, eye.y, eye.z, view.x, view.y, view.z,
                    REACH_IN_VOXELS, material -> material != PixelGrid.AIR);
            if (voxelHit != null) {
                return PixelGrid.index(voxelHit.x(), voxelHit.y(), voxelHit.z());
            }
        }
        Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        Vec3 local = hit.getLocation().subtract(normal.scale(INSET)).subtract(pos.getX(), pos.getY(), pos.getZ());
        int x = Mth.clamp(Mth.floor(local.x * PixelGrid.SIZE), 0, PixelGrid.SIZE - 1);
        int y = Mth.clamp(Mth.floor(local.y * PixelGrid.SIZE), 0, PixelGrid.SIZE - 1);
        int z = Mth.clamp(Mth.floor(local.z * PixelGrid.SIZE), 0, PixelGrid.SIZE - 1);
        return PixelGrid.index(x, y, z);
    }
}
