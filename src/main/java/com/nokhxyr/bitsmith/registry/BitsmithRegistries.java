/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.registry;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.block.CarvedBlock;
import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.item.ChiselItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
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
                    .dynamicShape()
                    .pushReaction(PushReaction.BLOCK)));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CarvedBlockEntity>> CARVED_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("carved_block",
                    () -> BlockEntityType.Builder.of(CarvedBlockEntity::new, CARVED_BLOCK.get()).build(null));

    public static final DeferredItem<ChiselItem> IRON_CHISEL = ITEMS.register("iron_chisel",
            () -> new ChiselItem(new Item.Properties().stacksTo(1)));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.bitsmith"))
                    .icon(() -> IRON_CHISEL.get().getDefaultInstance())
                    .displayItems((params, output) -> output.accept(IRON_CHISEL.get()))
                    .build());

    private BitsmithRegistries() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        TABS.register(modEventBus);
    }
}
