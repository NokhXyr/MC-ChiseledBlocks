/*
 * Bitsmith: Create
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.create;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.BeltProcessingBehaviour.ProcessingResult;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.createmod.catnip.levelWrappers.SchematicLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A machine of the bit line. It works on what passes under it on a Create belt or sits on a depot,
 * like Create's press: the item is held, the machine runs as fast as its shaft turns, then the
 * item is replaced by its 16 pieces, which move on to the next machine. Stopped, it lets items
 * pass untouched.
 */
public final class LineMachineBlockEntity extends KineticBlockEntity {
    /** Work for one cut; each tick adds the speed in rpm divided by 16 (2 seconds at 16 rpm). */
    public static final float WORK = 32;

    private float progress;
    private boolean running;

    public LineMachineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public LineStage stage() {
        return getBlockState().getBlock() instanceof LineMachineBlock machine ? machine.stage() : LineStage.SLICER;
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(new BeltProcessingBehaviour(this)
                .whenItemEnters(this::onItemEnters)
                .whileItemHeld(this::whileItemHeld));
    }

    /** Whether the machine is cutting, and how far (0 to 1), for the renderer. */
    public boolean running() {
        return running;
    }

    public float progress(float partialTicks) {
        if (!running) {
            return 0;
        }
        return Math.min(1, (progress + partialTicks * Math.abs(getSpeed()) / 16f) / WORK);
    }

    /** A schematic level: Ponder's scenes, where the machines cut like in a world but stay silent. */
    private boolean inSchematic() {
        return level instanceof SchematicLevel;
    }

    private ProcessingResult onItemEnters(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler) {
        if (stage().materialOf(transported.stack) == null || getSpeed() == 0) {
            return ProcessingResult.PASS;
        }
        start();
        return ProcessingResult.HOLD;
    }

    private ProcessingResult whileItemHeld(TransportedItemStack transported, TransportedItemStackHandlerBehaviour handler) {
        BlockState material = stage().materialOf(transported.stack);
        if (material == null) {
            return ProcessingResult.PASS;
        }
        if (getSpeed() == 0 || isOverStressed()) {
            return ProcessingResult.HOLD;
        }
        if (!running) {
            start();
        }
        if (progress < WORK) {
            return ProcessingResult.HOLD;
        }
        handler.handleProcessingOnItem(transported, TransportedResult.convertTo(piecesOf(transported, material)));
        running = false;
        progress = 0;
        if (level != null && !level.isClientSide) {
            sendData();
        }
        if (level != null && !inSchematic()) {
            level.playSound(null, worldPosition, stage() == LineStage.CRUSHER ? SoundEvents.ANVIL_LAND : SoundEvents.UI_STONECUTTER_TAKE_RESULT,
                    SoundSource.BLOCKS, stage() == LineStage.CRUSHER ? 0.15F : 0.5F, 1.4F);
        }
        return ProcessingResult.PASS;
    }

    /** The pieces an item is cut into, each going on from where the item was. */
    private List<TransportedItemStack> piecesOf(TransportedItemStack transported, BlockState material) {
        List<TransportedItemStack> outputs = new ArrayList<>();
        for (ItemStack piece : stage().outputs(material, transported.stack.getCount())) {
            TransportedItemStack output = transported.copy();
            output.stack = piece;
            outputs.add(output);
        }
        return outputs;
    }

    /** Ponder: whether this machine's input lies under it, at the centre of a belt or a depot. */
    private boolean inputBelow() {
        TransportedItemStackHandlerBehaviour handler = BlockEntityBehaviour.get(level, worldPosition.below(2), TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) {
            return false;
        }
        boolean[] found = {false};
        handler.handleCenteredProcessingOnAllItems(.25f, transported -> {
            found[0] |= stage().materialOf(transported.stack) != null;
            return TransportedResult.doNothing();
        });
        return found[0];
    }

    /**
     * Ponder: a belt in a scene tells the machine when an item arrives but never asks again while
     * it is held, and a depot does not tell it at all, so the machine runs the cut itself on what
     * lies under it, and lets it go.
     */
    private void finishCutInSchematic() {
        running = false;
        progress = 0;
        TransportedItemStackHandlerBehaviour handler = BlockEntityBehaviour.get(level, worldPosition.below(2), TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) {
            return;
        }
        handler.handleCenteredProcessingOnAllItems(.75f, transported -> {
            BlockState material = stage().materialOf(transported.stack);
            if (material == null) {
                return TransportedResult.doNothing();
            }
            List<TransportedItemStack> pieces = piecesOf(transported, material);
            pieces.forEach(piece -> piece.locked = false);
            return TransportedResult.convertTo(pieces);
        });
    }

    private void start() {
        running = true;
        progress = 0;
        if (level != null && !level.isClientSide) {
            sendData();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (running) {
            progress += Math.abs(getSpeed()) / 16f;
            if (progress >= WORK && inSchematic()) {
                finishCutInSchematic();
            }
        } else if (inSchematic() && getSpeed() != 0 && inputBelow()) {
            start();
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("running", running);
        tag.putFloat("progress", progress);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        running = tag.getBoolean("running");
        progress = tag.getFloat("progress");
    }
}
