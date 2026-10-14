/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.item;

import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Supplier;

/**
 * Left click removes bits, following the mode and size stored on the stack. The click itself is
 * intercepted on the client; vanilla block breaking never starts while a chisel is held.
 *
 * <p>A chisel cuts the materials its matching pickaxe could mine: an iron chisel cuts what an
 * iron pickaxe can harvest.
 */
public class ChiselItem extends Item {
    private final Supplier<Item> pickaxeTier;
    private ItemStack probe;

    public ChiselItem(Properties properties, Supplier<Item> pickaxeTier) {
        super(properties);
        this.pickaxeTier = pickaxeTier;
    }

    public static ChiselSettings settings(ItemStack stack) {
        return stack.getOrDefault(BitsmithComponents.CHISEL_SETTINGS.get(), ChiselSettings.DEFAULT);
    }

    /** Whether this chisel can cut the given material. */
    public boolean canCut(int material) {
        BlockState state = Materials.state(material);
        if (!state.requiresCorrectToolForDrops()) {
            return true;
        }
        if (probe == null) {
            probe = new ItemStack(pickaxeTier.get());
        }
        return probe.isCorrectToolForDrops(state);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(settings(stack).describe().copy().withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("bitsmith.chisel.keys").withStyle(ChatFormatting.DARK_GRAY));
    }
}
