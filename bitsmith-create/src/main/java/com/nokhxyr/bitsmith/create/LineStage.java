/*
 * Bitsmith: Create
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.create;

import com.nokhxyr.bitsmith.item.BitItem;
import com.nokhxyr.bitsmith.registry.CarvedItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The three machines of the bit line. Each cuts every item it holds into 16 of the next piece:
 * a block into 16 slices, a slice into 16 rods, a rod into 16 bits, so 4096 bits per block.
 */
public enum LineStage {
    SLICER("bit_slicer"),
    CUTTER("bit_cutter"),
    CRUSHER("bit_crusher");

    /** Pieces made from one item. */
    public static final int SPLIT = 16;
    private static final int STACK = 64;

    private final String id;

    LineStage(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** The material of an item this machine can cut, or null. */
    public @Nullable BlockState materialOf(ItemStack stack) {
        return switch (this) {
            case SLICER -> {
                if (stack.getItem() instanceof BlockItem blockItem) {
                    Block block = blockItem.getBlock();
                    yield CarvedItems.isCarvable(block) ? block.defaultBlockState() : null;
                }
                yield null;
            }
            case CUTTER -> stack.is(BitsmithCreate.SLICE.get()) ? pieceMaterial(stack) : null;
            case CRUSHER -> stack.is(BitsmithCreate.ROD.get()) ? pieceMaterial(stack) : null;
        };
    }

    private static @Nullable BlockState pieceMaterial(ItemStack stack) {
        BlockState material = BitPieceItem.material(stack);
        return material.isAir() ? null : material;
    }

    /** What {@code count} items of {@code material} are cut into, in stacks of 64 at most. */
    public List<ItemStack> outputs(BlockState material, int count) {
        List<ItemStack> stacks = new ArrayList<>();
        int left = count * SPLIT;
        while (left > 0) {
            int amount = Math.min(STACK, left);
            left -= amount;
            stacks.add(switch (this) {
                case SLICER -> BitPieceItem.of(BitsmithCreate.SLICE.get(), material, amount);
                case CUTTER -> BitPieceItem.of(BitsmithCreate.ROD.get(), material, amount);
                case CRUSHER -> BitItem.of(material, amount);
            });
        }
        return stacks;
    }
}
