/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.block;

import com.nokhxyr.chiseledblocks.admin.BlockProfiler;
import com.nokhxyr.chiseledblocks.admin.ChiseledIndex;
import com.nokhxyr.chiseledblocks.compat.cnb.ChiselsAndBitsImport;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGridBuilder;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGridInterner;
import com.nokhxyr.chiseledblocks.core.voxel.GridTransforms;
import com.nokhxyr.chiseledblocks.edit.EditBatcher;
import com.nokhxyr.chiseledblocks.item.GridSummary;
import com.nokhxyr.chiseledblocks.metrics.Metrics;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksComponents;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksRegistries;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Holds one interned {@link PixelGrid}. Never ticks and never renders by itself: the grid reaches
 * the chunk mesher through {@link ModelData}.
 *
 * <p>Server edits go to a pending builder and are applied once per tick by {@link EditBatcher},
 * which also sends one network update per block per tick.
 */
public final class ChiseledBlockEntity extends BlockEntity {
    public static final ModelProperty<PixelGrid> GRID = new ModelProperty<>();
    public static final PixelGridInterner INTERNER = new PixelGridInterner();
    private static final PixelGrid EMPTY = INTERNER.intern(PixelGrid.filled(PixelGrid.AIR));

    private PixelGrid grid = EMPTY;
    /** The material with the most pixels, worked out when first asked, forgotten with the grid. */
    private @Nullable BlockState mainMaterial;
    /** Orientation of the block state when the grid was last aligned. */
    private Direction gridFacing = Direction.NORTH;
    /** Set when the data came from Chisels & Bits: the block is saved again in Chiseled Blocks's format. */
    private boolean importedFromChiselsAndBits;
    private @Nullable PixelGridBuilder pending;
    /** Time spent reading this block from disk, kept until the level is known (profiling only). */
    private long loadNanos;

    public ChiseledBlockEntity(BlockPos pos, BlockState state) {
        super(ChiseledBlocksRegistries.CHISELED_BLOCK_ENTITY.get(), pos, state);
    }

    public PixelGrid getGrid() {
        return grid;
    }

    /** Replaces the grid. On the client this schedules exactly one rebuild of the section. */
    public void setGrid(PixelGrid newGrid) {
        PixelGrid interned = INTERNER.intern(newGrid);
        if (interned == grid) {
            return;
        }
        grid = interned;
        mainMaterial = null;
        if (level != null) {
            if (level.isClientSide) {
                onClientGridChanged();
            } else {
                setChanged();
            }
        }
    }

    /**
     * The material most of the carving is made of (air when empty): it decides how the block sounds,
     * how fast tools break it and how it stands explosions, and names its item.
     */
    public BlockState mainMaterial() {
        BlockState main = mainMaterial;
        if (main == null) {
            GridSummary summary = GridSummary.of(grid);
            main = summary.isEmpty() ? Blocks.AIR.defaultBlockState() : summary.materials().get(0).material();
            mainMaterial = main;
        }
        return main;
    }

    /**
     * Server side: puts a carving at {@code pos} in place of whatever is there. The grid is set
     * before the neighbours hear of the new block, so a torch or a button resting on it sees its
     * real shape and not the empty one of a block entity just created.
     */
    public static void place(ServerLevel level, BlockPos pos, PixelGrid grid) {
        BlockState state = ChiseledBlocksRegistries.CHISELED_BLOCK.get().defaultBlockState();
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        if (level.getBlockEntity(pos) instanceof ChiseledBlockEntity carved) {
            carved.setGrid(grid);
        }
        notifyNeighbours(level, pos, level.getBlockState(pos));
    }

    /**
     * Tells the blocks around that this one changed shape: what rests on it (torches, buttons,
     * redstone) checks it still holds, observers and redstone react, as for any block change.
     */
    public static void notifyNeighbours(ServerLevel level, BlockPos pos, BlockState state) {
        level.updateNeighborsAt(pos, state.getBlock());
        state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        state.updateIndirectNeighbourShapes(level, pos, Block.UPDATE_ALL);
    }

    /** Server side: queues an edit, applied at the end of the tick together with the others. */
    public void edit(Consumer<PixelGridBuilder> change) {
        if (pending == null) {
            pending = grid.toBuilder();
            EditBatcher.enqueue(this);
        }
        change.accept(pending);
    }

    /** Hands the pending builder over to the batcher and forgets it. */
    public @Nullable PixelGridBuilder takePending() {
        PixelGridBuilder taken = pending;
        pending = null;
        return taken;
    }

    /** The grid as it will be once this tick's pending edits are applied. */
    public PixelGrid effectiveGrid() {
        return pending != null ? pending.build() : grid;
    }

    /** Current state including edits not applied yet, for reading during the same tick. */
    public int peek(int index) {
        return pending != null ? pending.get(index) : grid.get(index);
    }

    private void onClientGridChanged() {
        if (Metrics.ENABLED) {
            Metrics.SECTION_DIRTY_MARKS.incrementAndGet();
        }
        requestModelDataUpdate();
        // Flag 0: the section is rebuilt off thread on the next frame, never synchronously.
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 0);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        ChiseledIndex.add(this);
        if (loadNanos != 0 && level != null) {
            // The level is not known yet while reading: the reading time is filed now.
            BlockProfiler.record(level, worldPosition, BlockProfiler.Kind.LOAD, loadNanos);
            loadNanos = 0;
        }
        if (importedFromChiselsAndBits && level != null && !level.isClientSide) {
            importedFromChiselsAndBits = false;
            setChanged();
        }
        if (level != null && !level.isClientSide && grid.isEmpty()) {
            // Normally filled within the same tick (placement, paste, item). If not, the block would
            // stay as an invisible empty shell: the batcher removes it at the end of the tick.
            EditBatcher.checkEmpty(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        ChiseledIndex.remove(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        long start = BlockProfiler.active ? System.nanoTime() : 0;
        super.saveAdditional(tag, registries);
        tag.put("grid", GridNbt.write(grid));
        tag.putString("facing", gridFacing.getSerializedName());
        if (start != 0 && level != null) {
            BlockProfiler.record(level, worldPosition, BlockProfiler.Kind.SAVE, System.nanoTime() - start);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        long start = BlockProfiler.active ? System.nanoTime() : 0;
        readGrid(tag, registries);
        if (start != 0) {
            loadNanos = System.nanoTime() - start;
        }
    }

    private void readGrid(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("grid")) {
            Direction saved = Direction.byName(tag.getString("facing"));
            gridFacing = saved != null && saved.getAxis().isHorizontal() ? saved : Direction.NORTH;
            setGrid(alignToState(GridNbt.read(tag.getCompound("grid"), registries)));
        } else if (ChiselsAndBitsImport.isChiselsAndBitsData(tag)) {
            // A block saved by Chisels & Bits, loaded through the registry aliases.
            PixelGrid imported = ChiselsAndBitsImport.read(tag, registries);
            gridFacing = Direction.NORTH;
            setGrid(alignToState(imported != null ? imported : EMPTY));
            importedFromChiselsAndBits = true;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        PixelGrid aligned = alignToState(grid);
        if (aligned != grid) {
            setGrid(aligned);
        }
    }

    /**
     * Turns the grid by the quarter turns between the orientation it was saved in and the
     * orientation of the block state, which another mod may have turned.
     */
    private PixelGrid alignToState(PixelGrid source) {
        BlockState state = getBlockState();
        if (!state.hasProperty(ChiseledBlock.FACING)) {
            return source;
        }
        Direction facing = state.getValue(ChiseledBlock.FACING);
        int turns = Math.floorMod(facing.get2DDataValue() - gridFacing.get2DDataValue(), 4);
        gridFacing = facing;
        PixelGrid turned = source;
        for (int i = 0; i < turns; i++) {
            turned = GridTransforms.rotate(turned, 1, material -> {
                BlockState rotated = Materials.state(material).rotate(Rotation.CLOCKWISE_90);
                return Materials.isValidMaterial(rotated) ? Materials.id(rotated) : material;
            });
        }
        return turned;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        PixelGrid fromItem = input.get(ChiseledBlocksComponents.GRID.get());
        if (fromItem != null) {
            setGrid(fromItem);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        components.set(ChiseledBlocksComponents.GRID.get(), grid);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("grid");
    }

    @Override
    public ModelData getModelData() {
        return ModelData.builder().with(GRID, grid).build();
    }
}
