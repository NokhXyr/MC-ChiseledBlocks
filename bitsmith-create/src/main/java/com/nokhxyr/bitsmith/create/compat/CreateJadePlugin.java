/*
 * Bitsmith: Create
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.create.compat;

import com.nokhxyr.bitsmith.create.BitsmithCreate;
import com.nokhxyr.bitsmith.create.MechanicalBitMillBlock;
import com.nokhxyr.bitsmith.create.MechanicalBitMillBlockEntity;
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

/** Jade: what a Mechanical Bit Mill holds. Only loaded when Jade is installed. */
@WailaPlugin
public final class CreateJadePlugin implements IWailaPlugin {
    /** How many times Jade asked for the tooltip, for the playtest. */
    public static int millTooltips;

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(MillProvider.INSTANCE, MechanicalBitMillBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(MillProvider.INSTANCE, MechanicalBitMillBlock.class);
    }

    private enum MillProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof MechanicalBitMillBlockEntity mill) {
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
            tooltip.add(Component.translatable("bitsmith_create.jade.blocks", data.getInt("blocks")));
            int bits = data.getInt("bits");
            tooltip.add(bits == 0 ? Component.translatable("bitsmith_create.jade.empty")
                    : Component.translatable("bitsmith_create.jade.bits", bits,
                    Block.stateById(data.getInt("material")).getBlock().getName()));
        }

        @Override
        public ResourceLocation getUid() {
            return BitsmithCreate.id("mechanical_bit_mill");
        }
    }
}
