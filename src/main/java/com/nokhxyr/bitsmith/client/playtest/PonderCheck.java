/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client.playtest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * Create's Ponder screen, driven like a player would (hold W over an item, scroll to the next
 * scene). Reached by reflection: Bitsmith does not depend on Create. Only used with Create loaded.
 */
final class PonderCheck {
    private PonderCheck() {
    }

    /** Opens Ponder on {@code stack}, as holding the Ponder key over it does. */
    static void open(ItemStack stack) {
        try {
            Class<?> ui = Class.forName("net.createmod.ponder.foundation.ui.PonderUI");
            Object screen = ui.getMethod("of", ItemStack.class).invoke(null, stack);
            Class.forName("net.createmod.catnip.gui.ScreenOpener").getMethod("open", Screen.class).invoke(null, screen);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot open Ponder", e);
        }
    }

    /** Whether the Ponder screen is open. */
    static boolean isOpen() {
        Screen screen = Minecraft.getInstance().screen;
        return screen != null && screen.getClass().getName().equals("net.createmod.ponder.foundation.ui.PonderUI");
    }

    private static Object scene() throws ReflectiveOperationException {
        Screen screen = Minecraft.getInstance().screen;
        return screen.getClass().getMethod("getActiveScene").invoke(screen);
    }

    /** The active scene's id, or what went wrong. */
    static String sceneId() {
        if (!isOpen()) {
            return "Ponder is not open";
        }
        try {
            Object scene = scene();
            return String.valueOf(scene.getClass().getMethod("getId").invoke(scene));
        } catch (ReflectiveOperationException e) {
            return "error " + e;
        }
    }

    /** The active scene's title, as the player reads it. */
    static String title() {
        try {
            Object scene = scene();
            return String.valueOf(scene.getClass().getMethod("getTitle").invoke(scene));
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "error " + e;
        }
    }

    /** Ticks played in the active scene, -1 when Ponder is closed. */
    static int time() {
        if (!isOpen()) {
            return -1;
        }
        try {
            Object scene = scene();
            return (int) scene.getClass().getMethod("getCurrentTime").invoke(scene);
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    /** The block entity at a scene position: its saved state and whether a renderer draws it. */
    static String blockEntity(int x, int y, int z) {
        try {
            Object scene = scene();
            Object level = scene.getClass().getMethod("getWorld").invoke(scene);
            var entity = ((net.minecraft.world.level.Level) level).getBlockEntity(new net.minecraft.core.BlockPos(x, y, z));
            if (entity == null) {
                return "no block entity";
            }
            var tag = entity.saveWithoutMetadata(Minecraft.getInstance().level.registryAccess());
            var renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(entity);
            return entity.getClass().getSimpleName() + " speed " + tag.getFloat("Speed") + ", running " + tag.getBoolean("running")
                    + ", progress " + tag.getFloat("progress") + ", renderer " + (renderer == null ? "none" : renderer.getClass().getSimpleName());
        } catch (ReflectiveOperationException | RuntimeException e) {
            return "error " + e;
        }
    }

    /** Goes to the next scene, as scrolling the mouse wheel does. */
    static void next() {
        try {
            Screen screen = Minecraft.getInstance().screen;
            Method scroll = Class.forName("net.createmod.ponder.foundation.ui.PonderUI").getDeclaredMethod("scroll", boolean.class);
            scroll.setAccessible(true);
            scroll.invoke(screen, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot change Ponder scene", e);
        }
    }
}
