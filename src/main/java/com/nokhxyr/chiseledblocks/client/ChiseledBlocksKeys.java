/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

/** Key bindings, all rebindable in the controls menu. */
public final class ChiseledBlocksKeys {
    private static final String CATEGORY = "key.categories.chiseledblocks";

    /** Held to show the radial menu of the tool in hand. */
    public static final KeyMapping RADIAL = new KeyMapping("key.chiseledblocks.radial", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);

    /** Cycles the chisel mode, or the bag's selected material. */
    public static final KeyMapping MODE = new KeyMapping("key.chiseledblocks.mode", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_V, CATEGORY);

    /** Cycles the mirror setting. */
    public static final KeyMapping SYMMETRY = new KeyMapping("key.chiseledblocks.symmetry", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORY);

    /** Cycles the chisel size. */
    public static final KeyMapping SIZE = new KeyMapping("key.chiseledblocks.size", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_B, CATEGORY);

    public static final KeyMapping UNDO = new KeyMapping("key.chiseledblocks.undo", KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Z, CATEGORY);

    public static final KeyMapping REDO = new KeyMapping("key.chiseledblocks.redo", KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, CATEGORY);

    /** Opens the admin screen (operators only). Unbound by default; {@code /chiseledblocks} also opens it. */
    public static final KeyMapping ADMIN = new KeyMapping("key.chiseledblocks.admin", KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN, CATEGORY);

    private ChiseledBlocksKeys() {
    }
}
