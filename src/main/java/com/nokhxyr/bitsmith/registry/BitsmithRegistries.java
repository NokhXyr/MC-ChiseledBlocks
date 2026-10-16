/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.registry;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.block.CarvedBlock;
import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.item.BitBagItem;
import com.nokhxyr.bitsmith.item.BitItem;
import com.nokhxyr.bitsmith.item.BlankPatternItem;
import com.nokhxyr.bitsmith.item.CarvedBlockItem;
import com.nokhxyr.bitsmith.item.ChiselItem;
import com.nokhxyr.bitsmith.item.PatternItem;
import com.nokhxyr.bitsmith.item.SawItem;
import com.nokhxyr.bitsmith.item.TapeMeasureItem;
import com.nokhxyr.bitsmith.item.WrenchItem;
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

public final class BitsmithRegistries {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Bitsmith.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Bitsmith.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Bitsmith.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Bitsmith.MOD_ID);

    public static final DeferredBlock<CarvedBlock> CARVED_BLOCK = BLOCKS.register("carved_block",
            () -> new CarvedBlock(BlockBehaviour.Properties.of()
                    .strength(1.5F, 6.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .dynamicShape()));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CarvedBlockEntity>> CARVED_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("carved_block",
                    () -> BlockEntityType.Builder.of(CarvedBlockEntity::new, CARVED_BLOCK.get()).build(null));

    public static final DeferredItem<CarvedBlockItem> CARVED_BLOCK_ITEM = ITEMS.register("carved_block",
            () -> new CarvedBlockItem(CARVED_BLOCK.get(), new Item.Properties()));

    public static final DeferredItem<ChiselItem> IRON_CHISEL = ITEMS.register("iron_chisel",
            () -> new ChiselItem(new Item.Properties().durability(2048), () -> Items.IRON_PICKAXE));

    public static final DeferredItem<BitItem> BIT = ITEMS.register("bit",
            () -> new BitItem(new Item.Properties()));

    public static final DeferredItem<BitBagItem> BIT_BAG = ITEMS.register("bit_bag",
            () -> new BitBagItem(new Item.Properties()));

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
                    .title(Component.translatable("itemGroup.bitsmith"))
                    .icon(() -> IRON_CHISEL.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(IRON_CHISEL.get());
                        output.accept(BIT_BAG.get());
                        output.accept(BLANK_PATTERN.get());
                        output.accept(WRENCH.get());
                        output.accept(SAW.get());
                        output.accept(TAPE_MEASURE.get());
                        // One carved block per material, each showing its example shape.
                        CarvedItems.all().forEach(output::accept);
                    })
                    .build());

    private BitsmithRegistries() {
    }

    /**
     * Makes worlds saved with Chisels & Bits load their chiseled blocks as carved blocks. Only when
     * Chisels & Bits is not installed: with both mods present its own entries must stay its own.
     */
    public static void aliasChiselsAndBits() {
        BLOCKS.addAlias(ResourceLocation.fromNamespaceAndPath("chiselsandbits", "chiseled"), CARVED_BLOCK.getId());
        BLOCK_ENTITIES.addAlias(ResourceLocation.fromNamespaceAndPath("chiselsandbits", "chiseled_block"), CARVED_BLOCK_ENTITY.getId());
        BLOCK_ENTITIES.addAlias(ResourceLocation.fromNamespaceAndPath("chiselsandbits", "chiseled"), CARVED_BLOCK_ENTITY.getId());
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        TABS.register(modEventBus);
    }
}
