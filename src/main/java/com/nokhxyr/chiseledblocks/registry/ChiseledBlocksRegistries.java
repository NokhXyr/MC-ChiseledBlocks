/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.registry;

import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.block.ChiseledBlock;
import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import com.nokhxyr.chiseledblocks.item.PixelBagItem;
import com.nokhxyr.chiseledblocks.item.PixelItem;
import com.nokhxyr.chiseledblocks.item.BlankPatternItem;
import com.nokhxyr.chiseledblocks.item.ChiseledBlockItem;
import com.nokhxyr.chiseledblocks.item.ChiselItem;
import com.nokhxyr.chiseledblocks.item.CreativePixelBagItem;
import com.nokhxyr.chiseledblocks.item.PatternItem;
import com.nokhxyr.chiseledblocks.item.SawItem;
import com.nokhxyr.chiseledblocks.item.TapeMeasureItem;
import com.nokhxyr.chiseledblocks.item.WrenchItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ChiseledBlocksRegistries {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ChiseledBlocks.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ChiseledBlocks.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ChiseledBlocks.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ChiseledBlocks.MOD_ID);

    public static final DeferredBlock<ChiseledBlock> CHISELED_BLOCK = BLOCKS.register("chiseled_block",
            () -> new ChiseledBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .dynamicShape()));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChiseledBlockEntity>> CHISELED_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("chiseled_block",
                    () -> BlockEntityType.Builder.of(ChiseledBlockEntity::new, CHISELED_BLOCK.get()).build(null));

    public static final DeferredItem<ChiseledBlockItem> CHISELED_BLOCK_ITEM = ITEMS.register("chiseled_block",
            () -> new ChiseledBlockItem(CHISELED_BLOCK.get(), new Item.Properties()));

    public static final DeferredItem<ChiselItem> IRON_CHISEL = ITEMS.register("iron_chisel",
            () -> new ChiselItem(new Item.Properties().durability(2048), () -> Items.IRON_PICKAXE));

    public static final DeferredItem<PixelItem> PIXEL = ITEMS.register("pixel",
            () -> new PixelItem(new Item.Properties()));

    public static final DeferredItem<PixelBagItem> PIXEL_BAG = ITEMS.register("pixel_bag",
            () -> new PixelBagItem(new Item.Properties()));

    /** Creative and commands only: no recipe. */
    public static final DeferredItem<CreativePixelBagItem> CREATIVE_PIXEL_BAG = ITEMS.register("creative_pixel_bag",
            () -> new CreativePixelBagItem(new Item.Properties()));

    public static final DeferredItem<BlankPatternItem> BLANK_PATTERN = ITEMS.register("blank_pattern",
            () -> new BlankPatternItem(new Item.Properties()));

    public static final DeferredItem<PatternItem> PATTERN = ITEMS.register("pattern",
            () -> new PatternItem(new Item.Properties()));

    public static final DeferredItem<WrenchItem> WRENCH = ITEMS.register("wrench",
            () -> new WrenchItem(new Item.Properties().durability(512)));

    public static final DeferredItem<SawItem> SAW = ITEMS.register("saw",
            () -> new SawItem(new Item.Properties().durability(512)));

    public static final DeferredItem<TapeMeasureItem> TAPE_MEASURE = ITEMS.register("tape_measure",
            () -> new TapeMeasureItem(new Item.Properties()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.chiseledblocks"))
                    .icon(() -> IRON_CHISEL.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(IRON_CHISEL.get());
                        output.accept(PIXEL_BAG.get());
                        output.accept(CREATIVE_PIXEL_BAG.get());
                        output.accept(BLANK_PATTERN.get());
                        output.accept(WRENCH.get());
                        output.accept(SAW.get());
                        output.accept(TAPE_MEASURE.get());
                        // One carved block per material, each showing its example shape.
                        ChiseledItems.all().forEach(output::accept);
                    })
                    .build());

    private ChiseledBlocksRegistries() {
    }

    /**
     * Makes worlds saved with Chisels & Bits load their chiseled blocks as carved blocks. Only when
     * Chisels & Bits is not installed: with both mods present its own entries must stay its own.
     */
    public static void aliasChiselsAndBits() {
        BLOCKS.addAlias(ResourceLocation.fromNamespaceAndPath("chiselsandbits", "chiseled"), CHISELED_BLOCK.getId());
        BLOCK_ENTITIES.addAlias(ResourceLocation.fromNamespaceAndPath("chiselsandbits", "chiseled_block"), CHISELED_BLOCK_ENTITY.getId());
        BLOCK_ENTITIES.addAlias(ResourceLocation.fromNamespaceAndPath("chiselsandbits", "chiseled"), CHISELED_BLOCK_ENTITY.getId());
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        TABS.register(modEventBus);
    }
}
