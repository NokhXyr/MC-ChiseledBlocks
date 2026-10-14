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
import com.nokhxyr.bitsmith.item.BagContents;
import com.nokhxyr.bitsmith.item.BitBagItem;
import com.nokhxyr.bitsmith.item.ChiselItem;
import com.nokhxyr.bitsmith.item.ChiselSettings;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Map;
import java.util.WeakHashMap;

public final class BitsmithNetwork {
    private static final String VERSION = "2";
    /** Furthest a player may ask for a grid, in blocks, squared. */
    private static final double RESYNC_RANGE_SQUARED = 64 * 64;
    private static final int RESYNCS_PER_TICK = 8;
    private static final Map<ServerPlayer, long[]> RESYNC_BUDGET = new WeakHashMap<>();

    private BitsmithNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(Bitsmith.MOD_ID).versioned(VERSION);
        registrar.playToServer(ChiselPayload.TYPE, ChiselPayload.CODEC, BitsmithNetwork::onChisel);
        registrar.playToServer(CyclePayload.TYPE, CyclePayload.CODEC, BitsmithNetwork::onCycle);
        registrar.playToServer(ResyncPayload.TYPE, ResyncPayload.CODEC, BitsmithNetwork::onResync);
        registrar.playToClient(GridDeltaPayload.TYPE, GridDeltaPayload.CODEC, BitsmithNetwork::onDelta);
        registrar.playToClient(GridFullPayload.TYPE, GridFullPayload.CODEC, BitsmithNetwork::onFull);
    }

    private static void onChisel(ChiselPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ChiselLogic.chisel(player, payload.pos(), payload.voxel(), payload.face());
        }
    }

    private static void onCycle(CyclePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof ChiselItem) {
            ChiselSettings current = ChiselItem.settings(held);
            ChiselSettings next = payload.size() ? current.withNextSize() : current.withNextMode();
            held.set(BitsmithComponents.CHISEL_SETTINGS.get(), next);
            player.displayClientMessage(next.describe(), true);
        } else if (held.getItem() instanceof BitBagItem && !payload.size()) {
            BagContents next = BitBagItem.contents(held).selectNext();
            BitBagItem.setContents(held, next);
            BagContents.Entry entry = next.selectedEntry();
            if (entry != null) {
                player.displayClientMessage(Component.translatable("bitsmith.bag.selected",
                        entry.material().getBlock().getName(), entry.count()), true);
            }
        }
    }

    private static void onResync(ResyncPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)
                || player.distanceToSqr(payload.pos().getCenter()) > RESYNC_RANGE_SQUARED
                || !player.level().isLoaded(payload.pos())
                || !takeResyncBudget(player)) {
            return;
        }
        if (player.level().getBlockEntity(payload.pos()) instanceof CarvedBlockEntity carved) {
            PacketDistributor.sendToPlayer(player, GridFullPayload.of(payload.pos(), carved.getGrid()));
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
        carved.setGrid(result);
        // A mismatch right after a local prediction is expected: the server has not applied the
        // later strokes yet. Only a lasting mismatch means a real desync.
        if (result.hashCode() != payload.expectedHash()
                && !PredictionTracker.isRecent(payload.pos(), level.getGameTime())) {
            PacketDistributor.sendToServer(new ResyncPayload(payload.pos()));
        }
    }

    private static void onFull(GridFullPayload payload, IPayloadContext context) {
        Level level = context.player().level();
        if (level.getBlockEntity(payload.pos()) instanceof CarvedBlockEntity carved) {
            carved.setGrid(payload.toGrid());
        }
    }

    private static boolean takeResyncBudget(ServerPlayer player) {
        long[] budget = RESYNC_BUDGET.computeIfAbsent(player, p -> new long[2]);
        long now = player.level().getGameTime();
        if (budget[0] != now) {
            budget[0] = now;
            budget[1] = 0;
        }
        return budget[1]++ < RESYNCS_PER_TICK;
    }
}
