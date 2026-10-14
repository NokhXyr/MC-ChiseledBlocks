/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.item;

import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * A carved block picked up with its carving. Placing it restores the carving through the block
 * entity's implicit components; a stack without a carving cannot be placed.
 */
public class CarvedBlockItem extends BlockItem {

    public CarvedBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        BitGrid grid = context.getItemInHand().get(BitsmithComponents.GRID.get());
        if (grid == null || grid.isEmpty()) {
            return InteractionResult.FAIL;
        }
        return super.place(context);
    }
}
