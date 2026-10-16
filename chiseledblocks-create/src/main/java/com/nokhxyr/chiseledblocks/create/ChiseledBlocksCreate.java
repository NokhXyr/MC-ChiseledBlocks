/*
 * Chiseled Blocks: Create
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.create;

import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.create.client.CreateClient;
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
 * Chiseled Blocks: Create. Bulk bit production is only possible with this addon, on a Create belt line:
 * the Block Slicer cuts blocks into slices, the Slice Cutter slices into rods, the Pixel Crusher rods
 * into pixels. Chiseled Blocks alone makes pixels by hand, with the chisel.
 */
@Mod(ChiseledBlocksCreate.MOD_ID)
public final class ChiseledBlocksCreate {
    public static final String MOD_ID = "chiseledblocks_create";
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
            BLOCK_ENTITIES.register("line_machine", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new LineMachineBlockEntity(ChiseledBlocksCreate.LINE_ENTITY.get(), pos, state),
                    MACHINES.get(LineStage.SLICER).get(), MACHINES.get(LineStage.CUTTER).get(),
                    MACHINES.get(LineStage.CRUSHER).get()).build(null));

    /** 16 x 16 x 1 bits of one material, made by the slicer. */
    public static final DeferredItem<PieceItem> SLICE = ITEMS.register("slice",
            () -> new PieceItem(new Item.Properties(), "item.chiseledblocks_create.slice.named"));
    /** 16 x 1 x 1 bits of one material, made by the cutter. */
    public static final DeferredItem<PieceItem> ROD = ITEMS.register("rod",
            () -> new PieceItem(new Item.Properties(), "item.chiseledblocks_create.rod.named"));

    public ChiseledBlocksCreate(IEventBus modEventBus, Dist dist) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener(ChiseledBlocksCreate::onCommonSetup);
        modEventBus.addListener(ChiseledBlocksCreate::onCreativeTab);
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

    /** The machines go into Chiseled Blocks's own creative tab. */
    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ResourceKey.create(Registries.CREATIVE_MODE_TAB, ChiseledBlocks.id("main"))) {
            for (LineStage stage : LineStage.values()) {
                event.accept(MACHINE_ITEMS.get(stage).get());
            }
        }
    }
}
