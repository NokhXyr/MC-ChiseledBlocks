/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.edit.ChiselShape;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BoxMerger;
import com.nokhxyr.bitsmith.core.voxel.Face;
import com.nokhxyr.bitsmith.edit.PatternLogic;
import com.nokhxyr.bitsmith.item.ChiselItem;
import com.nokhxyr.bitsmith.item.ChiselSettings;
import com.nokhxyr.bitsmith.item.PatternItem;
import com.nokhxyr.bitsmith.item.PatternMode;
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

import java.util.List;

/**
 * Replaces the block outline with what the held tool will do: the box the next chisel stroke
 * covers, or the shape a pattern will print or carve and where. Only the targeted
 * block is concerned, so the cost is a handful of lines per frame.
 */
@EventBusSubscriber(modid = Bitsmith.MOD_ID, value = Dist.CLIENT)
public final class ChiselHighlight {
    private static final Face[] FACES = Face.values();
    private static final double UNIT = 1.0 / 16.0;

    private ChiselHighlight() {
    }

    /** Boxes of the last pattern drawn; patterns are interned, so identity is enough. */
    private static BitGrid lastPattern;
    private static List<BoxMerger.Box> lastBoxes = List.of();
    /** Beyond this many boxes the preview shows the block outline only. */
    private static final int MAX_PREVIEW_BOXES = 256;

    @SubscribeEvent
    public static void onPatternHighlight(RenderHighlightEvent.Block event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof PatternItem)) {
            return;
        }
        BitGrid pattern = PatternItem.grid(player.getMainHandItem());
        if (pattern == null || pattern.isEmpty()) {
            return;
        }
        PatternMode mode = PatternItem.mode(player.getMainHandItem());
        BlockHitResult hit = event.getTarget();
        BlockPos pos = PatternLogic.target(hit.getBlockPos(), hit.getDirection(), mode, player.isShiftKeyDown());
        if (pattern != lastPattern) {
            lastPattern = pattern;
            lastBoxes = BoxMerger.merge(pattern, material -> true);
        }

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        float red = mode == PatternMode.NEGATIVE ? 0.9F : 0.1F;
        float green = mode == PatternMode.NEGATIVE ? 0.2F : 0.7F;
        if (lastBoxes.size() <= MAX_PREVIEW_BOXES) {
            for (BoxMerger.Box box : lastBoxes) {
                LevelRenderer.renderLineBox(pose, lines,
                        box.minX() * UNIT, box.minY() * UNIT, box.minZ() * UNIT,
                        box.maxX() * UNIT, box.maxY() * UNIT, box.maxZ() * UNIT, red, green, 0.2F, 0.8F);
            }
        } else {
            LevelRenderer.renderLineBox(pose, lines, 0, 0, 0, 1, 1, 1, red, green, 0.2F, 0.8F);
        }
        pose.popPose();
        event.setCanceled(true);
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
