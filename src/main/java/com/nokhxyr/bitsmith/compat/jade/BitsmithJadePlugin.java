/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.compat.jade;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.block.CarvedBlock;
import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.item.GridSummary;
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
public final class BitsmithJadePlugin implements IWailaPlugin {
    private static final int LISTED = 4;
    /** How many times Jade asked for the tooltip, for the playtest. */
    public static int carvedTooltips;

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CarvedProvider.INSTANCE, CarvedBlock.class);
    }

    private enum CarvedProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (!(accessor.getBlockEntity() instanceof CarvedBlockEntity carved) || carved.getGrid().isEmpty()) {
                return;
            }
            carvedTooltips++;
            GridSummary summary = GridSummary.of(carved.getGrid());
            for (int i = 0; i < Math.min(LISTED, summary.materials().size()); i++) {
                GridSummary.Entry entry = summary.materials().get(i);
                tooltip.add(Component.translatable("bitsmith.jade.material", entry.material().getBlock().getName(),
                        entry.bits() * 100 / BitGrid.VOLUME).withStyle(ChatFormatting.GRAY));
            }
            if (summary.materials().size() > LISTED) {
                tooltip.add(Component.translatable("bitsmith.carved.more", summary.materials().size() - LISTED)
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            tooltip.add(Component.translatable("bitsmith.carved.volume", summary.filled(), BitGrid.VOLUME,
                    summary.filled() * 100 / BitGrid.VOLUME).withStyle(ChatFormatting.DARK_GRAY));
        }

        @Override
        public ResourceLocation getUid() {
            return Bitsmith.id("carved_block");
        }
    }
}
