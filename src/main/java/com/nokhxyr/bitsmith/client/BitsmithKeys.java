/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** Key bindings, all rebindable in the controls menu. */
public final class BitsmithKeys {
    private static final String CATEGORY = "key.categories.bitsmith";

    /** Held to show the radial menu of the tool in hand. */
    public static final KeyMapping RADIAL = new KeyMapping("key.bitsmith.radial", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);

    /** Cycles the chisel mode, or the bag's selected material. */
    public static final KeyMapping MODE = new KeyMapping("key.bitsmith.mode", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    /** Cycles the chisel size. */
    public static final KeyMapping SIZE = new KeyMapping("key.bitsmith.size", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);

    public static final KeyMapping UNDO = new KeyMapping("key.bitsmith.undo", KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    public static final KeyMapping REDO = new KeyMapping("key.bitsmith.redo", KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);

    private BitsmithKeys() {
    }
}
