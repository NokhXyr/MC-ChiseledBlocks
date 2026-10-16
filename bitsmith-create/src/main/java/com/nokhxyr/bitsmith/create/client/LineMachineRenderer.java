/*
 * Bitsmith: Create
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.create.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nokhxyr.bitsmith.create.LineMachineBlockEntity;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the shaft through a bit line machine (when Flywheel does not) and its moving tool: the
 * slicer's blade spins with the shaft, the cutter's blade drops once per cut, the crusher's
 * hammer strikes three times per cut.
 */
public final class LineMachineRenderer extends KineticBlockEntityRenderer<LineMachineBlockEntity> {

    public LineMachineRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected BlockState getRenderedBlockState(LineMachineBlockEntity machine) {
        return shaft(getRotationAxisOf(machine));
    }

    @Override
    protected void renderSafe(LineMachineBlockEntity machine, float partialTicks, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay) {
        super.renderSafe(machine, partialTicks, pose, buffers, light, overlay);
        BlockState state = machine.getBlockState();
        Direction facing = state.getValue(HorizontalKineticBlock.HORIZONTAL_FACING);
        VertexConsumer consumer = buffers.getBuffer(RenderType.cutoutMipped());
        float progress = machine.progress(partialTicks);
        switch (machine.stage()) {
            case SLICER -> {
                // The spinning blade comes down through the gap, cuts, and goes back up.
                float drop = Mth.sin(progress * Mth.PI) * REACH;
                float angle = (AnimationTickHolder.getRenderTime(machine.getLevel()) * machine.getSpeed() * 3f / 10) % 360;
                facing(LinePartials.SLICER_BLADE, state, facing).translate(0, -drop, 0).center().rotateYDegrees(angle).uncenter()
                        .light(light).renderInto(pose, consumer);
                pole(LinePartials.SLICER_POLE, state, facing, 2.5f / 16, drop, light, pose, consumer);
            }
            case CUTTER -> {
                float drop = Mth.sin(progress * Mth.PI) * REACH;
                facing(LinePartials.CUTTER_HEAD, state, facing).translate(0, -drop, 0).light(light).renderInto(pose, consumer);
                pole(LinePartials.CUTTER_POLE, state, facing, POLE_TOP, drop, light, pose, consumer);
            }
            case CRUSHER -> {
                // Three strikes per cut.
                float drop = Math.abs(Mth.sin(progress * 3 * Mth.PI)) * REACH;
                facing(LinePartials.CRUSHER_HAMMER, state, facing).translate(0, -drop, 0).light(light).renderInto(pose, consumer);
                pole(LinePartials.CRUSHER_POLE, state, facing, POLE_TOP, drop, light, pose, consumer);
            }
        }
    }

    /** How far a tool goes down to reach the item: through the gap block onto the belt or depot. */
    private static final float REACH = 18.5f / 16;

    /** Top of the pole on the cutter's head and the crusher's hammer, at rest. */
    private static final float POLE_TOP = 13f / 16;

    /**
     * The extension above the tool's own pole: from its top, lowered by {@code drop}, up to where
     * that top rests, stretched to fit. It starts where the tool's pole ends, never over it.
     */
    private static void pole(PartialModel part, BlockState state, Direction facing, float restTop, float drop, int light,
            PoseStack pose, VertexConsumer consumer) {
        if (drop < 1 / 64f) {
            return;
        }
        facing(part, state, facing).translate(0, restTop - drop, 0).scale(1, drop, 1).light(light).renderInto(pose, consumer);
    }

    /** A part drawn for a machine facing north, turned to the machine's facing. */
    private static SuperByteBuffer facing(PartialModel part, BlockState state, Direction facing) {
        return CachedBuffers.partial(part, state).center().rotateYDegrees(-facing.toYRot()).uncenter();
    }
}
