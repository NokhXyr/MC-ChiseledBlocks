/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.item;

import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.edit.ChiselShape;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.edit.BlockSnapshot;
import com.nokhxyr.bitsmith.edit.EditHistory;
import com.nokhxyr.bitsmith.registry.BitsmithComponents;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Right click cuts a slice off the clicked side of a block: the outer layer of the chosen
 * thickness, measured from the block boundary. The slice is handed over as a carved block item
 * with its shape; what is left stays in place. B or the tool menu sets the thickness.
 */
public class SawItem extends Item {

    public SawItem(Properties properties) {
        super(properties);
    }

    public static int thickness(ItemStack stack) {
        return ChiselShape.validSize(stack.getOrDefault(BitsmithComponents.SAW_THICKNESS.get(), 1));
    }

    /** Whether voxel {@code index} lies in the slice cut through {@code face} with this thickness. */
    public static boolean inSlice(int index, Direction face, int thickness) {
        int[] p = {index & 15, index >> 8, (index >> 4) & 15};
        int c = p[face.getAxis().ordinal()];
        return face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? c >= BitGrid.SIZE - thickness : c < thickness;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (context.getLevel().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.FAIL;
        }
        BlockPos pos = context.getClickedPos();
        if (!player.mayBuild() || !level.mayInteract(player, pos)) {
            return InteractionResult.FAIL;
        }
        BitGrid base = baseAt(level, pos);
        if (base == null) {
            return InteractionResult.FAIL;
        }

        Direction face = context.getClickedFace();
        int thickness = thickness(context.getItemInHand());
        ChiselItem cutter = BitsmithRegistries.IRON_CHISEL.get();
        BitGridBuilder rest = base.toBuilder();
        BitGridBuilder slice = new BitGridBuilder();
        int sliced = 0;
        int soundMaterial = BitGrid.AIR;
        for (int i = 0; i < BitGrid.VOLUME; i++) {
            int material = base.get(i);
            if (material != BitGrid.AIR && inSlice(i, face, thickness) && cutter.canCut(material)) {
                slice.set(i, material);
                rest.set(i, BitGrid.AIR);
                sliced++;
                soundMaterial = material;
            }
        }
        if (sliced == 0) {
            player.displayClientMessage(Component.translatable("bitsmith.saw.nothing"), true);
            return InteractionResult.FAIL;
        }

        BlockSnapshot before = BlockSnapshot.current(level, pos);
        BlockSnapshot after = BlockSnapshot.of(rest.build());
        after.apply(level, pos);

        ItemStack piece = new ItemStack(BitsmithRegistries.CARVED_BLOCK_ITEM.get());
        piece.set(BitsmithComponents.GRID.get(), CarvedBlockEntity.INTERNER.intern(slice.build()));
        if (!player.getInventory().add(piece)) {
            player.drop(piece, false);
        }

        boolean survival = !player.getAbilities().instabuild;
        if (survival) {
            // The slice left as an item: undoing would duplicate it, so survival cuts are final.
            context.getItemInHand().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        } else {
            EditHistory.record(player, pos, before, after, new Int2IntOpenHashMap());
        }
        SoundType sound = Materials.state(soundMaterial).getSoundType();
        level.playSound(null, pos, sound.getBreakSound(), SoundSource.BLOCKS, 0.8F, sound.getPitch() * 0.8F);
        return InteractionResult.CONSUME;
    }

    private static @Nullable BitGrid baseAt(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CarvedBlockEntity carved) {
            return carved.effectiveGrid();
        }
        BlockState state = level.getBlockState(pos);
        return Materials.isCarvable(state, level, pos) ? BitGrid.filled(Materials.id(state)) : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("bitsmith.saw.thickness", thickness(stack)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("bitsmith.saw.help").withStyle(ChatFormatting.DARK_GRAY));
    }
}
