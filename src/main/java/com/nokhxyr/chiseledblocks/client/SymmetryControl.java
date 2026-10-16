/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client;

import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.core.edit.Symmetry;
import com.nokhxyr.chiseledblocks.edit.PlayerSymmetry;
import com.nokhxyr.chiseledblocks.network.SymmetryPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/** The local player's mirror setting: changed with the key or the tool menu, sent to the server. */
@EventBusSubscriber(modid = ChiseledBlocks.MOD_ID, value = Dist.CLIENT)
public final class SymmetryControl {

    private SymmetryControl() {
    }

    public static void set(Symmetry symmetry) {
        PlayerSymmetry.setOnClient(symmetry);
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new SymmetryPayload(symmetry));
        }
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(
                    Component.translatable("chiseledblocks.symmetry.set", name(symmetry)), true);
        }
    }

    public static Component name(Symmetry symmetry) {
        return Component.translatable("chiseledblocks.symmetry." + symmetry.name().toLowerCase(Locale.ROOT));
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        PacketDistributor.sendToServer(new SymmetryPayload(PlayerSymmetry.client()));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        while (ChiseledBlocksKeys.SYMMETRY.consumeClick()) {
            set(PlayerSymmetry.client().next());
        }
    }
}
