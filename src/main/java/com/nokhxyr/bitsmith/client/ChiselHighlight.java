/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.edit.ChiselShape;
import com.nokhxyr.bitsmith.core.voxel.Face;
import com.nokhxyr.bitsmith.item.ChiselItem;
import com.nokhxyr.bitsmith.item.ChiselSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/**
 * Replaces the block outline with the box the next chisel stroke will cover. Only the targeted
 * block is concerned, so the cost is a handful of lines per frame.
 */
@EventBusSubscriber(modid = Bitsmith.MOD_ID, value = Dist.CLIENT)
public final class ChiselHighlight {
    private static final Face[] FACES = Face.values();
    private static final double UNIT = 1.0 / 16.0;

    private ChiselHighlight() {
    }

    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof ChiselItem)) {
            return;
        }
        BlockHitResult hit = event.getTarget();
        BlockPos pos = hit.getBlockPos();
        Level level = player.level();
        if (!(level.getBlockEntity(pos) instanceof CarvedBlockEntity)
                && !Materials.isCarvable(level.getBlockState(pos), level, pos)) {
            return;
        }

        ChiselSettings settings = ChiselItem.settings(player.getMainHandItem());
        int voxel = ChiselInput.voxelAt(hit);
        int[] box = ChiselShape.bounds(settings.mode(), settings.size(),
                voxel & 15, voxel >> 8, (voxel >> 4) & 15, FACES[hit.getDirection().ordinal()]);

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        LevelRenderer.renderLineBox(pose, event.getMultiBufferSource().getBuffer(RenderType.lines()),
                box[0] * UNIT, box[1] * UNIT, box[2] * UNIT, box[3] * UNIT, box[4] * UNIT, box[5] * UNIT,
                0.0F, 0.0F, 0.0F, 0.6F);
        pose.popPose();
        event.setCanceled(true);
    }
}
