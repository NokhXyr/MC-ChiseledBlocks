/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nokhxyr.bitsmith.client.admin.UiKit;
import com.nokhxyr.bitsmith.core.edit.ChiselMode;
import com.nokhxyr.bitsmith.core.edit.ChiselShape;
import com.nokhxyr.bitsmith.core.edit.Symmetry;
import com.nokhxyr.bitsmith.edit.PlayerSymmetry;
import com.nokhxyr.bitsmith.item.BagContents;
import com.nokhxyr.bitsmith.item.BitBagItem;
import com.nokhxyr.bitsmith.item.BitItem;
import com.nokhxyr.bitsmith.item.ChiselItem;
import com.nokhxyr.bitsmith.item.ChiselSettings;
import com.nokhxyr.bitsmith.item.PatternItem;
import com.nokhxyr.bitsmith.item.PatternMode;
import com.nokhxyr.bitsmith.item.SawItem;
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
    /** Same palette as the admin panel. */
    private static final int SCRIM = 0x50080A0D;
    private static final int BACKDROP = 0xC0101318;
    private static final int IDLE = 0xF0222832;
    private static final int SELECTED = UiKit.mix(UiKit.ACCENT, UiKit.PANEL, 0.62F);

    /** One choice in a ring. */
    private record Slot(Component label, @Nullable ItemStack icon, boolean selected, Runnable action) {
    }

    private record Ring(float inner, float outer, List<Slot> slots) {
    }

    private final List<Ring> rings = new ArrayList<>();
    /** The tool the menu belongs to, shown above it when nothing is pointed at. */
    private Component toolName = Component.empty();
    /** Shrinks the whole menu when it would not fit the screen (large GUI scales). */
    private float menuScale = 1.0F;
    private @Nullable Slot hovered;
    private int hoveredCenter;

    private RadialMenuScreen() {
        super(Component.translatable("bitsmith.radial.title"));
    }

    /** Builds the menu for the tool in hand, or returns null when it has no options. */
    public static @Nullable RadialMenuScreen forItem(ItemStack held) {
        RadialMenuScreen screen = new RadialMenuScreen();
        screen.toolName = held.getHoverName();
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
            screen.rings.add(new Ring(116, 150, symmetrySlots()));
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
        if (held.getItem() instanceof BitBagItem bag) {
            BagContents contents = BitBagItem.contents(held);
            List<Slot> materials = new ArrayList<>();
            for (int i = 0; i < contents.entries().size(); i++) {
                BagContents.Entry entry = contents.entries().get(i);
                int index = i;
                materials.add(new Slot(bag.describeEntry(entry),
                        BitItem.of(entry.material(), 1), i == contents.selected(),
                        () -> send(ToolOptionPayload.Option.BAG_MATERIAL, index)));
            }
            if (!materials.isEmpty()) {
                screen.rings.add(new Ring(36, 104, materials));
            }
            screen.rings.add(new Ring(108, 142, symmetrySlots()));
            return screen;
        }
        if (held.getItem() instanceof SawItem) {
            int current = SawItem.thickness(held);
            List<Slot> sizes = new ArrayList<>();
            for (int size : ChiselShape.SIZES) {
                sizes.add(new Slot(Component.translatable("bitsmith.saw.thickness", size), null, size == current,
                        () -> send(ToolOptionPayload.Option.SAW_THICKNESS, size)));
            }
            screen.rings.add(new Ring(36, 96, sizes));
            return screen;
        }
        if (held.getItem() instanceof BitItem) {
            screen.rings.add(new Ring(36, 96, symmetrySlots()));
            return screen;
        }
        return null;
    }

    /** The mirror choices, shared by every tool that carves or places bits. */
    private static List<Slot> symmetrySlots() {
        List<Slot> slots = new ArrayList<>();
        for (Symmetry symmetry : Symmetry.values()) {
            slots.add(new Slot(SymmetryControl.name(symmetry), null, PlayerSymmetry.client() == symmetry,
                    () -> SymmetryControl.set(symmetry)));
        }
        return slots;
    }

    private static void send(ToolOptionPayload.Option option, int value) {
        PacketDistributor.sendToServer(new ToolOptionPayload(option, value));
    }

    @Override
    protected void init() {
        super.init();
        // Room for the menu plus its caption above it.
        float available = Math.min(width, height) / 2.0F - 16;
        menuScale = Math.min(1.0F, available / (outerRadius() + 14));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // No blur, only a light shade: the world stays visible behind the menu.
        graphics.fill(0, 0, width, height, SCRIM);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float cx = width / 2.0F;
        float cy = height / 2.0F;
        hoverAt(mouseX, mouseY);
        renderBackground(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().scale(menuScale, menuScale, 1);
        graphics.pose().translate(-cx, -cy, 0);

        Matrix4f matrix = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());

        // A dark disc behind the rings, so they read on any background.
        sector(buffer, matrix, cx, cy, 0, outerRadius() + 4, -Mth.HALF_PI, Mth.PI * 1.5F, BACKDROP);
        // Centre: undo on the left half, redo on the right.
        bordered(buffer, matrix, cx, cy, 0, CENTER_RADIUS, Mth.HALF_PI + GAP, Mth.PI * 1.5F - GAP,
                hoveredCenter < 0 ? UiKit.ACCENT : IDLE);
        bordered(buffer, matrix, cx, cy, 0, CENTER_RADIUS, -Mth.HALF_PI + GAP, Mth.HALF_PI - GAP,
                hoveredCenter > 0 ? UiKit.ACCENT : IDLE);

        for (Ring ring : rings) {
            float step = Mth.TWO_PI / ring.slots().size();
            for (int i = 0; i < ring.slots().size(); i++) {
                Slot slot = ring.slots().get(i);
                boolean hover = slot == hovered;
                float from = -Mth.HALF_PI + i * step + GAP;
                float to = from + step - 2 * GAP;
                int fill = hover ? UiKit.SURFACE_HOVER : slot.selected() ? SELECTED : IDLE;
                bordered(buffer, matrix, cx, cy, ring.inner(), ring.outer(), from, to, fill);
                if (hover || slot.selected()) {
                    // An accent edge on the outside, like the panel's active tab.
                    float inset = 1.0F / ring.outer();
                    sector(buffer, matrix, cx, cy, ring.outer() - 3, ring.outer() - 1, from + inset, to - inset,
                            hover ? UiKit.ACCENT_HOVER : UiKit.ACCENT);
                }
            }
        }
        graphics.flush();

        UiKit.centred(graphics, font, Component.literal("<"), (int) (cx - CENTER_RADIUS / 2), (int) cy - 4,
                hoveredCenter < 0 ? UiKit.ON_ACCENT : UiKit.MUTED);
        UiKit.centred(graphics, font, Component.literal(">"), (int) (cx + CENTER_RADIUS / 2), (int) cy - 4,
                hoveredCenter > 0 ? UiKit.ON_ACCENT : UiKit.MUTED);
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
                    UiKit.centred(graphics, font, slot.label(), x, y - 4,
                            slot == hovered || slot.selected() ? UiKit.TEXT : UiKit.MUTED);
                }
            }
        }

        Component caption = hoveredCenter < 0 ? Component.translatable("bitsmith.radial.undo")
                : hoveredCenter > 0 ? Component.translatable("bitsmith.radial.redo")
                : hovered != null ? hovered.label() : null;
        // Above the menu: below it the caption would collide with the action bar message.
        caption(graphics, caption != null ? caption : toolName, caption != null, (int) cx, (int) (cy - outerRadius() - 22));
        graphics.pose().popPose();
    }

    /** A label in a small panel, with the accent bar on its left like the panel's tooltips. */
    private void caption(GuiGraphics graphics, Component text, boolean active, int centreX, int y) {
        int w = font.width(text) + 18;
        int x = centreX - w / 2;
        UiKit.box(graphics, x - 1, y - 1, w + 2, 18, UiKit.BORDER);
        UiKit.box(graphics, x, y, w, 16, UiKit.HEADER);
        graphics.fill(x, y + 1, x + 2, y + 15, active ? UiKit.ACCENT : UiKit.FAINT);
        UiKit.centred(graphics, font, text, centreX + 1, y + 4, active ? UiKit.TEXT : UiKit.MUTED);
    }

    /** A sector with a one pixel border in the panel's border colour. */
    private static void bordered(VertexConsumer buffer, Matrix4f matrix, float cx, float cy,
                                 float inner, float outer, float from, float to, int fill) {
        sector(buffer, matrix, cx, cy, inner, outer, from, to, UiKit.BORDER);
        float inset = 1.0F / outer;
        sector(buffer, matrix, cx, cy, inner == 0 ? 0 : inner + 1, outer - 1, from + inset, to - inset, fill);
    }

    /**
     * Works out the option under a point in GUI units. Called when drawing and again when the
     * choice is made, so a mouse move and a release within the same frame still pick the option
     * actually under the cursor.
     */
    private void hoverAt(double mouseX, double mouseY) {
        float dx = (float) ((mouseX - width / 2.0) / menuScale);
        float dy = (float) ((mouseY - height / 2.0) / menuScale);
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
