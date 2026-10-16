/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.core.edit.PlacementMode;
import com.nokhxyr.chiseledblocks.core.edit.PlacementShape;
import com.nokhxyr.chiseledblocks.core.voxel.PixelGrid;
import com.nokhxyr.chiseledblocks.item.PixelBagItem;
import com.nokhxyr.chiseledblocks.item.PixelItem;
import com.nokhxyr.chiseledblocks.network.PlaceShapePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * The placement modes on the client, with bits or a bit bag in hand. Single placement stays the
 * game's own right click; the other modes take the right click over: drawing sends a bit each tick
 * while the key is held, on the layer of the first one; line, plane and box take a start on the
 * first click and place the shape on the second, with an outline preview in between.
 */
@EventBusSubscriber(modid = ChiseledBlocks.MOD_ID, value = Dist.CLIENT)
public final class PlacementControl {
    private static final double INSET = 1.0 / 64.0;
    private static final float[] PREVIEW = {0.35F, 0.95F, 0.35F};

    private static PlacementMode mode = PlacementMode.SINGLE;
    private static int @Nullable [] start;
    private static int startAxis;
    /** Set when a press was handled, until the key is released, so holding does not repeat it. */
    private static boolean pressHandled;
    private static boolean drawing;
    private static int drawAxis;
    private static int drawLayer;
    private static int @Nullable [] lastDrawn;
    private static ItemStack lastHeld = ItemStack.EMPTY;

    private PlacementControl() {
    }

    public static PlacementMode mode() {
        return mode;
    }

    public static void set(PlacementMode next) {
        mode = next;
        start = null;
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable("chiseledblocks.place.mode", name(next)), true);
        }
    }

    public static Component name(PlacementMode mode) {
        return Component.translatable("chiseledblocks.place." + mode.name().toLowerCase(Locale.ROOT));
    }

    /** The start of the shape being made, for the playtest. */
    public static int @Nullable [] start() {
        return start;
    }

    static boolean holdsPlacer(Player player) {
        ItemStack held = player.getMainHandItem();
        return held.getItem() instanceof PixelItem || held.getItem() instanceof PixelBagItem;
    }

    /** The bit just in front of the clicked face, in world bit coordinates. */
    static int[] bitInFront(BlockHitResult hit) {
        Vec3 point = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(INSET));
        return new int[]{Mth.floor(point.x * PixelGrid.SIZE), Mth.floor(point.y * PixelGrid.SIZE), Mth.floor(point.z * PixelGrid.SIZE)};
    }

    private static void send(PlacementMode sent, int[] from, int[] to, int axis) {
        PacketDistributor.sendToServer(new PlaceShapePayload(sent.ordinal(), from[0], from[1], from[2], to[0], to[1], to[2], axis));
    }

    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (!event.isUseItem() || player == null || mode == PlacementMode.SINGLE || !holdsPlacer(player)) {
            return;
        }
        if (player.isShiftKeyDown() && player.getMainHandItem().getItem() instanceof PixelBagItem) {
            // Sneaking keeps the bag's own uses: a stack out, or picking a material.
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
        if (pressHandled) {
            return;
        }
        pressHandled = true;
        BlockHitResult hit = minecraft.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
                ? block : null;
        if (mode == PlacementMode.DRAW) {
            if (hit != null) {
                int[] bit = bitInFront(hit);
                drawing = true;
                drawAxis = hit.getDirection().getAxis().ordinal();
                drawLayer = bit[drawAxis];
                lastDrawn = bit;
                send(PlacementMode.DRAW, bit, bit, drawAxis);
                player.swing(InteractionHand.MAIN_HAND);
            }
            return;
        }
        if (hit == null) {
            // Right click in the air drops the start.
            if (start != null) {
                start = null;
                player.displayClientMessage(Component.translatable("chiseledblocks.place.cleared"), true);
            }
            return;
        }
        int[] bit = bitInFront(hit);
        if (start == null) {
            start = bit;
            startAxis = hit.getDirection().getAxis().ordinal();
            player.displayClientMessage(Component.translatable("chiseledblocks.place.started", name(mode)), true);
            return;
        }
        if (!PlacementShape.fits(mode, start, bit, startAxis)) {
            player.displayClientMessage(Component.translatable("chiseledblocks.place.too_big", PlacementShape.MAX_PIXELS), true);
            return;
        }
        send(mode, start, bit, startAxis);
        start = null;
        player.swing(InteractionHand.MAIN_HAND);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!ItemStack.isSameItem(held, lastHeld)) {
            // Another tool: an unfinished shape is dropped.
            start = null;
            lastHeld = held.copy();
        }
        if (!minecraft.options.keyUse.isDown()) {
            pressHandled = false;
            drawing = false;
            return;
        }
        if (drawing && mode == PlacementMode.DRAW && holdsPlacer(player) && minecraft.screen == null) {
            int[] bit = onDrawLayer(player);
            if (bit != null && !java.util.Arrays.equals(bit, lastDrawn)) {
                lastDrawn = bit;
                send(PlacementMode.DRAW, bit, bit, drawAxis);
            }
        }
    }

    /** Where the view ray crosses the layer being drawn on, within reach. */
    private static int @Nullable [] onDrawLayer(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 view = player.getViewVector(1.0F);
        double[] origin = {eye.x, eye.y, eye.z};
        double[] direction = {view.x, view.y, view.z};
        if (Math.abs(direction[drawAxis]) < 1e-6) {
            return null;
        }
        double plane = (drawLayer + 0.5) / PixelGrid.SIZE;
        double distance = (plane - origin[drawAxis]) / direction[drawAxis];
        if (distance <= 0 || distance > player.blockInteractionRange()) {
            return null;
        }
        int[] bit = new int[3];
        for (int i = 0; i < 3; i++) {
            bit[i] = Mth.floor((origin[i] + direction[i] * distance) * PixelGrid.SIZE);
        }
        bit[drawAxis] = drawLayer;
        return bit;
    }

    /** Outlines the shape from the start to the bit in front of the pointed face. */
    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !mode.twoPoints() || !holdsPlacer(player)) {
            return;
        }
        int[] end = bitInFront(event.getTarget());
        int[] from = start != null ? start : end;
        int[] min = new int[3];
        int[] max = new int[3];
        for (int i = 0; i < 3; i++) {
            min[i] = Math.min(from[i], end[i]);
            max[i] = Math.max(from[i], end[i]) + 1;
        }
        if (start != null && mode == PlacementMode.PLANE) {
            min[startAxis] = start[startAxis];
            max[startAxis] = start[startAxis] + 1;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        boolean fits = start == null || PlacementShape.fits(mode, start, end, startAxis);
        float red = fits ? PREVIEW[0] : 0.95F;
        float green = fits ? PREVIEW[1] : 0.3F;
        if (start != null && mode == PlacementMode.LINE && fits) {
            // Each bit of the line, so its path shows.
            PlacementShape.forEach(PlacementMode.LINE, start, end, startAxis, (x, y, z) ->
                    LevelRenderer.renderLineBox(pose, lines, x / 16.0, y / 16.0, z / 16.0, (x + 1) / 16.0, (y + 1) / 16.0,
                            (z + 1) / 16.0, red, green, PREVIEW[2], 0.9F));
        } else {
            LevelRenderer.renderLineBox(pose, lines, min[0] / 16.0, min[1] / 16.0, min[2] / 16.0,
                    max[0] / 16.0, max[1] / 16.0, max[2] / 16.0, red, green, PREVIEW[2], 0.9F);
        }
        pose.popPose();
        event.setCanceled(true);
    }
}
