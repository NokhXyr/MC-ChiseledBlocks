/*
 * Chiseled Blocks: Create
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.create;

import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A machine of the pixel line, placed one block above a Create belt or a depot. A shaft runs through
 * it from front to back; its tool works on the items below.
 */
public final class LineMachineBlock extends HorizontalKineticBlock implements IBE<LineMachineBlockEntity> {
    /** The body; the tool reaches below it into the belt's space only while it works. */
    private static final VoxelShape SHAPE = Block.box(0, 2, 0, 16, 16, 16);

    private final LineStage stage;

    public LineMachineBlock(Properties properties, LineStage stage) {
        super(properties);
        this.stage = stage;
    }

    public LineStage stage() {
        return stage;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face.getAxis() == state.getValue(HORIZONTAL_FACING).getAxis();
    }

    @Override
    public Class<LineMachineBlockEntity> getBlockEntityClass() {
        return LineMachineBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends LineMachineBlockEntity> getBlockEntityType() {
        return ChiseledBlocksCreate.LINE_ENTITY.get();
    }
}
