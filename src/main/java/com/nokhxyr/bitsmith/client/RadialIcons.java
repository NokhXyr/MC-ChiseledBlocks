/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client;

import com.nokhxyr.bitsmith.core.edit.ChiselMode;
import com.nokhxyr.bitsmith.item.PatternMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;

/**
 * The 16 x 16 icons of the radial menu, drawn as flat pixel art with fills: no texture to ship,
 * crisp at any GUI scale, and tinted to show the current choice.
 */
final class RadialIcons {
    private RadialIcons() {
    }

    /** Draws itself in a 16 x 16 square whose top left corner is given. */
    interface Icon {
        void draw(GuiGraphics g, Font font, int x, int y, int colour);
    }

    static Icon item(ItemStack stack) {
        return (g, font, x, y, colour) -> g.renderItem(stack, x, y);
    }

    static Icon text(Component text) {
        return (g, font, x, y, colour) -> g.drawString(font, text, x + 8 - font.width(text) / 2, y + 4, colour, true);
    }

    static Icon mode(ChiselMode mode) {
        return switch (mode) {
            case BIT -> (g, font, x, y, c) -> block(g, x + 6, y + 6, 4, 4, c);
            case CUBE -> (g, font, x, y, c) -> block(g, x + 3, y + 3, 10, 10, c);
            case LINE -> (g, font, x, y, c) -> block(g, x + 1, y + 6, 14, 4, c);
            case PLANE -> (g, font, x, y, c) -> {
                // A flat slab seen from above at an angle.
                for (int row = 0; row < 6; row++) {
                    g.fill(x + 5 - row, y + 5 + row, x + 15 - row, y + 6 + row, row == 0 ? light(c) : c);
                }
                g.fill(x, y + 11, x + 10, y + 12, dark(c));
            };
            case SPHERE -> (g, font, x, y, c) -> {
                for (int py = 0; py < 14; py++) {
                    for (int px = 0; px < 14; px++) {
                        double dx = px - 6.5;
                        double dy = py - 6.5;
                        double d = dx * dx + dy * dy;
                        if (d <= 49) {
                            int shade = dx + dy < -5 ? light(c) : dx + dy > 6 ? dark(c) : c;
                            g.fill(x + 1 + px, y + 1 + py, x + 2 + px, y + 2 + py, shade);
                        }
                    }
                }
            };
        };
    }

    static Icon pattern(PatternMode mode) {
        if (mode == PatternMode.POSITIVE) {
            // Print: a plus, bits added.
            return (g, font, x, y, c) -> {
                g.fill(x + 6, y + 2, x + 10, y + 14, c);
                g.fill(x + 2, y + 6, x + 14, y + 10, c);
                g.fill(x + 6, y + 2, x + 10, y + 3, light(c));
            };
        }
        // Carve out: a block with a step cut away.
        return (g, font, x, y, c) -> {
            block(g, x + 2, y + 8, 12, 6, c);
            block(g, x + 2, y + 2, 6, 6, c);
            outline(g, x + 8, y + 2, 6, 6, dark(c));
        };
    }

    /** A block outline holding a slice of {@code bits} sixteenths at its bottom. */
    static Icon slice(int bits) {
        return (g, font, x, y, c) -> {
            outline(g, x + 1, y + 1, 14, 14, dark(c));
            int height = Math.max(1, Math.round(bits * 14 / 16f));
            block(g, x + 1, y + 15 - height, 14, height, c);
        };
    }

    static Icon undo(boolean redo) {
        return (g, font, x, y, c) -> {
            for (int i = 0; i < 4; i++) {
                // Arrow head.
                int ax = redo ? x + 13 - i : x + 2 + i;
                g.fill(ax, y + 7 - i, ax + 1, y + 9 + i, c);
            }
            // Shaft, then the hook going down.
            int shaftFrom = redo ? x + 3 : x + 5;
            g.fill(shaftFrom, y + 7, shaftFrom + 8, y + 9, c);
            int hook = redo ? x + 3 : x + 11;
            g.fill(hook, y + 7, hook + 2, y + 13, c);
            g.fill(redo ? hook + 2 : hook - 3, y + 11, redo ? hook + 5 : hook, y + 13, c);
        };
    }

    private static void block(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + h, c);
        g.fill(x, y, x + w, y + 1, light(c));
        g.fill(x + w - 1, y + 1, x + w, y + h, dark(c));
        g.fill(x, y + h - 1, x + w - 1, y + h, dark(c));
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y + 1, x + 1, y + h - 1, c);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, c);
    }

    private static int light(int c) {
        return FastColor.ARGB32.lerp(0.35F, c, 0xFFFFFFFF);
    }

    private static int dark(int c) {
        return FastColor.ARGB32.lerp(0.4F, c, 0xFF000000);
    }
}
