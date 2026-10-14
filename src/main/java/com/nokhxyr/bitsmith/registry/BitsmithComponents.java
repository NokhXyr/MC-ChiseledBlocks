/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.registry;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.block.GridCodecs;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.item.BagContents;
import com.nokhxyr.bitsmith.item.ChiselSettings;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class BitsmithComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Bitsmith.MOD_ID);

    /** Material of a bit item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockState>> BIT_MATERIAL =
            COMPONENTS.registerComponentType("bit_material", builder -> builder
                    .persistent(BlockState.CODEC)
                    .networkSynchronized(ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY)));

    /** Carving held by a carved block item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BitGrid>> GRID =
            COMPONENTS.registerComponentType("grid", builder -> builder
                    .persistent(GridCodecs.CODEC)
                    .networkSynchronized(GridCodecs.STREAM_CODEC)
                    .cacheEncoding());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ChiselSettings>> CHISEL_SETTINGS =
            COMPONENTS.registerComponentType("chisel_settings", builder -> builder
                    .persistent(ChiselSettings.CODEC)
                    .networkSynchronized(ChiselSettings.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BagContents>> BAG_CONTENTS =
            COMPONENTS.registerComponentType("bag_contents", builder -> builder
                    .persistent(BagContents.CODEC)
                    .networkSynchronized(BagContents.STREAM_CODEC));

    private BitsmithComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
