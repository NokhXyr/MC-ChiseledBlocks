/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.item;

import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.edit.BitPlacement;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A bit bag that never runs out, for building: it places any of its materials without limit,
 * pays for patterns, and swallows the bits chiseled while it is in the inventory. It has no
 * recipe; only the creative tab and commands give it.
 *
 * <p>Its list holds the materials to choose from, not amounts: sneak and right click a block or a
 * bit to add that material and select it.
 */
public class CreativeBitBagItem extends BitBagItem {
    /** Shown and used as the amount of every material: more than any carving can ask for. */
    public static final int INFINITE = 1 << 30;

    public CreativeBitBagItem(Properties properties) {
        super(properties.rarity(Rarity.EPIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    /** Adds a material to the list (dropping the oldest when full) and selects it. */
    public static void pick(ItemStack bag, BlockState material) {
        BagContents contents = contents(bag);
        List<BagContents.Entry> entries = new java.util.ArrayList<>(contents.entries());
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).material() == material) {
                index = i;
            }
        }
        if (index < 0) {
            if (entries.size() >= BagContents.MAX_TYPES) {
                entries.remove(0);
            }
            entries.add(new BagContents.Entry(material, 1));
            index = entries.size() - 1;
        }
        setContents(bag, new BagContents(entries, index));
    }

    /** Remembers a material, without changing the selection unless the list was empty. */
    public static void remember(ItemStack bag, BlockState material) {
        BagContents contents = contents(bag);
        if (contents.count(material) > 0 || contents.entries().size() >= BagContents.MAX_TYPES) {
            return;
        }
        setContents(bag, contents.add(material, 1));
    }

    @Override
    public Component describeSelection(BagContents.Entry entry) {
        return Component.translatable("bitsmith.creative_bag.selected", entry.material().getBlock().getName());
    }

    @Override
    public Component describeEntry(BagContents.Entry entry) {
        return entry.material().getBlock().getName();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack bag = context.getItemInHand();
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            BlockState material = materialAt(context);
            if (material == null) {
                return InteractionResult.FAIL;
            }
            if (!context.getLevel().isClientSide) {
                pick(bag, material);
                player.displayClientMessage(Component.translatable("bitsmith.creative_bag.picked",
                        material.getBlock().getName()), true);
            }
            return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
        }
        BagContents.Entry entry = contents(bag).selectedEntry();
        if (entry == null) {
            if (player != null && !context.getLevel().isClientSide) {
                player.displayClientMessage(Component.translatable("bitsmith.creative_bag.empty"), true);
            }
            return InteractionResult.FAIL;
        }
        return BitPlacement.place(context, entry.material(), INFINITE, amount -> { });
    }

    /** The material under the cursor: the clicked bit of a carved block, or the clicked block. */
    private static @Nullable BlockState materialAt(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.getBlockEntity(pos) instanceof CarvedBlockEntity carved) {
            // Just inside the clicked face.
            Vec3 inside = context.getClickLocation().subtract(Vec3.atLowerCornerOf(context.getClickedFace().getNormal()).scale(1.0 / 64));
            int x = Mth.clamp(Mth.floor((inside.x - pos.getX()) * BitGrid.SIZE), 0, BitGrid.SIZE - 1);
            int y = Mth.clamp(Mth.floor((inside.y - pos.getY()) * BitGrid.SIZE), 0, BitGrid.SIZE - 1);
            int z = Mth.clamp(Mth.floor((inside.z - pos.getZ()) * BitGrid.SIZE), 0, BitGrid.SIZE - 1);
            int material = carved.getGrid().get(BitGrid.index(x, y, z));
            return material == BitGrid.AIR ? null : Materials.state(material);
        }
        BlockState state = level.getBlockState(pos);
        return Materials.isValidMaterial(state) ? state : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack bag = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(bag);
        }
        BagContents.Entry entry = contents(bag).selectedEntry();
        if (entry == null) {
            return InteractionResultHolder.fail(bag);
        }
        if (!level.isClientSide) {
            ItemStack bits = BitItem.of(entry.material(), 64);
            if (!player.getInventory().add(bits)) {
                player.drop(bits, false);
            }
        }
        return InteractionResultHolder.sidedSuccess(bag, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("bitsmith.creative_bag.infinite").withStyle(ChatFormatting.LIGHT_PURPLE));
        BagContents contents = contents(stack);
        BagContents.Entry selected = contents.selectedEntry();
        int shown = Math.min(TOOLTIP_LINES, contents.entries().size());
        for (int i = 0; i < shown; i++) {
            BagContents.Entry entry = contents.entries().get(i);
            tooltip.add(entry.material().getBlock().getName().copy()
                    .withStyle(entry == selected ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
        }
        if (contents.entries().size() > shown) {
            tooltip.add(Component.translatable("bitsmith.bag.more", contents.entries().size() - shown).withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable(contents.isEmpty() ? "bitsmith.creative_bag.empty" : "bitsmith.creative_bag.keys")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
