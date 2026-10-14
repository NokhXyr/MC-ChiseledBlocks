/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.item.TapeMeasureItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The tape measure, entirely on the client: right click sets the first bit, right click again
 * the second; the measured box stays drawn and its size shows above the hotbar while the tape is
 * held. Sneak and right click in the air clears it.
 */
@EventBusSubscriber(modid = Bitsmith.MOD_ID, value = Dist.CLIENT)
public final class TapeMeasureClient {
    private static final double UNIT = 1.0 / BitGrid.SIZE;

    /** A bit in world bit coordinates (block coordinate times 16 plus the voxel). */
    public record BitPoint(int x, int y, int z) {
    }

    private static @Nullable BitPoint first;
    private static @Nullable BitPoint second;

    private TapeMeasureClient() {
    }

    public static @Nullable BitPoint first() {
        return first;
    }

    public static @Nullable BitPoint second() {
        return second;
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!event.isUseItem() || player == null || !(player.getMainHandItem().getItem() instanceof TapeMeasureItem)) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(true);
        if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            int voxel = ChiselInput.voxelAt(player, hit);
            BitPoint point = new BitPoint(
                    hit.getBlockPos().getX() * BitGrid.SIZE + (voxel & 15),
                    hit.getBlockPos().getY() * BitGrid.SIZE + (voxel >> 8),
                    hit.getBlockPos().getZ() * BitGrid.SIZE + ((voxel >> 4) & 15));
            if (first == null || second != null) {
                first = point;
                second = null;
                player.displayClientMessage(Component.translatable("bitsmith.tape.first"), true);
            } else {
                second = point;
                player.displayClientMessage(describe(), true);
            }
        } else if (player.isShiftKeyDown()) {
            first = null;
            second = null;
            player.displayClientMessage(Component.translatable("bitsmith.tape.cleared"), true);
        }
    }

    /** Size, distance and volume of the current measurement. */
    public static Component describe() {
        if (first == null || second == null) {
            return Component.translatable("bitsmith.tape.first");
        }
        int dx = Math.abs(second.x() - first.x()) + 1;
        int dy = Math.abs(second.y() - first.y()) + 1;
        int dz = Math.abs(second.z() - first.z()) + 1;
        double distance = Math.sqrt(sq(second.x() - first.x()) + sq(second.y() - first.y()) + sq(second.z() - first.z()));
        return Component.translatable("bitsmith.tape.result", dx, dy, dz,
                String.format(Locale.ROOT, "%.1f", distance),
                String.format(Locale.ROOT, "%.2f", distance / BitGrid.SIZE),
                (long) dx * dy * dz);
    }

    private static double sq(int value) {
        return (double) value * value;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Player player = Minecraft.getInstance().player;
        if (player != null && second != null && player.tickCount % 10 == 0
                && player.getItemInHand(InteractionHand.MAIN_HAND).getItem() instanceof TapeMeasureItem) {
            player.displayClientMessage(describe(), true);
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || first == null) {
            return;
        }
        BitPoint a = first;
        BitPoint b = second != null ? second : first;
        Vec3 camera = event.getCamera().getPosition();
        // The camera rotation is already applied by the level renderer at this stage: only the
        // camera position has to be taken off.
        PoseStack pose = event.getPoseStack() != null ? event.getPoseStack() : new PoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        LevelRenderer.renderLineBox(pose, lines,
                Math.min(a.x(), b.x()) * UNIT, Math.min(a.y(), b.y()) * UNIT, Math.min(a.z(), b.z()) * UNIT,
                (Math.max(a.x(), b.x()) + 1) * UNIT, (Math.max(a.y(), b.y()) + 1) * UNIT, (Math.max(a.z(), b.z()) + 1) * UNIT,
                1.0F, 0.85F, 0.1F, 1.0F);
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
