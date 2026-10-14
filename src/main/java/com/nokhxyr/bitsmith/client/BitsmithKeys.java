/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Key bindings, all rebindable in the controls menu. */
public final class BitsmithKeys {
    private static final String CATEGORY = "key.categories.bitsmith";

    /** Cycles the chisel mode, or the bag's selected material. */
    public static final KeyMapping MODE = new KeyMapping("key.bitsmith.mode", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    /** Cycles the chisel size. */
    public static final KeyMapping SIZE = new KeyMapping("key.bitsmith.size", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);

    private BitsmithKeys() {
    }
}
