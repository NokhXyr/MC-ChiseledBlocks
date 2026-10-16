/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client.admin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;

/** Colours and flat drawing primitives of the admin screen. */
public final class UiKit {
    public static final int SCRIM = 0xA0080A0D;
    public static final int PANEL = 0xF5151920;
    public static final int HEADER = 0xFF1B2028;
    public static final int SURFACE = 0xFF222832;
    public static final int SURFACE_HOVER = 0xFF2B323D;
    public static final int BORDER = 0xFF2F3742;
    public static final int ROW_ALT = 0x08FFFFFF;
    public static final int ROW_HOVER = 0x14FFFFFF;
    public static final int ACCENT = 0xFF4FC3F7;
    public static final int ACCENT_HOVER = 0xFF81D4FA;
    public static final int ON_ACCENT = 0xFF0B1A22;
    public static final int TEXT = 0xFFE8EAED;
    public static final int MUTED = 0xFF8B95A1;
    public static final int FAINT = 0xFF5C6672;
    public static final int GOOD = 0xFF66BB6A;
    public static final int WARN = 0xFFFFCA28;
    public static final int BAD = 0xFFEF5350;
    public static final int BAD_HOVER = 0xFFF47C7A;

    private UiKit() {
    }

    /** A rectangle with its corner pixels cut, which reads as slightly rounded. */
    public static void box(GuiGraphics g, int x, int y, int w, int h, int colour) {
        if (w <= 2 || h <= 2) {
            g.fill(x, y, x + w, y + h, colour);
            return;
        }
        g.fill(x + 1, y, x + w - 1, y + h, colour);
        g.fill(x, y + 1, x + 1, y + h - 1, colour);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    public static void outline(GuiGraphics g, int x, int y, int w, int h, int colour) {
        g.fill(x + 1, y, x + w - 1, y + 1, colour);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, colour);
        g.fill(x, y + 1, x + 1, y + h - 1, colour);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    public static void text(GuiGraphics g, Font font, Component text, int x, int y, int colour) {
        g.drawString(font, text, x, y, colour, false);
    }

    public static void text(GuiGraphics g, Font font, String text, int x, int y, int colour) {
        g.drawString(font, text, x, y, colour, false);
    }

    /** Text cut with an ellipsis to fit {@code width}. */
    public static void textFit(GuiGraphics g, Font font, String text, int x, int y, int width, int colour) {
        if (font.width(text) > width) {
            text = font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "...";
        }
        g.drawString(font, text, x, y, colour, false);
    }

    public static void textFit(GuiGraphics g, Font font, Component text, int x, int y, int width, int colour) {
        textFit(g, font, text.getString(), x, y, width, colour);
    }

    public static void centred(GuiGraphics g, Font font, Component text, int centreX, int y, int colour) {
        FormattedCharSequence sequence = text.getVisualOrderText();
        g.drawString(font, sequence, centreX - font.width(sequence) / 2, y, colour, false);
    }

    public static void rightAligned(GuiGraphics g, Font font, String text, int right, int y, int colour) {
        g.drawString(font, text, right - font.width(text), y, colour, false);
    }

    public static int mix(int from, int to, float amount) {
        float t = Math.clamp(amount, 0f, 1f);
        return FastColor.ARGB32.lerp(t, from, to);
    }

    /** Colour of a carved block count on the map: cool for a few, warm for many. */
    public static int heat(int count) {
        if (count <= 0) {
            return 0;
        }
        float t = (float) (Math.log(count) / Math.log(256));
        return t < 0.5f ? mix(0xFF26A69A, 0xFFFFCA28, t * 2) : mix(0xFFFFCA28, 0xFFEF5350, (t - 0.5f) * 2);
    }

    /** Colour of a cost in microseconds per tick: green, then yellow, then red. */
    public static int cost(double microsPerTick) {
        return microsPerTick < 50 ? GOOD : microsPerTick < 500 ? WARN : BAD;
    }
}
