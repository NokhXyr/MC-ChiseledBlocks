/*
 * Chiseled Blocks: Create
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.create;

import com.nokhxyr.chiseledblocks.item.PixelItem;
import com.nokhxyr.chiseledblocks.registry.ChiseledItems;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The three machines of the pixel line. Each cuts every item it holds into 16 of the next piece:
 * a block into 16 slices, a slice into 16 rods, a rod into 16 pixels, so 4096 pixels per block.
 */
public enum LineStage {
    SLICER("block_slicer"),
    CUTTER("slice_cutter"),
    CRUSHER("pixel_crusher");

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
                    yield ChiseledItems.isCarvable(block) ? block.defaultBlockState() : null;
                }
                yield null;
            }
            case CUTTER -> stack.is(ChiseledBlocksCreate.SLICE.get()) ? pieceMaterial(stack) : null;
            case CRUSHER -> stack.is(ChiseledBlocksCreate.ROD.get()) ? pieceMaterial(stack) : null;
        };
    }

    private static @Nullable BlockState pieceMaterial(ItemStack stack) {
        BlockState material = PieceItem.material(stack);
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
                case SLICER -> PieceItem.of(ChiseledBlocksCreate.SLICE.get(), material, amount);
                case CUTTER -> PieceItem.of(ChiseledBlocksCreate.ROD.get(), material, amount);
                case CRUSHER -> PixelItem.of(material, amount);
            });
        }
        return stacks;
    }
}
