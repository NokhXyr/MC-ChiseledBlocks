/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.edit;

import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.block.Materials;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.BitGridBuilder;
import com.nokhxyr.bitsmith.core.voxel.Face;
import com.nokhxyr.bitsmith.item.ChiselItem;
import com.nokhxyr.bitsmith.item.ChiselSettings;
import com.nokhxyr.bitsmith.network.GridFullPayload;
import com.nokhxyr.bitsmith.registry.BitsmithRegistries;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.WeakHashMap;

/** Server side, authoritative handling of chisel strokes. */
public final class ChiselLogic {
    /** Most chisel strokes one player may perform in one tick. */
    private static final int MAX_ACTIONS_PER_TICK = 4;
    private static final Map<ServerPlayer, long[]> BUDGET = new WeakHashMap<>();

    private ChiselLogic() {
    }

    public static void chisel(ServerPlayer player, BlockPos pos, int voxel, Face face) {
        ServerLevel level = player.serverLevel();
        ItemStack tool = player.getMainHandItem();
        if (!(tool.getItem() instanceof ChiselItem chisel)
                || !level.isLoaded(pos)
                || !player.canInteractWithBlock(pos, 1.0)
                || !player.mayBuild()
                || !level.mayInteract(player, pos)
                || !takeBudget(player, level.getGameTime())) {
            resync(player, level, pos);
            return;
        }

        ChiselSettings settings = ChiselItem.settings(tool);
        Int2IntOpenHashMap removed = new Int2IntOpenHashMap();
        BlockState state = level.getBlockState(pos);
        int soundMaterial;

        if (level.getBlockEntity(pos) instanceof CarvedBlockEntity carved) {
            int[] count = {0};
            carved.edit(builder -> count[0] = ChiselOperation.apply(settings, voxel, face, builder::get, chisel::canCut,
                    (index, material) -> {
                        builder.set(index, BitGrid.AIR);
                        removed.addTo(material, 1);
                    }));
            if (count[0] == 0) {
                return;
            }
            soundMaterial = removed.keySet().iterator().nextInt();
        } else {
            int material = Materials.id(state);
            if (!Materials.isCarvable(state, level, pos) || !chisel.canCut(material)) {
                return;
            }
            // Convert the plain block. The client receives the new block and its data through
            // vanilla's block entity sync, already carved.
            BitGridBuilder builder = BitGrid.filled(material).toBuilder();
            ChiselOperation.apply(settings, voxel, face, builder::get, chisel::canCut, (index, m) -> {
                builder.set(index, BitGrid.AIR);
                removed.addTo(m, 1);
            });
            level.setBlock(pos, BitsmithRegistries.CARVED_BLOCK.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof CarvedBlockEntity carved) {
                carved.setGrid(builder.build());
            }
            soundMaterial = material;
        }

        if (!player.getAbilities().instabuild) {
            BitInventory.give(player, removed);
            tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        SoundType sound = Materials.state(soundMaterial).getSoundType();
        level.playSound(null, pos, sound.getHitSound(), SoundSource.BLOCKS, 0.5F, sound.getPitch() * 1.2F);
    }

    /**
     * Undoes a client prediction the server refused, by sending the true grid to that player.
     */
    private static void resync(ServerPlayer player, ServerLevel level, BlockPos pos) {
        if (player.connection.hasChannel(GridFullPayload.TYPE)
                && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof CarvedBlockEntity carved) {
            PacketDistributor.sendToPlayer(player, GridFullPayload.of(pos, carved.getGrid()));
        }
    }

    private static boolean takeBudget(ServerPlayer player, long gameTime) {
        long[] budget = BUDGET.computeIfAbsent(player, p -> new long[2]);
        if (budget[0] != gameTime) {
            budget[0] = gameTime;
            budget[1] = 0;
        }
        return budget[1]++ < MAX_ACTIONS_PER_TICK;
    }
}
