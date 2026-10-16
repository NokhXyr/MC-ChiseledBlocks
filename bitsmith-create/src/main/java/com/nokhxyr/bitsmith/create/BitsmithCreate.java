/*
 * Bitsmith: Create
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.create;

import com.nokhxyr.bitsmith.Bitsmith;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Bitsmith: Create. Bulk bit production is only possible with this addon, through the
 * Mechanical Bit Mill, a Create machine: Bitsmith alone makes bits by hand, with the chisel.
 */
@Mod(BitsmithCreate.MOD_ID)
public final class BitsmithCreate {
    public static final String MOD_ID = "bitsmith_create";
    /** Stress the mill puts on its network, per rpm, like Create's mechanical saw. */
    private static final double STRESS_IMPACT = 4.0;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MOD_ID);

    public static final DeferredBlock<MechanicalBitMillBlock> MILL = BLOCKS.register("mechanical_bit_mill",
            () -> new MechanicalBitMillBlock(BlockBehaviour.Properties.of()
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<MechanicalBitMillBlockEntity>> MILL_ENTITY =
            BLOCK_ENTITIES.register("mechanical_bit_mill", () -> BlockEntityType.Builder.of(
                    (pos, state) -> new MechanicalBitMillBlockEntity(BitsmithCreate.MILL_ENTITY.get(), pos, state), MILL.get()).build(null));

    public static final DeferredItem<BlockItem> MILL_ITEM = ITEMS.registerSimpleBlockItem("mechanical_bit_mill", MILL);

    /** Blocks and bits held by a mill, kept on the item when it is broken. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MillContents>> MILL_CONTENTS =
            COMPONENTS.registerComponentType("mill_contents", builder -> builder
                    .persistent(MillContents.CODEC)
                    .networkSynchronized(MillContents.STREAM_CODEC));

    public BitsmithCreate(IEventBus modEventBus, ModContainer container, Dist dist) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        COMPONENTS.register(modEventBus);
        modEventBus.addListener(BitsmithCreate::onCommonSetup);
        modEventBus.addListener(BitsmithCreate::onCapabilities);
        modEventBus.addListener(BitsmithCreate::onCreativeTab);
        if (dist.isClient()) {
            modEventBus.addListener(Client::onRenderers);
            modEventBus.addListener(Client::onClientSetup);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> BlockStressValues.IMPACTS.register(MILL.get(), () -> STRESS_IMPACT));
    }

    /** The mill's inventory, for Create's logistics, hoppers and storage mods' buses, on every side. */
    private static void onCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, MILL_ENTITY.get(), (mill, side) -> mill.handler());
    }

    /** The mill goes into Bitsmith's own creative tab. */
    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == ResourceKey.create(Registries.CREATIVE_MODE_TAB, Bitsmith.id("main"))) {
            event.accept(MILL_ITEM.get());
        }
    }

    /** Client only: the shaft turning through the mill, with or without Flywheel. */
    private static final class Client {
        private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(MILL_ENTITY.get(), ShaftRenderer::new);
        }

        private static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> SimpleBlockEntityVisualizer.builder(MILL_ENTITY.get())
                    .factory(SingleAxisRotatingVisual::shaft)
                    .skipVanillaRender(mill -> false)
                    .apply());
        }
    }
}
