/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.block;

import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.core.voxel.BitGridInterner;
import com.nokhxyr.bitsmith.edit.EditBatcher;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("grid", GridNbt.write(grid));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("grid")) {
            setGrid(GridNbt.read(tag.getCompound("grid"), registries));
        }
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
