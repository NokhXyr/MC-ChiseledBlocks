/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.machine;

import com.nokhxyr.bitsmith.item.BitItem;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import com.nokhxyr.bitsmith.registry.CarvedItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Cuts whole blocks into bits: 4096 bits of the block's material for every block. Blocks go in
 * through slot 0, bits come out of slot 1, through the standard item handler, so hoppers,
 * Create's funnels, chutes and belts, and the import and export buses of storage mods can feed
 * and empty it without knowing anything about Bitsmith.
 */
public final class BitMillBlockEntity extends BlockEntity {
    public static final int BITS_PER_BLOCK = 4096;
    /** Bits kept ready: two blocks, so the output never starves between two cuts. */
    public static final int CAPACITY = 2 * BITS_PER_BLOCK;
    /** Ticks to cut one block. */
    public static final int CUT_TICKS = 10;
    private static final int STACK = 64;

    private ItemStack input = ItemStack.EMPTY;
    private BlockState material = Blocks.AIR.defaultBlockState();
    private int bits;
    private int progress;
    private final Handler handler = new Handler();

    public BitMillBlockEntity(BlockPos pos, BlockState state) {
        super(BitsmithRegistries.BIT_MILL_ENTITY.get(), pos, state);
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

    public static void serverTick(Level level, BlockPos pos, BlockState state, BitMillBlockEntity mill) {
        if (mill.input.isEmpty()) {
            mill.progress = 0;
            return;
        }
        BlockState next = materialOf(mill.input);
        boolean room = mill.bits == 0 || (next == mill.material && mill.bits + BITS_PER_BLOCK <= CAPACITY);
        if (next == null || !room) {
            return;
        }
        if (++mill.progress < CUT_TICKS) {
            return;
        }
        mill.progress = 0;
        mill.input.shrink(1);
        mill.material = next;
        mill.bits += BITS_PER_BLOCK;
        mill.changed();
        if (level instanceof ServerLevel server && server.getRandom().nextInt(3) == 0) {
            server.playSound(null, pos, SoundEvents.UI_STONECUTTER_TAKE_RESULT, SoundSource.BLOCKS, 0.4F, 1.2F);
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

    /** Puts what a player holds into the mill; returns what is left in hand. */
    public ItemStack insert(ItemStack stack) {
        return handler.insertItem(0, stack, false);
    }

    /** Takes up to a stack of bits out. */
    public ItemStack takeBits(int max) {
        return handler.extractItem(1, max, false);
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

    /** Takes the waiting blocks back out. */
    public ItemStack takeInput() {
        ItemStack taken = input;
        input = ItemStack.EMPTY;
        progress = 0;
        changed();
        return taken;
    }

    // Saving, and the contents kept on the item

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        MillContents.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), contents())
                .result().ifPresent(encoded -> tag.put("contents", encoded));
        tag.putInt("progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("contents")) {
            MillContents.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("contents"))
                    .result().ifPresent(this::set);
        }
        progress = tag.getInt("progress");
    }

    private void set(MillContents contents) {
        input = contents.input().copy();
        bits = Math.max(0, contents.bits());
        material = bits == 0 ? Blocks.AIR.defaultBlockState() : contents.material();
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput componentInput) {
        MillContents contents = componentInput.get(BitsmithComponents.MILL_CONTENTS.get());
        if (contents != null) {
            set(contents);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        MillContents contents = contents();
        if (!contents.isEmpty()) {
            components.set(BitsmithComponents.MILL_CONTENTS.get(), contents);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
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
            int room = Math.min(stack.getMaxStackSize(), STACK) - input.getCount();
            int moved = Math.min(room, stack.getCount());
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
