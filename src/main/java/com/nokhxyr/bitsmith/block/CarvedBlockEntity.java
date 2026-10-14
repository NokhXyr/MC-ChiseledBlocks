/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.block;

import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.core.voxel.BitGridInterner;
import com.nokhxyr.bitsmith.core.voxel.GridTransforms;
import com.nokhxyr.bitsmith.edit.EditBatcher;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Holds one interned {@link BitGrid}. Never ticks and never renders by itself: the grid reaches
 * the chunk mesher through {@link ModelData}.
 *
 * <p>Server edits go to a pending builder and are applied once per tick by {@link EditBatcher},
 * which also sends one network update per block per tick.
 */
public final class CarvedBlockEntity extends BlockEntity {
    public static final ModelProperty<BitGrid> GRID = new ModelProperty<>();
    public static final BitGridInterner INTERNER = new BitGridInterner();
    private static final BitGrid EMPTY = INTERNER.intern(BitGrid.filled(BitGrid.AIR));

    private BitGrid grid = EMPTY;
    /** Orientation of the block state when the grid was last aligned. */
    private Direction gridFacing = Direction.NORTH;
    private @Nullable BitGridBuilder pending;

    public CarvedBlockEntity(BlockPos pos, BlockState state) {
        super(BitsmithRegistries.CARVED_BLOCK_ENTITY.get(), pos, state);
    }

    public BitGrid getGrid() {
        return grid;
    }

    /** Replaces the grid. On the client this schedules exactly one rebuild of the section. */
    public void setGrid(BitGrid newGrid) {
        BitGrid interned = INTERNER.intern(newGrid);
        if (interned == grid) {
            return;
        }
        grid = interned;
        if (level != null) {
            if (level.isClientSide) {
                onClientGridChanged();
            } else {
                setChanged();
            }
        }
    }

    /** Server side: queues an edit, applied at the end of the tick together with the others. */
    public void edit(Consumer<BitGridBuilder> change) {
        if (pending == null) {
            pending = grid.toBuilder();
            EditBatcher.enqueue(this);
        }
        change.accept(pending);
    }

    /** Hands the pending builder over to the batcher and forgets it. */
    public @Nullable BitGridBuilder takePending() {
        BitGridBuilder taken = pending;
        pending = null;
        return taken;
    }

    /** The grid as it will be once this tick's pending edits are applied. */
    public BitGrid effectiveGrid() {
        return pending != null ? pending.build() : grid;
    }

    /** Current state including edits not applied yet, for reading during the same tick. */
    public int peek(int index) {
        return pending != null ? pending.get(index) : grid.get(index);
    }

    private void onClientGridChanged() {
        requestModelDataUpdate();
        // Flag 0: the section is rebuilt off thread on the next frame, never synchronously.
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 0);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && grid.isEmpty()) {
            // Normally filled within the same tick (placement, paste, item). If not, the block would
            // stay as an invisible empty shell: the batcher removes it at the end of the tick.
            EditBatcher.checkEmpty(this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("grid", GridNbt.write(grid));
        tag.putString("facing", gridFacing.getSerializedName());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("grid")) {
            Direction saved = Direction.byName(tag.getString("facing"));
            gridFacing = saved != null && saved.getAxis().isHorizontal() ? saved : Direction.NORTH;
            setGrid(alignToState(GridNbt.read(tag.getCompound("grid"), registries)));
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        BitGrid aligned = alignToState(grid);
        if (aligned != grid) {
            setGrid(aligned);
        }
    }

    /**
     * Turns the grid by the quarter turns between the orientation it was saved in and the
     * orientation of the block state, which another mod may have turned.
     */
    private BitGrid alignToState(BitGrid source) {
        BlockState state = getBlockState();
        if (!state.hasProperty(CarvedBlock.FACING)) {
            return source;
        }
        Direction facing = state.getValue(CarvedBlock.FACING);
        int turns = Math.floorMod(facing.get2DDataValue() - gridFacing.get2DDataValue(), 4);
        gridFacing = facing;
        BitGrid turned = source;
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
        BitGrid fromItem = input.get(BitsmithComponents.GRID.get());
        if (fromItem != null) {
            setGrid(fromItem);
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        components.set(BitsmithComponents.GRID.get(), grid);
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
