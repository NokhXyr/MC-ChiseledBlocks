/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client;

import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import com.nokhxyr.chiseledblocks.block.Materials;
import com.nokhxyr.chiseledblocks.client.playtest.ClientPlaytest;
import com.nokhxyr.chiseledblocks.client.render.ChiseledBakedModel;
import com.nokhxyr.chiseledblocks.client.render.ChiseledColors;
import com.nokhxyr.chiseledblocks.client.render.GridItemOverrides;
import com.nokhxyr.chiseledblocks.client.render.MeshCache;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGridBuilder;
import com.nokhxyr.chiseledblocks.item.PixelItem;
import com.nokhxyr.chiseledblocks.item.ChiseledBlockItem;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksComponents;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksRegistries;
import com.nokhxyr.chiseledblocks.registry.ChiseledItems;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client side registration on the mod bus. */
@EventBusSubscriber(modid = ChiseledBlocks.MOD_ID, value = Dist.CLIENT)
public final class ChiseledBlocksClient {
    /** The small centred cube a bit item is drawn as, one per material. */
    private static final Map<BlockState, PixelGrid> PIXEL_SHAPES = new ConcurrentHashMap<>();

    private ChiseledBlocksClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("chiseledblocks.clientTest")) {
            NeoForge.EVENT_BUS.register(new ClientPlaytest());
        }
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (BlockState state : ChiseledBlocksRegistries.CHISELED_BLOCK.get().getStateDefinition().getPossibleStates()) {
            models.computeIfPresent(BlockModelShaper.stateToModelLocation(state), (location, json) -> new ChiseledBakedModel(json));
        }
        models.computeIfPresent(ModelResourceLocation.inventory(ChiseledBlocksRegistries.CHISELED_BLOCK_ITEM.getId()),
                (location, json) -> GridItemOverrides.wrap(json, stack -> stack.get(ChiseledBlocksComponents.GRID.get())));
        for (ChiseledBlockItem item : ChiseledItems.all()) {
            models.computeIfPresent(ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(item)),
                    (location, json) -> GridItemOverrides.wrap(json, item::displayGrid));
        }
        models.computeIfPresent(ModelResourceLocation.inventory(ChiseledBlocksRegistries.PIXEL.getId()),
                (location, json) -> GridItemOverrides.wrap(json, ChiseledBlocksClient::bitShape));
    }

    private static PixelGrid bitShape(ItemStack stack) {
        BlockState material = PixelItem.material(stack);
        if (!Materials.isValidMaterial(material)) {
            return null;
        }
        return PIXEL_SHAPES.computeIfAbsent(material, state -> ChiseledBlockEntity.INTERNER.intern(
                new PixelGridBuilder().fill(4, 4, 4, 12, 12, 12, Materials.id(state)).build()));
    }

    @SubscribeEvent
    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        MeshCache.clear();
    }

    @SubscribeEvent
    public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(ChiseledColors::color, ChiseledBlocksRegistries.CHISELED_BLOCK.get());
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        List<ItemLike> tinted = new ArrayList<>(ChiseledItems.all());
        tinted.add(ChiseledBlocksRegistries.CHISELED_BLOCK_ITEM.get());
        tinted.add(ChiseledBlocksRegistries.PIXEL.get());
        event.register((stack, tintIndex) -> {
            int color = ChiseledColors.color(null, null, null, tintIndex);
            return color == -1 ? -1 : FastColor.ARGB32.opaque(color);
        }, tinted.toArray(ItemLike[]::new));
    }

    @SubscribeEvent
    public static void onKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ChiseledBlocksKeys.MODE);
        event.register(ChiseledBlocksKeys.SIZE);
        event.register(ChiseledBlocksKeys.RADIAL);
        event.register(ChiseledBlocksKeys.UNDO);
        event.register(ChiseledBlocksKeys.REDO);
        event.register(ChiseledBlocksKeys.SYMMETRY);
        event.register(ChiseledBlocksKeys.ADMIN);
    }
}
