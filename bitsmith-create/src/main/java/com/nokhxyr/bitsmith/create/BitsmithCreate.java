/*
 * Bitsmith: Create
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.create;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.create.client.CreateClient;
import com.simibubi.create.api.stress.BlockStressValues;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

/**
 * Bitsmith: Create. Bulk bit production is only possible with this addon, on a Create belt line:
 * the Bit Slicer cuts blocks into slices, the Bit Cutter slices into rods, the Bit Crusher rods
 * into bits. Bitsmith alone makes bits by hand, with the chisel.
 */
@Mod(BitsmithCreate.MOD_ID)
public final class BitsmithCreate {
    public static final String MOD_ID = "bitsmith_create";
    /** Stress each machine puts on its network, per rpm, like Create's press. */
    private static final double STRESS_IMPACT = 8.0;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    public static final Map<LineStage, DeferredBlock<LineMachineBlock>> MACHINES = new EnumMap<>(LineStage.class);
    public static final Map<LineStage, DeferredItem<BlockItem>> MACHINE_ITEMS = new EnumMap<>(LineStage.class);

    static {
        for (LineStage stage : LineStage.values()) {
            DeferredBlock<LineMachineBlock> block = BLOCKS.register(stage.id(), () -> new LineMachineBlock(
                    BlockBehaviour.Properties.of().strength(3.5F, 6.0F).sound(SoundType.WOOD)
                            .requiresCorrectToolForDrops().noOcclusion(), stage));
            MACHINES.put(stage, block);
            MACHINE_ITEMS.put(stage, ITEMS.registerSimpleBlockItem(stage.id(), block));
        }
    }

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LineMachineBlockEntity>> LINE_ENTITY =
            BLOCK_ENTITIES.register("bit_line_machine", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new LineMachineBlockEntity(BitsmithCreate.LINE_ENTITY.get(), pos, state),
                    MACHINES.get(LineStage.SLICER).get(), MACHINES.get(LineStage.CUTTER).get(),
                    MACHINES.get(LineStage.CRUSHER).get()).build(null));

    /** 16 x 16 x 1 bits of one material, made by the slicer. */
    public static final DeferredItem<BitPieceItem> SLICE = ITEMS.register("bit_slice",
            () -> new BitPieceItem(new Item.Properties(), "item.bitsmith_create.bit_slice.named"));
    /** 16 x 1 x 1 bits of one material, made by the cutter. */
    public static final DeferredItem<BitPieceItem> ROD = ITEMS.register("bit_rod",
            () -> new BitPieceItem(new Item.Properties(), "item.bitsmith_create.bit_rod.named"));

    public BitsmithCreate(IEventBus modEventBus, Dist dist) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(BitsmithCreate::onCommonSetup);
        modEventBus.addListener(BitsmithCreate::onCreativeTab);
        if (dist.isClient()) {
            CreateClient.register(modEventBus);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> MACHINES.values().forEach(block -> BlockStressValues.IMPACTS.register(block.get(), () -> STRESS_IMPACT)));
    }

    /** The machines go into Bitsmith's own creative tab. */
    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ResourceKey.create(Registries.CREATIVE_MODE_TAB, Bitsmith.id("main"))) {
            for (LineStage stage : LineStage.values()) {
                event.accept(MACHINE_ITEMS.get(stage).get());
            }
        }
    }
}
