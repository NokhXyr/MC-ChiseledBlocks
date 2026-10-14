/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith;

import com.mojang.logging.LogUtils;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.edit.EditBatcher;
import com.nokhxyr.bitsmith.network.BitsmithNetwork;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Bitsmith.MOD_ID)
public final class Bitsmith {
    public static final String MOD_ID = "bitsmith";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Bitsmith(IEventBus modEventBus) {
        BitsmithComponents.register(modEventBus);
        BitsmithRegistries.register(modEventBus);
        modEventBus.addListener(BitsmithNetwork::register);
        modEventBus.addListener((FMLCommonSetupEvent event) -> Materials.checkAirIsZero());
        NeoForge.EVENT_BUS.addListener(EditBatcher::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(EditBatcher::onServerStopped);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
