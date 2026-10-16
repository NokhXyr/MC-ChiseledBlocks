/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.block;

import com.nokhxyr.bitsmith.admin.BlockProfiler;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.Face;
import com.nokhxyr.bitsmith.registry.CarvedItems;
import com.nokhxyr.bitsmith.shape.ShapeCache;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * The block every carving lives in. All per position data sits in {@link CarvedBlockEntity};
 * this class only answers shape and culling questions from it.
 *
 * <h2>Why the occlusion shape is a full cube</h2>
 * Vanilla caches face culling per pair of block states, not per position, and every carved
 * block shares one state. So the culling answer must not depend on the carving:
 * <ul>
 *   <li>The block cannot occlude ({@code noOcclusion}), so neighbours always draw their faces
 *       towards it. Correct for any carving, at the price of some hidden faces.</li>
 *   <li>Its own occlusion shape is a constant full cube. When vanilla asks whether one of our
 *       boundary faces is hidden by an opaque neighbour, the answer only depends on that
 *       neighbour, which is exactly what the pair cache assumes.</li>
 * </ul>
 * Light is treated as for glass: it passes through.
 */
public final class CarvedBlock extends Block implements EntityBlock {

    /**
     * Horizontal orientation of the block. Tools that turn whole builds (WorldEdit, structure
     * blocks, Create) only turn block states; the block entity compares this with the orientation
     * its grid was saved in and turns the grid to match.
     */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public CarvedBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CarvedBlockEntity(pos, state);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        BitGrid grid = gridAt(level, pos);
        if (grid == null || grid.isEmpty()) {
            return super.getCloneItemStack(level, pos, state);
        }
        return CarvedItems.stackFor(grid);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    private static BitGrid gridAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CarvedBlockEntity carved ? carved.getGrid() : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (BlockProfiler.active && level instanceof Level world && !world.isClientSide) {
            long start = System.nanoTime();
            VoxelShape shape = shapeAt(level, pos);
            BlockProfiler.record(world, pos, BlockProfiler.Kind.SHAPE, System.nanoTime() - start);
            return shape;
        }
        return shapeAt(level, pos);
    }

    private static VoxelShape shapeAt(BlockGetter level, BlockPos pos) {
        BitGrid grid = gridAt(level, pos);
        return grid == null ? Shapes.block() : ShapeCache.get(grid);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.block();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState state, BlockState neighborState, Direction dir) {
        // Only reached for neighbours outside the solid layer (glass, leaves): position aware, no cache.
        BitGrid grid = gridAt(level, pos);
        return grid != null && grid.isFaceCovered(Face.values()[dir.ordinal()], Materials.OPAQUE);
    }
}
