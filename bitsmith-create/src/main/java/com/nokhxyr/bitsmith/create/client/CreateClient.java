/*
 * Bitsmith: Create
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.create.client;

import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.client.render.CarvedColors;
import com.nokhxyr.bitsmith.client.render.GridItemOverrides;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.create.BitPieceItem;
import com.nokhxyr.bitsmith.create.BitsmithCreate;
import com.nokhxyr.bitsmith.create.client.ponder.BitsmithPonderPlugin;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.createmod.ponder.foundation.PonderIndex;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Client side of the addon: machine renderers, Ponder scenes, and slices and rods drawn in 3D in their material. */
public final class CreateClient {
    private static final Map<BlockState, BitGrid> SLICES = new ConcurrentHashMap<>();
    private static final Map<BlockState, BitGrid> RODS = new ConcurrentHashMap<>();

    private CreateClient() {
    }

    public static void register(IEventBus modEventBus) {
        LinePartials.init();
        PonderIndex.addPlugin(new BitsmithPonderPlugin());
        modEventBus.addListener(CreateClient::onRenderers);
        modEventBus.addListener(CreateClient::onClientSetup);
        modEventBus.addListener(CreateClient::onModifyBakingResult);
        modEventBus.addListener(CreateClient::onItemColors);
    }

    private static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BitsmithCreate.LINE_ENTITY.get(), LineMachineRenderer::new);
    }

    /** With Flywheel, the shaft is drawn as an instance; the moving tool still comes from the renderer. */
    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> SimpleBlockEntityVisualizer.builder(BitsmithCreate.LINE_ENTITY.get())
                .factory(SingleAxisRotatingVisual::shaft)
                .skipVanillaRender(machine -> false)
                .apply());
    }

    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        event.getModels().computeIfPresent(ModelResourceLocation.inventory(BitsmithCreate.SLICE.getId()),
                (location, json) -> GridItemOverrides.wrap(json, stack -> piece(stack, SLICES, 16, 1, 16)));
        event.getModels().computeIfPresent(ModelResourceLocation.inventory(BitsmithCreate.ROD.getId()),
                (location, json) -> GridItemOverrides.wrap(json, stack -> piece(stack, RODS, 16, 1, 1)));
    }

    /** A centred box of bits of the piece's material, shared between stacks of the same material. */
    private static BitGrid piece(ItemStack stack, Map<BlockState, BitGrid> cache, int width, int height, int depth) {
        BlockState material = BitPieceItem.material(stack);
        if (!Materials.isValidMaterial(material)) {
            return null;
        }
        return cache.computeIfAbsent(material, state -> {
            int x = (16 - width) / 2;
            int y = (16 - height) / 2;
            int z = (16 - depth) / 2;
            return CarvedBlockEntity.INTERNER.intern(new BitGridBuilder()
                    .fill(x, y, z, x + width, y + height, z + depth, Materials.id(state)).build());
        });
    }

    private static void onItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            int color = CarvedColors.color(null, null, null, tintIndex);
            return color == -1 ? -1 : FastColor.ARGB32.opaque(color);
        }, BitsmithCreate.SLICE.get(), BitsmithCreate.ROD.get());
    }
}
