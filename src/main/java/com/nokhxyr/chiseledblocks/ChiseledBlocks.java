/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks;

import com.mojang.logging.LogUtils;
import com.nokhxyr.chiseledblocks.admin.ChiseledBlocksCommand;
import com.nokhxyr.chiseledblocks.admin.ChiseledBlocksPermissions;
import com.nokhxyr.chiseledblocks.admin.BlockProfiler;
import com.nokhxyr.chiseledblocks.admin.ChiseledIndex;
import com.nokhxyr.chiseledblocks.block.Materials;
import com.nokhxyr.chiseledblocks.compat.cnb.ChiselsAndBitsImport;
import com.nokhxyr.chiseledblocks.edit.PixelInventory;
import com.nokhxyr.chiseledblocks.edit.EditBatcher;
import com.nokhxyr.chiseledblocks.edit.EditHistory;
import com.nokhxyr.chiseledblocks.network.ChiseledBlocksNetwork;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksComponents;
import com.nokhxyr.chiseledblocks.registry.ChiseledBlocksRegistries;
import com.nokhxyr.chiseledblocks.registry.ChiseledItems;
import com.nokhxyr.chiseledblocks.registry.ChiseledItemsPack;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(ChiseledBlocks.MOD_ID)
public final class ChiseledBlocks {
    public static final String MOD_ID = "chiseledblocks";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ChiseledBlocks(IEventBus modEventBus) {
        ChiseledBlocksComponents.register(modEventBus);
        ChiseledBlocksRegistries.register(modEventBus);
        ChiseledItems.register(modEventBus);
        modEventBus.addListener(ChiseledItemsPack::onAddPackFinders);
        if (!ModList.get().isLoaded(ChiselsAndBitsImport.MOD_ID)) {
            ChiseledBlocksRegistries.aliasChiselsAndBits();
        }
        modEventBus.addListener(ChiseledBlocksNetwork::register);
        modEventBus.addListener((FMLCommonSetupEvent event) -> Materials.checkAirIsZero());
        NeoForge.EVENT_BUS.addListener(EditBatcher::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(EditBatcher::onServerStopped);
        NeoForge.EVENT_BUS.addListener(EditHistory::onServerStopped);
        NeoForge.EVENT_BUS.addListener(PixelInventory::onPickup);
        NeoForge.EVENT_BUS.addListener(ChiseledBlocksCommand::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(ChiseledBlocksPermissions::onGatherNodes);
        NeoForge.EVENT_BUS.addListener(ChiseledIndex::onServerStopped);
        NeoForge.EVENT_BUS.addListener(BlockProfiler::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(BlockProfiler::onServerStopped);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
