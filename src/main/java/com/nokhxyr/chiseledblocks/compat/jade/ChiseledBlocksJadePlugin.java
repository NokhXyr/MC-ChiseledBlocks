/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.compat.jade;

import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.block.ChiseledBlock;
import com.nokhxyr.chiseledblocks.block.ChiseledBlockEntity;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.item.GridSummary;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade: a carved block shows what it is made of. Only loaded when Jade is installed.
 */
@WailaPlugin
public final class ChiseledBlocksJadePlugin implements IWailaPlugin {
    private static final int LISTED = 4;
    /** How many times Jade asked for the tooltip, for the playtest. */
    public static int carvedTooltips;

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ChiseledProvider.INSTANCE, ChiseledBlock.class);
    }

    private enum ChiseledProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof ChiseledBlockEntity carved) || carved.getGrid().isEmpty()) {
                return;
            }
            carvedTooltips++;
            GridSummary summary = GridSummary.of(carved.getGrid());
            for (int i = 0; i < Math.min(LISTED, summary.materials().size()); i++) {
                GridSummary.Entry entry = summary.materials().get(i);
                tooltip.add(Component.translatable("chiseledblocks.jade.material", entry.material().getBlock().getName(),
                        entry.bits() * 100 / PixelGrid.VOLUME).withStyle(ChatFormatting.GRAY));
            }
            if (summary.materials().size() > LISTED) {
                tooltip.add(Component.translatable("chiseledblocks.chiseled.more", summary.materials().size() - LISTED)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            tooltip.add(Component.translatable("chiseledblocks.chiseled.volume", summary.filled(), PixelGrid.VOLUME,
                    summary.filled() * 100 / PixelGrid.VOLUME).withStyle(ChatFormatting.DARK_GRAY));
        }

        @Override
        public ResourceLocation getUid() {
            return ChiseledBlocks.id("chiseled_block");
        }
    }
}
