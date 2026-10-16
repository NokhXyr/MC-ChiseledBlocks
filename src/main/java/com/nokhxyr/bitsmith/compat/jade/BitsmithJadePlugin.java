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
import com.nokhxyr.bitsmith.machine.BitMillBlock;
import com.nokhxyr.bitsmith.machine.BitMillBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade: a carved block shows what it is made of, a bit mill what it holds. Only loaded when Jade
 * is installed.
 */
@WailaPlugin
public final class BitsmithJadePlugin implements IWailaPlugin {
    private static final int LISTED = 4;
    /** How many times Jade asked for each tooltip, for the playtest. */
    public static int carvedTooltips;
    public static int millTooltips;

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(MillProvider.INSTANCE, BitMillBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CarvedProvider.INSTANCE, CarvedBlock.class);
        registration.registerBlockComponent(MillProvider.INSTANCE, BitMillBlock.class);
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

    private enum MillProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof BitMillBlockEntity mill) {
                data.putInt("blocks", mill.input().getCount());
                data.putInt("bits", mill.bits());
                data.putInt("material", Block.getId(mill.material()));
            }
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (!data.contains("bits")) {
                return;
            }
            millTooltips++;
            tooltip.add(Component.translatable("bitsmith.jade.mill.blocks", data.getInt("blocks")));
            int bits = data.getInt("bits");
            tooltip.add(bits == 0 ? Component.translatable("bitsmith.jade.mill.empty")
                    : Component.translatable("bitsmith.jade.mill.bits", bits,
                    Block.stateById(data.getInt("material")).getBlock().getName()));
        }

        @Override
        public ResourceLocation getUid() {
            return Bitsmith.id("bit_mill");
        }
    }
}
