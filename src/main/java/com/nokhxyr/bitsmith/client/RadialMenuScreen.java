/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nokhxyr.bitsmith.core.edit.ChiselMode;
import com.nokhxyr.bitsmith.core.edit.ChiselShape;
import com.nokhxyr.bitsmith.item.BagContents;
import com.nokhxyr.bitsmith.item.BitBagItem;
import com.nokhxyr.bitsmith.item.BitItem;
import com.nokhxyr.bitsmith.item.ChiselItem;
import com.nokhxyr.bitsmith.item.ChiselSettings;
import com.nokhxyr.bitsmith.item.PatternItem;
import com.nokhxyr.bitsmith.item.PatternMode;
import com.nokhxyr.bitsmith.network.ToolOptionPayload;
import com.nokhxyr.bitsmith.network.UndoPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Radial menu shown while the menu key is held with a Bitsmith tool in hand. Point at an option
 * and release the key (or click) to pick it. The centre holds undo (left half) and redo (right
 * half). Options are applied by the server, like the V and B keys.
 */
public final class RadialMenuScreen extends Screen {
    private static final float CENTER_RADIUS = 24;
    private static final float GAP = 0.03F;
    private static final int COLOR_IDLE = 0x90202020;
    private static final int COLOR_HOVER = 0xD0FFFFFF;
    private static final int COLOR_SELECTED = 0xC0D08A28;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_ON_HOVER = 0xFF202020;

    /** One choice in a ring. */
    private record Slot(Component label, @Nullable ItemStack icon, boolean selected, Runnable action) {
    }

    private record Ring(float inner, float outer, List<Slot> slots) {
    }

    private final List<Ring> rings = new ArrayList<>();
    private @Nullable Slot hovered;
    private int hoveredCenter;

    private RadialMenuScreen() {
        super(Component.translatable("bitsmith.radial.title"));
    }

    /** Builds the menu for the tool in hand, or returns null when it has no options. */
    public static @Nullable RadialMenuScreen forItem(ItemStack held) {
        RadialMenuScreen screen = new RadialMenuScreen();
        if (held.getItem() instanceof ChiselItem) {
            ChiselSettings settings = ChiselItem.settings(held);
            List<Slot> sizes = new ArrayList<>();
            for (int size : ChiselShape.SIZES) {
                sizes.add(new Slot(Component.literal(Integer.toString(size)), null, settings.size() == size,
                        () -> send(ToolOptionPayload.Option.CHISEL_SIZE, size)));
            }
            List<Slot> modes = new ArrayList<>();
            for (ChiselMode mode : ChiselMode.values()) {
                modes.add(new Slot(Component.translatable("bitsmith.mode." + mode.name().toLowerCase(Locale.ROOT)), null,
                        settings.mode() == mode, () -> send(ToolOptionPayload.Option.CHISEL_MODE, mode.ordinal())));
            }
            screen.rings.add(new Ring(30, 58, sizes));
            screen.rings.add(new Ring(62, 112, modes));
            return screen;
        }
        if (held.getItem() instanceof PatternItem) {
            PatternMode current = PatternItem.mode(held);
            List<Slot> modes = new ArrayList<>();
            for (PatternMode mode : PatternMode.values()) {
                modes.add(new Slot(mode.displayName(), null, mode == current,
                        () -> send(ToolOptionPayload.Option.PATTERN_MODE, mode.ordinal())));
            }
            screen.rings.add(new Ring(36, 96, modes));
            return screen;
        }
        if (held.getItem() instanceof BitBagItem) {
            BagContents contents = BitBagItem.contents(held);
            List<Slot> materials = new ArrayList<>();
            for (int i = 0; i < contents.entries().size(); i++) {
                BagContents.Entry entry = contents.entries().get(i);
                int index = i;
                materials.add(new Slot(Component.translatable("bitsmith.bag.entry", entry.material().getBlock().getName(), entry.count()),
                        BitItem.of(entry.material(), 1), i == contents.selected(),
                        () -> send(ToolOptionPayload.Option.BAG_MATERIAL, index)));
            }
            if (!materials.isEmpty()) {
                screen.rings.add(new Ring(36, 104, materials));
            }
            return screen;
        }
        return null;
    }

    private static void send(ToolOptionPayload.Option option, int value) {
        PacketDistributor.sendToServer(new ToolOptionPayload(option, value));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No blur: the world stays visible behind the menu.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float cx = width / 2.0F;
        float cy = height / 2.0F;
        hoverAt(mouseX, mouseY);

        Matrix4f matrix = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());

        sector(buffer, matrix, cx, cy, 0, CENTER_RADIUS, Mth.HALF_PI + GAP, Mth.PI * 1.5F - GAP,
                hoveredCenter < 0 ? COLOR_HOVER : COLOR_IDLE);
        sector(buffer, matrix, cx, cy, 0, CENTER_RADIUS, -Mth.HALF_PI + GAP, Mth.HALF_PI - GAP,
                hoveredCenter > 0 ? COLOR_HOVER : COLOR_IDLE);

        for (Ring ring : rings) {
            float step = Mth.TWO_PI / ring.slots().size();
            for (int i = 0; i < ring.slots().size(); i++) {
                Slot slot = ring.slots().get(i);
                float start = -Mth.HALF_PI + i * step;
                int color = slot == hovered ? COLOR_HOVER : slot.selected() ? COLOR_SELECTED : COLOR_IDLE;
                sector(buffer, matrix, cx, cy, ring.inner(), ring.outer(), start + GAP, start + step - GAP, color);
            }
        }
        graphics.flush();

        graphics.drawCenteredString(font, "<", (int) (cx - CENTER_RADIUS / 2), (int) cy - 4, hoveredCenter < 0 ? TEXT_ON_HOVER : TEXT);
        graphics.drawCenteredString(font, ">", (int) (cx + CENTER_RADIUS / 2), (int) cy - 4, hoveredCenter > 0 ? TEXT_ON_HOVER : TEXT);
        for (Ring ring : rings) {
            float step = Mth.TWO_PI / ring.slots().size();
            float middle = (ring.inner() + ring.outer()) / 2;
            for (int i = 0; i < ring.slots().size(); i++) {
                Slot slot = ring.slots().get(i);
                float mid = -Mth.HALF_PI + (i + 0.5F) * step;
                int x = (int) (cx + Mth.cos(mid) * middle);
                int y = (int) (cy + Mth.sin(mid) * middle);
                if (slot.icon() != null) {
                    graphics.renderItem(slot.icon(), x - 8, y - 8);
                } else {
                    graphics.drawCenteredString(font, slot.label(), x, y - 4, slot == hovered ? TEXT_ON_HOVER : TEXT);
                }
            }
        }

        Component caption = hoveredCenter < 0 ? Component.translatable("bitsmith.radial.undo")
                : hoveredCenter > 0 ? Component.translatable("bitsmith.radial.redo")
                : hovered != null ? hovered.label() : null;
        if (caption != null) {
            graphics.drawCenteredString(font, caption, (int) cx, (int) (cy + outerRadius() + 8), TEXT);
        }
    }

    /**
     * Works out the option under a point in GUI units. Called when drawing and again when the
     * choice is made, so a mouse move and a release within the same frame still pick the option
     * actually under the cursor.
     */
    private void hoverAt(double mouseX, double mouseY) {
        float dx = (float) (mouseX - width / 2.0);
        float dy = (float) (mouseY - height / 2.0);
        updateHover(Mth.sqrt(dx * dx + dy * dy), angleOf(dx, dy), dx);
    }

    /** The cursor position right now, in GUI units. */
    private void hoverAtCursor() {
        if (minecraft == null) {
            return;
        }
        double x = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
        double y = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
        hoverAt(x, y);
    }

    private float outerRadius() {
        float outer = CENTER_RADIUS;
        for (Ring ring : rings) {
            outer = Math.max(outer, ring.outer());
        }
        return outer;
    }

    /** Angle in screen space, 0 to the right, growing clockwise, in [-PI/2, 3PI/2). */
    private static float angleOf(float dx, float dy) {
        float angle = (float) Math.atan2(dy, dx);
        return angle < -Mth.HALF_PI ? angle + Mth.TWO_PI : angle;
    }

    private void updateHover(float distance, float angle, float dx) {
        hovered = null;
        hoveredCenter = 0;
        if (distance <= CENTER_RADIUS) {
            hoveredCenter = dx < 0 ? -1 : 1;
            return;
        }
        // Each ring owns the space up to halfway to the next one; the last ring owns everything
        // beyond it, so a quick flick of the mouse past the menu still picks something.
        for (int r = 0; r < rings.size(); r++) {
            Ring ring = rings.get(r);
            float upper = r + 1 < rings.size() ? (ring.outer() + rings.get(r + 1).inner()) / 2 : Float.MAX_VALUE;
            if (distance < upper) {
                float step = Mth.TWO_PI / ring.slots().size();
                int index = Mth.clamp((int) ((angle + Mth.HALF_PI) / step), 0, ring.slots().size() - 1);
                hovered = ring.slots().get(index);
                return;
            }
        }
    }

    /**
     * Fills an annular sector with small quads, in the same vertex order as
     * {@link GuiGraphics#fill}, so the GUI render type does not cull them.
     */
    private static void sector(VertexConsumer buffer, Matrix4f matrix, float cx, float cy,
                               float inner, float outer, float from, float to, int color) {
        int steps = Math.max(2, (int) ((to - from) / 0.08F));
        float step = (to - from) / steps;
        for (int i = 0; i < steps; i++) {
            float a = from + i * step;
            float b = a + step;
            float cosA = Mth.cos(a);
            float sinA = Mth.sin(a);
            float cosB = Mth.cos(b);
            float sinB = Mth.sin(b);
            buffer.addVertex(matrix, cx + cosA * inner, cy + sinA * inner, 0).setColor(color);
            buffer.addVertex(matrix, cx + cosB * inner, cy + sinB * inner, 0).setColor(color);
            buffer.addVertex(matrix, cx + cosB * outer, cy + sinB * outer, 0).setColor(color);
            buffer.addVertex(matrix, cx + cosA * outer, cy + sinA * outer, 0).setColor(color);
        }
    }

    /** Runs the hovered choice. Returns whether there was one. */
    private boolean activate() {
        if (hoveredCenter != 0) {
            PacketDistributor.sendToServer(new UndoPayload(hoveredCenter > 0));
            return true;
        }
        if (hovered != null) {
            hovered.action().run();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        hoverAt(mouseX, mouseY);
        if (button == 0 && activate()) {
            if (hoveredCenter == 0) {
                onClose();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (BitsmithKeys.RADIAL.matches(keyCode, scanCode)) {
            hoverAtCursor();
            if (hoveredCenter == 0) {
                activate();
            }
            onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
}
