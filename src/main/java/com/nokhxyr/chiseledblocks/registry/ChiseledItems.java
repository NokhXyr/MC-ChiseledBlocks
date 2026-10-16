/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.registry;

import com.mojang.serialization.MapCodec;
import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.block.ChiseledBlock;
import com.nokhxyr.chiseledblocks.block.Materials;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.item.ChiseledBlockItem;
import com.nokhxyr.chiseledblocks.item.GridSummary;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * One carved block item per carvable material, {@code chiseledblocks:chiseled/oak_planks} or
 * {@code chiseledblocks:chiseled/<mod>/<block>}, so carved blocks can be told apart by id in recipes,
 * filters, item lists and commands. A carving becomes the item of its main material.
 *
 * <p>The items are registered at startup from every block known by then that a player could
 * carve. They all place the same carved block; a material without its own item uses
 * {@code chiseledblocks:chiseled_block}.
 */
public final class ChiseledItems {
    private static final Map<Block, ChiseledBlockItem> BY_MATERIAL = new IdentityHashMap<>();
    private static final List<ChiseledBlockItem> ALL = new ArrayList<>();

    private static final DeferredRegister<LootItemFunctionType<?>> LOOT_FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, ChiseledBlocks.MOD_ID);

    /** Loot function turning a dropped carved block into the item of its main material. */
    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<MaterialItemFunction>> MATERIAL_ITEM =
            LOOT_FUNCTIONS.register("chiseled_item", () -> new LootItemFunctionType<>(MapCodec.unit(MaterialItemFunction.INSTANCE)));

    private ChiseledItems() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ChiseledItems::onRegister);
        LOOT_FUNCTIONS.register(modEventBus);
    }

    private static void onRegister(RegisterEvent event) {
        // Every mod's blocks are registered before any item.
        event.register(Registries.ITEM, helper -> {
            for (Block block : BuiltInRegistries.BLOCK) {
                if (isCarvable(block)) {
                    ChiseledBlockItem item = new ChiseledBlockItem(ChiseledBlocksRegistries.CHISELED_BLOCK.get(), new Item.Properties(), block);
                    helper.register(ChiseledBlocks.id(path(BuiltInRegistries.BLOCK.getKey(block))), item);
                    BY_MATERIAL.put(block, item);
                    ALL.add(item);
                }
            }
            ChiseledBlocks.LOGGER.info("Registered {} carved block items, one per carvable material", ALL.size());
        });
    }

    /** {@code carved/oak_planks} for Minecraft's blocks, {@code carved/<mod>/<block>} for the others. */
    static String path(ResourceLocation block) {
        return ResourceLocation.DEFAULT_NAMESPACE.equals(block.getNamespace())
                ? "chiseled/" + block.getPath()
                : "chiseled/" + block.getNamespace() + "/" + block.getPath();
    }

    /** Whether a player could carve this block as it is placed by default. */
    public static boolean isCarvable(Block block) {
        if (block instanceof ChiseledBlock) {
            return false;
        }
        try {
            BlockState state = block.defaultBlockState();
            return Materials.isValidMaterial(state)
                    && state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) >= 0
                    && state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
        } catch (RuntimeException e) {
            // A block that needs a real world to answer cannot be judged here: no own item.
            return false;
        }
    }

    /** The per material items, in block registry order. */
    public static List<ChiseledBlockItem> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** The item a carving is held as: the one of its main material, or the generic one. */
    public static Item itemFor(PixelGrid grid) {
        GridSummary summary = GridSummary.of(grid);
        if (!summary.isEmpty()) {
            ChiseledBlockItem item = BY_MATERIAL.get(summary.materials().get(0).material().getBlock());
            if (item != null) {
                return item;
            }
        }
        return ChiseledBlocksRegistries.CHISELED_BLOCK_ITEM.get();
    }

    /** A stack of the carving, as the item of its main material. */
    public static ItemStack stackFor(PixelGrid grid) {
        ItemStack stack = new ItemStack(itemFor(grid));
        stack.set(ChiseledBlocksComponents.GRID.get(), grid);
        return stack;
    }

    /** Gives an existing stack the item of its carving's main material, keeping everything else. */
    public static ItemStack withMaterialItem(ItemStack stack) {
        PixelGrid grid = stack.get(ChiseledBlocksComponents.GRID.get());
        if (grid == null || grid.isEmpty()) {
            return stack;
        }
        Item item = itemFor(grid);
        return stack.is(item) ? stack : stack.transmuteCopy(item, stack.getCount());
    }

    public static final class MaterialItemFunction implements LootItemFunction {
        static final MaterialItemFunction INSTANCE = new MaterialItemFunction();

        private MaterialItemFunction() {
        }

        @Override
        public LootItemFunctionType<MaterialItemFunction> getType() {
            return MATERIAL_ITEM.get();
        }

        @Override
        public ItemStack apply(ItemStack stack, LootContext context) {
            return withMaterialItem(stack);
        }
    }
}
