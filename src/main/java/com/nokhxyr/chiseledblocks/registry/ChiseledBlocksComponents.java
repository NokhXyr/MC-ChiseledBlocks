/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.registry;

import com.mojang.serialization.Codec;
import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.block.GridCodecs;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.item.BagContents;
import com.nokhxyr.chiseledblocks.item.ChiselSettings;
import com.nokhxyr.chiseledblocks.item.PatternMode;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ChiseledBlocksComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ChiseledBlocks.MOD_ID);

    /** Material of a bit item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BlockState>> PIXEL_MATERIAL =
            COMPONENTS.registerComponentType("pixel_material", builder -> builder
                    .persistent(BlockState.CODEC)
                    .networkSynchronized(ByteBufCodecs.idMapper(Block.BLOCK_STATE_REGISTRY)));

    /** Carving held by a carved block item. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PixelGrid>> GRID =
            COMPONENTS.registerComponentType("grid", builder -> builder
                    .persistent(GridCodecs.CODEC)
                    .networkSynchronized(GridCodecs.STREAM_CODEC)
                    .cacheEncoding());

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ChiselSettings>> CHISEL_SETTINGS =
            COMPONENTS.registerComponentType("chisel_settings", builder -> builder
                    .persistent(ChiselSettings.CODEC)
                    .networkSynchronized(ChiselSettings.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PatternMode>> PATTERN_MODE =
            COMPONENTS.registerComponentType("pattern_mode", builder -> builder
                    .persistent(PatternMode.CODEC)
                    .networkSynchronized(PatternMode.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SAW_THICKNESS =
            COMPONENTS.registerComponentType("saw_thickness", builder -> builder
                    .persistent(Codec.intRange(1, 8))
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BagContents>> BAG_CONTENTS =
            COMPONENTS.registerComponentType("bag_contents", builder -> builder
                    .persistent(BagContents.CODEC)
                    .networkSynchronized(BagContents.STREAM_CODEC));

    private ChiseledBlocksComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
