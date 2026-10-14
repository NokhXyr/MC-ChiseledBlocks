/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client;

import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.item.ChiselItem;
import com.nokhxyr.bitsmith.network.ChiselPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Turns the attack key into chisel actions while a chisel is held: one bit per click, and a
 * steady rate while the key is held down. Vanilla block breaking never starts.
 */
@EventBusSubscriber(modid = Bitsmith.MOD_ID, value = Dist.CLIENT)
public final class ChiselInput {
    /** Ticks between two actions while the attack key is held. */
    private static final int REPEAT_TICKS = 3;
    /** How far inside the hit face the targeted point is moved, in blocks. */
    private static final double INSET = 1.0 / 64.0;

    private static int cooldown;

    private ChiselInput() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (cooldown > 0) {
            cooldown--;
        }
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || !(player.getMainHandItem().getItem() instanceof ChiselItem)) {
            return;
        }
        if (!(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }

        event.setCanceled(true);
        event.setSwingHand(false);
        if (cooldown > 0) {
            return;
        }
        cooldown = REPEAT_TICKS;

        PacketDistributor.sendToServer(new ChiselPayload(hit.getBlockPos(), voxelAt(hit)));
        player.swing(InteractionHand.MAIN_HAND);
    }

    /** The voxel just behind the hit point, in the targeted block. */
    static int voxelAt(BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
        Vec3 local = hit.getLocation().subtract(normal.scale(INSET)).subtract(pos.getX(), pos.getY(), pos.getZ());
        int x = Mth.clamp(Mth.floor(local.x * BitGrid.SIZE), 0, BitGrid.SIZE - 1);
        int y = Mth.clamp(Mth.floor(local.y * BitGrid.SIZE), 0, BitGrid.SIZE - 1);
        int z = Mth.clamp(Mth.floor(local.z * BitGrid.SIZE), 0, BitGrid.SIZE - 1);
        return BitGrid.index(x, y, z);
    }
}
