/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.client.playtest.ClientPlaytest;
import com.nokhxyr.bitsmith.client.render.CarvedBakedModel;
import com.nokhxyr.bitsmith.client.render.CarvedColors;
import com.nokhxyr.bitsmith.client.render.GridItemOverrides;
import com.nokhxyr.bitsmith.client.render.MeshCache;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.item.BitItem;
import com.nokhxyr.bitsmith.item.CarvedBlockItem;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import com.nokhxyr.bitsmith.registry.CarvedItems;
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
@EventBusSubscriber(modid = Bitsmith.MOD_ID, value = Dist.CLIENT)
public final class BitsmithClient {
    /** The small centred cube a bit item is drawn as, one per material. */
    private static final Map<BlockState, BitGrid> BIT_SHAPES = new ConcurrentHashMap<>();

    private BitsmithClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (Boolean.getBoolean("bitsmith.clientTest")) {
            NeoForge.EVENT_BUS.register(new ClientPlaytest());
        }
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (BlockState state : BitsmithRegistries.CARVED_BLOCK.get().getStateDefinition().getPossibleStates()) {
            models.computeIfPresent(BlockModelShaper.stateToModelLocation(state), (location, json) -> new CarvedBakedModel(json));
        }
        models.computeIfPresent(ModelResourceLocation.inventory(BitsmithRegistries.CARVED_BLOCK_ITEM.getId()),
                (location, json) -> GridItemOverrides.wrap(json, stack -> stack.get(BitsmithComponents.GRID.get())));
        for (CarvedBlockItem item : CarvedItems.all()) {
            models.computeIfPresent(ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(item)),
                    (location, json) -> GridItemOverrides.wrap(json, item::displayGrid));
        }
        models.computeIfPresent(ModelResourceLocation.inventory(BitsmithRegistries.BIT.getId()),
                (location, json) -> GridItemOverrides.wrap(json, BitsmithClient::bitShape));
    }

    private static BitGrid bitShape(ItemStack stack) {
        BlockState material = BitItem.material(stack);
        if (!Materials.isValidMaterial(material)) {
            return null;
        }
        return BIT_SHAPES.computeIfAbsent(material, state -> CarvedBlockEntity.INTERNER.intern(
                new BitGridBuilder().fill(4, 4, 4, 12, 12, 12, Materials.id(state)).build()));
    }

    @SubscribeEvent
    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        MeshCache.clear();
    }

    @SubscribeEvent
    public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(CarvedColors::color, BitsmithRegistries.CARVED_BLOCK.get());
    }

    @SubscribeEvent
    public static void onItemColors(RegisterColorHandlersEvent.Item event) {
        List<ItemLike> tinted = new ArrayList<>(CarvedItems.all());
        tinted.add(BitsmithRegistries.CARVED_BLOCK_ITEM.get());
        tinted.add(BitsmithRegistries.BIT.get());
        event.register((stack, tintIndex) -> {
            int color = CarvedColors.color(null, null, null, tintIndex);
            return color == -1 ? -1 : FastColor.ARGB32.opaque(color);
        }, tinted.toArray(ItemLike[]::new));
    }

    @SubscribeEvent
    public static void onKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(BitsmithKeys.MODE);
        event.register(BitsmithKeys.SIZE);
        event.register(BitsmithKeys.RADIAL);
        event.register(BitsmithKeys.UNDO);
        event.register(BitsmithKeys.REDO);
        event.register(BitsmithKeys.SYMMETRY);
        event.register(BitsmithKeys.ADMIN);
    }
}
