/*
 * Bitsmith: Create
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.create;

import com.nokhxyr.bitsmith.item.BitItem;
import com.nokhxyr.bitsmith.registry.CarvedItems;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Mechanical Bit Mill: cuts whole blocks into 4096 bits each, as fast as its shaft turns, and
 * does nothing while it stands still. Blocks go in through slot 0 and bits come out of slot 1 of
 * a standard item handler, so Create's chutes, funnels and belts, hoppers and storage buses all
 * work with it.
 */
public final class MechanicalBitMillBlockEntity extends KineticBlockEntity {
    public static final int BITS_PER_BLOCK = 4096;
    /** Bits kept ready: two blocks, so the output never starves between two cuts. */
    public static final int CAPACITY = 2 * BITS_PER_BLOCK;
    /** Work to cut one block; each tick adds the speed in rpm divided by 16 (64 ticks at 16 rpm). */
    public static final float WORK_PER_BLOCK = 64;
    private static final int STACK = 64;

    private ItemStack input = ItemStack.EMPTY;
    private BlockState material = Blocks.AIR.defaultBlockState();
    private int bits;
    private float progress;
    private final Handler handler = new Handler();

    public MechanicalBitMillBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public IItemHandler handler() {
        return handler;
    }

    public MillContents contents() {
        return new MillContents(input.copy(), material, bits);
    }

    /** The material a stack would be cut into, or null when it cannot be cut. */
    public static @Nullable BlockState materialOf(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            return CarvedItems.isCarvable(block) ? block.defaultBlockState() : null;
        }
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide || input.isEmpty()) {
            return;
        }
        float speed = Math.abs(getSpeed());
        BlockState next = materialOf(input);
        boolean room = bits == 0 || (next == material && bits + BITS_PER_BLOCK <= CAPACITY);
        if (speed == 0 || isOverStressed() || next == null || !room) {
            return;
        }
        progress += speed / 16f;
        if (progress < WORK_PER_BLOCK) {
            return;
        }
        progress = 0;
        input.shrink(1);
        material = next;
        bits += BITS_PER_BLOCK;
        changed();
        if (level.getRandom().nextInt(3) == 0) {
            level.playSound(null, worldPosition, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.4F, 1.2F);
        }
    }

    private void changed() {
        if (bits == 0) {
            material = Blocks.AIR.defaultBlockState();
        }
        setChanged();
        if (level != null) {
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    /** Redstone for comparators: how full the bit store is. */
    public int signal() {
        return bits == 0 ? 0 : 1 + bits * 14 / CAPACITY;
    }

    public ItemStack insert(ItemStack stack) {
        return handler.insertItem(0, stack, false);
    }

    public ItemStack takeBits(int max) {
        return handler.extractItem(1, max, false);
    }

    public ItemStack takeInput() {
        ItemStack taken = input;
        input = ItemStack.EMPTY;
        progress = 0;
        changed();
        return taken;
    }

    public int bits() {
        return bits;
    }

    public BlockState material() {
        return material;
    }

    public ItemStack input() {
        return input;
    }

    // Saving, and the contents kept on the item

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        MillContents.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), contents())
                .result().ifPresent(encoded -> tag.put("contents", encoded));
        tag.putFloat("progress", progress);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("contents")) {
            MillContents.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("contents"))
                    .result().ifPresent(this::set);
        }
        progress = tag.getFloat("progress");
    }

    private void set(MillContents contents) {
        input = contents.input().copy();
        bits = Math.max(0, contents.bits());
        material = bits == 0 ? Blocks.AIR.defaultBlockState() : contents.material();
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput componentInput) {
        super.applyImplicitComponents(componentInput);
        MillContents contents = componentInput.get(BitsmithCreate.MILL_CONTENTS.get());
        if (contents != null) {
            set(contents);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        MillContents contents = contents();
        if (!contents.isEmpty()) {
            components.set(BitsmithCreate.MILL_CONTENTS.get(), contents);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("contents");
    }

    /** Slot 0: blocks in. Slot 1: bits out, a stack at a time. */
    private final class Handler implements IItemHandler {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot == 0) {
                return input;
            }
            return slot == 1 && bits > 0 ? BitItem.of(material, Math.min(STACK, bits)) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || materialOf(stack) == null) {
                return stack;
            }
            if (!input.isEmpty() && !ItemStack.isSameItemSameComponents(input, stack)) {
                return stack;
            }
            int moved = Math.min(Math.min(stack.getMaxStackSize(), STACK) - input.getCount(), stack.getCount());
            if (moved <= 0) {
                return stack;
            }
            if (!simulate) {
                if (input.isEmpty()) {
                    input = stack.copyWithCount(moved);
                } else {
                    input.grow(moved);
                }
                changed();
            }
            return stack.copyWithCount(stack.getCount() - moved);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 1 || bits == 0 || amount <= 0) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(Math.min(amount, STACK), bits);
            ItemStack result = BitItem.of(material, taken);
            if (!simulate) {
                bits -= taken;
                changed();
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return STACK;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && materialOf(stack) != null;
        }
    }
}
