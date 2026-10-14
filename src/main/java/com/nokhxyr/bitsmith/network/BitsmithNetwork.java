/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.network;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.edit.ChiselLogic;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class BitsmithNetwork {
    private static final String VERSION = "1";

    private BitsmithNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Bitsmith.MOD_ID).versioned(VERSION);
        registrar.playToServer(ChiselPayload.TYPE, ChiselPayload.CODEC, BitsmithNetwork::onChisel);
        registrar.playToClient(GridDeltaPayload.TYPE, GridDeltaPayload.CODEC, BitsmithNetwork::onDelta);
        registrar.playToClient(GridFullPayload.TYPE, GridFullPayload.CODEC, BitsmithNetwork::onFull);
    }

    private static void onChisel(ChiselPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ChiselLogic.chisel(player, payload.pos(), payload.voxel());
        }
    }

    private static void onDelta(GridDeltaPayload payload, IPayloadContext context) {
        Level level = context.player().level();
        if (!(level.getBlockEntity(payload.pos()) instanceof CarvedBlockEntity carved)) {
            return;
        }
        BitGridBuilder builder = carved.getGrid().toBuilder();
        int[] indices = payload.indices();
        int[] materials = payload.materials();
        for (int i = 0; i < indices.length; i++) {
            builder.set(indices[i], materials[i]);
        }
        BitGrid result = builder.build();
        if (result.hashCode() != payload.expectedHash()) {
            Bitsmith.LOGGER.warn("Carved block at {} is out of sync with the server", payload.pos());
        }
        carved.setGrid(result);
    }

    private static void onFull(GridFullPayload payload, IPayloadContext context) {
        Level level = context.player().level();
        if (level.getBlockEntity(payload.pos()) instanceof CarvedBlockEntity carved) {
            carved.setGrid(payload.toGrid());
        }
    }
}
