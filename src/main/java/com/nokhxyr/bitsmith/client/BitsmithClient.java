/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.client.render.CarvedBakedModel;
import com.nokhxyr.bitsmith.client.render.CarvedColors;
import com.nokhxyr.bitsmith.client.render.MeshCache;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.util.Map;

/** Client side registration on the mod bus. */
@EventBusSubscriber(modid = Bitsmith.MOD_ID, value = Dist.CLIENT)
public final class BitsmithClient {

    private BitsmithClient() {
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (BlockState state : BitsmithRegistries.CARVED_BLOCK.get().getStateDefinition().getPossibleStates()) {
            ModelResourceLocation location = BlockModelShaper.stateToModelLocation(state);
            BakedModel json = models.get(location);
            if (json != null) {
                models.put(location, new CarvedBakedModel(json));
            }
        }
    }

    @SubscribeEvent
    public static void onBakingCompleted(ModelEvent.BakingCompleted event) {
        MeshCache.clear();
    }

    @SubscribeEvent
    public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(CarvedColors::color, BitsmithRegistries.CARVED_BLOCK.get());
    }
}
