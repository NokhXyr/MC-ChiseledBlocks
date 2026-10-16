/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;

/**
 * The tool menu, shown while the menu key is held with a Bitsmith tool in hand. One wheel of
 * choices around the tool, square buttons on the sides (undo and redo on the left, the mirror on
 * the right), and a slider below for the chisel size.
 *
 * <p>Point at a wedge and release the key to pick it, or click it: a click keeps the menu open.
 * Choices are applied by the server, like the V and B keys.
 */
public final class RadialMenuScreen extends Screen {
    private static final float INNER = 26;
    private static final float OUTER = 60;
    /** Icons sit at this distance from the centre. */
    private static final float ICON_RADIUS = 43;
    private static final float GAP = 0.02F;
    private static final int BUTTON = 24;
    private static final int BUTTON_STEP = 34;
    private static final float COLUMN_OFFSET = 88;
    private static final int SLIDER_W = 90;
    private static final int SLIDER_H = 14;
    private static final float SLIDER_OFFSET = 74;
    /** Ticks of the opening animation. */
    private static final float OPENING = 6;

    private static final int WEDGE_DARK = 0x66404040;
    private static final int WEDGE_LIGHT = 0x66595959;
    private static final int WEDGE_CURRENT_DARK = 0x6640FF40;
    private static final int WEDGE_CURRENT_LIGHT = 0x6659FF59;
    private static final int WEDGE_HOVER = 0x66FFFFFF;
    private static final int ICON = 0xFFFFFFFF;
    private static final int ICON_CURRENT = 0xFF66FF66;

    /** One wedge of the wheel. */
    private record Option(Component label, RadialIcons.Icon icon, Runnable action) {
    }

    /** One square button on a side. */
    private record SideButton(String id, Component label, RadialIcons.Icon icon, boolean toggled, Runnable action) {
    }

    private final List<Option> options = new ArrayList<>();
    private final List<SideButton> left = new ArrayList<>();
    private final List<SideButton> right = new ArrayList<>();
    private ItemStack held = ItemStack.EMPTY;
    private int current = -1;
    private @Nullable Component emptyHint;
    private @Nullable SizeSlider slider;
    private int @Nullable [] sliderValues;
    private int sliderIndex;
    private @Nullable IntConsumer sliderAction;
    private Component sliderLabel = Component.empty();

    private float menuScale = 1.0F;
    private int ticksOpen;
    private int hovered = -1;
    private @Nullable SideButton hoveredButton;

    private RadialMenuScreen() {
        super(Component.translatable("bitsmith.radial.title"));
    }

    /** Builds the menu for the tool in hand, or returns null when it has no options. */
    public static @Nullable RadialMenuScreen forItem(ItemStack held) {
        RadialMenuScreen screen = new RadialMenuScreen();
        screen.held = held.copy();
        if (held.getItem() instanceof ChiselItem) {
            ChiselSettings settings = ChiselItem.settings(held);
            for (ChiselMode mode : ChiselMode.values()) {
                screen.options.add(new Option(Component.translatable("bitsmith.mode." + mode.name().toLowerCase(Locale.ROOT)),
                        RadialIcons.mode(mode), () -> send(ToolOptionPayload.Option.CHISEL_MODE, mode.ordinal())));
            }
            screen.current = settings.mode().ordinal();
            screen.slider(ChiselShape.SIZES, settings.size(), "bitsmith.radial.size",
                    size -> send(ToolOptionPayload.Option.CHISEL_SIZE, size));
            screen.undoButtons();
            screen.mirrorButton();
            return screen;
        }
        if (held.getItem() instanceof PatternItem) {
            for (PatternMode mode : PatternMode.values()) {
                screen.options.add(new Option(mode.displayName(), RadialIcons.pattern(mode),
                        () -> send(ToolOptionPayload.Option.PATTERN_MODE, mode.ordinal())));
            }
            screen.current = PatternItem.mode(held).ordinal();
            screen.undoButtons();
            return screen;
        }
        if (held.getItem() instanceof BitBagItem bag) {
            BagContents contents = BitBagItem.contents(held);
            for (int i = 0; i < contents.entries().size(); i++) {
                BagContents.Entry entry = contents.entries().get(i);
                int index = i;
                screen.options.add(new Option(bag.describeEntry(entry), RadialIcons.item(BitItem.of(entry.material(), 1)),
                        () -> send(ToolOptionPayload.Option.BAG_MATERIAL, index)));
            }
            screen.current = contents.isEmpty() ? -1 : contents.selected();
            if (contents.isEmpty()) {
                screen.emptyHint = Component.translatable("bitsmith.bag.empty");
            }
            screen.undoButtons();
            screen.mirrorButton();
            return screen;
        }
        if (held.getItem() instanceof SawItem) {
            int thickness = SawItem.thickness(held);
            for (int size : ChiselShape.SIZES) {
                screen.options.add(new Option(Component.translatable("bitsmith.saw.thickness", size), RadialIcons.slice(size),
                        () -> send(ToolOptionPayload.Option.SAW_THICKNESS, size)));
                if (size == thickness) {
                    screen.current = screen.options.size() - 1;
                }
            }
            screen.undoButtons();
            return screen;
        }
        if (held.getItem() instanceof BitItem) {
            for (Symmetry symmetry : Symmetry.values()) {
                screen.options.add(new Option(Component.translatable("bitsmith.symmetry.set", SymmetryControl.name(symmetry)),
                        RadialIcons.text(SymmetryControl.name(symmetry)), () -> SymmetryControl.set(symmetry)));
            }
            screen.current = PlayerSymmetry.client().ordinal();
            screen.undoButtons();
            return screen;
        }
        return null;
    }

    private void undoButtons() {
        left.add(new SideButton("undo", Component.translatable("bitsmith.radial.undo"), RadialIcons.undo(false), false,
                () -> PacketDistributor.sendToServer(new UndoPayload(false))));
        left.add(new SideButton("redo", Component.translatable("bitsmith.radial.redo"), RadialIcons.undo(true), false,
                () -> PacketDistributor.sendToServer(new UndoPayload(true))));
    }

    /** One button showing the mirror; each click moves to the next setting. */
    private void mirrorButton() {
        Symmetry symmetry = PlayerSymmetry.client();
        right.add(new SideButton("mirror", Component.translatable("bitsmith.symmetry.set", SymmetryControl.name(symmetry)),
                RadialIcons.text(SymmetryControl.name(symmetry)), symmetry != Symmetry.NONE, () -> {
                    Symmetry[] all = Symmetry.values();
                    SymmetryControl.set(all[(PlayerSymmetry.client().ordinal() + 1) % all.length]);
                    right.clear();
                    mirrorButton();
                }));
    }

    private void slider(int[] values, int currentValue, String labelKey, IntConsumer action) {
        sliderValues = values;
        sliderAction = action;
        sliderLabel = Component.translatable(labelKey);
        sliderIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == currentValue) {
                sliderIndex = i;
            }
        }
    }

    private static void send(ToolOptionPayload.Option option, int value) {
        PacketDistributor.sendToServer(new ToolOptionPayload(option, value));
    }

    @Override
    protected void init() {
        super.init();
        // Wheel, side columns, labels and slider must fit, at any GUI scale.
        float halfWidth = COLUMN_OFFSET + BUTTON / 2.0F + 8;
        float halfHeight = SLIDER_OFFSET + SLIDER_H + 4;
        menuScale = Math.min(1.0F, Math.min((width / 2.0F - 8) / halfWidth, (height / 2.0F - 8) / halfHeight));
        if (sliderValues != null) {
            int sx = Math.round(width / 2.0F - SLIDER_W * menuScale / 2);
            int sy = Math.round(height / 2.0F + SLIDER_OFFSET * menuScale);
            slider = new SizeSlider(sx, sy, Math.round(SLIDER_W * menuScale), SLIDER_H);
            addWidget(slider);
        }
    }

    @Override
    public void tick() {
        ticksOpen++;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Nothing: the world stays fully visible around the wheel.
    }

    // Layout, in GUI units, without the opening animation

    private float cx() {
        return width / 2.0F;
    }

    private float cy() {
        return height / 2.0F;
    }

    /** Top left corner and size of a side button. */
    private int[] buttonAt(boolean leftSide, int index, int count) {
        float column = leftSide ? -COLUMN_OFFSET : COLUMN_OFFSET;
        float top = -(count * BUTTON_STEP - (BUTTON_STEP - BUTTON)) / 2.0F;
        float x = cx() + (column - BUTTON / 2.0F) * menuScale;
        float y = cy() + (top + index * BUTTON_STEP) * menuScale;
        return new int[]{Math.round(x), Math.round(y), Math.round(BUTTON * menuScale)};
    }

    /** Centre of a named button or of a wedge ({@code "option.N"}), for the playtest. */
    public int @Nullable [] locate(String id) {
        if (id.startsWith("option.")) {
            int index = Integer.parseInt(id.substring(7));
            if (index < 0 || index >= options.size()) {
                return null;
            }
            float angle = wedgeMiddle(index);
            return new int[]{Math.round(cx() + Mth.cos(angle) * ICON_RADIUS * menuScale),
                    Math.round(cy() + Mth.sin(angle) * ICON_RADIUS * menuScale)};
        }
        for (int side = 0; side < 2; side++) {
            List<SideButton> column = side == 0 ? left : right;
            for (int i = 0; i < column.size(); i++) {
                if (column.get(i).id().equals(id)) {
                    int[] box = buttonAt(side == 0, i, column.size());
                    return new int[]{box[0] + box[2] / 2, box[1] + box[2] / 2};
                }
            }
        }
        return null;
    }

    private float wedgeStep() {
        return Mth.TWO_PI / Math.max(1, options.size());
    }

    /** Wedges start at the top and run clockwise. */
    private float wedgeMiddle(int index) {
        // A single choice fills the whole wheel: its icon goes at the top.
        return options.size() == 1 ? -Mth.HALF_PI : -Mth.HALF_PI + (index + 0.5F) * wedgeStep();
    }

    // Drawing

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        hoverAt(mouseX, mouseY);
        float opened = Mth.clamp((ticksOpen + partialTick) / OPENING, 0, 1);
        float eased = 1 - (1 - opened) * (1 - opened);
        float cx = cx();
        float cy = cy();
        float scale = menuScale * (0.6F + 0.4F * eased);

        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().scale(scale, scale, 1);
        graphics.pose().translate(-cx, -cy, 0);
        renderWheel(graphics, cx, cy, ticksOpen + partialTick);
        renderIcons(graphics, cx, cy, eased);
        graphics.pose().pushPose();
        graphics.pose().translate(cx, cy, 0);
        graphics.pose().scale(1.8F, 1.8F, 1);
        graphics.renderItem(held, -8, -8);
        graphics.pose().popPose();
        renderHoveredLabel(graphics, cx, cy);
        graphics.pose().popPose();

        renderButtons(graphics, eased);
        if (slider != null) {
            slider.setAlpha(eased);
            slider.render(graphics, mouseX, mouseY, partialTick);
        }
        if (hoveredButton != null) {
            graphics.renderTooltip(font, hoveredButton.label(), mouseX, mouseY);
        }
    }

    private void renderWheel(GuiGraphics graphics, float cx, float cy, float time) {
        Matrix4f matrix = graphics.pose().last().pose();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.gui());
        int count = Math.max(1, options.size());
        float step = wedgeStep();
        for (int i = 0; i < count; i++) {
            // Each wedge grows out from the centre a little after the previous one.
            float grow = Mth.clamp((time - i * 0.5F) / 3.0F, 0, 1);
            float outer = INNER + (OUTER - INNER) * grow;
            boolean light = i % 2 == 0;
            int colour = options.isEmpty() ? WEDGE_DARK
                    : i == hovered ? WEDGE_HOVER
                    : i == current ? (light ? WEDGE_CURRENT_LIGHT : WEDGE_CURRENT_DARK)
                    : light ? WEDGE_LIGHT : WEDGE_DARK;
            float gap = count > 1 ? GAP : 0;
            sector(buffer, matrix, cx, cy, INNER, outer, -Mth.HALF_PI + i * step + gap, -Mth.HALF_PI + (i + 1) * step - gap, colour);
        }
        graphics.flush();
    }

    private void renderIcons(GuiGraphics graphics, float cx, float cy, float eased) {
        if (options.isEmpty()) {
            if (emptyHint != null) {
                graphics.drawCenteredString(font, emptyHint, (int) cx, (int) (cy + OUTER + 6), 0xFFFFFFFF);
            }
            return;
        }
        for (int i = 0; i < options.size(); i++) {
            float angle = wedgeMiddle(i);
            float radius = INNER + (ICON_RADIUS - INNER) * eased;
            int x = Math.round(cx + Mth.cos(angle) * radius) - 8;
            int y = Math.round(cy + Mth.sin(angle) * radius) - 8;
            options.get(i).icon().draw(graphics, font, x, y, i == current ? ICON_CURRENT : ICON);
        }
    }

    /** The name of the pointed wedge, just outside the wheel, on the wedge's side. */
    private void renderHoveredLabel(GuiGraphics graphics, float cx, float cy) {
        if (hovered < 0) {
            return;
        }
        Component label = options.get(hovered).label();
        float angle = wedgeMiddle(hovered);
        float cos = Mth.cos(angle);
        float sin = Mth.sin(angle);
        float ax = cx + cos * (OUTER + 6);
        float ay = cy + sin * (OUTER + 6);
        int labelWidth = font.width(label);
        int x = Math.abs(cos) < 0.3F ? Math.round(ax - labelWidth / 2.0F) : cos > 0 ? Math.round(ax) : Math.round(ax - labelWidth);
        int y = Math.round(ay - 4 + sin * 4);
        graphics.drawString(font, label, x, y, hovered == current ? ICON_CURRENT : 0xFFFFFFFF, true);
    }

    private void renderButtons(GuiGraphics graphics, float eased) {
        // While the cursor is on the wheel, the side buttons step back.
        float alpha = eased * (hovered >= 0 ? 0.45F : 1.0F);
        for (int side = 0; side < 2; side++) {
            List<SideButton> column = side == 0 ? left : right;
            for (int i = 0; i < column.size(); i++) {
                SideButton button = column.get(i);
                int[] box = buttonAt(side == 0, i, column.size());
                // Slides out from the centre while opening.
                int x = Math.round(cx() + (box[0] - cx()) * eased);
                int y = box[1];
                int size = box[2];
                boolean hover = button == hoveredButton;
                int background = hover ? 0xA0FFFFFF : button.toggled() ? 0x9040A040 : 0x90000000;
                graphics.fill(x, y, x + size, y + size, withAlpha(background, alpha));
                graphics.fill(x, y, x + size, y + 1, withAlpha(0x60FFFFFF, alpha));
                graphics.fill(x, y + size - 1, x + size, y + size, withAlpha(0x60000000, alpha));
                graphics.pose().pushPose();
                graphics.pose().translate(x + size / 2.0F, y + size / 2.0F, 0);
                graphics.pose().scale(menuScale, menuScale, 1);
                button.icon().draw(graphics, font, -8, -8, withAlpha(hover ? 0xFF202020 : 0xFFFFFFFF, alpha));
                graphics.pose().popPose();
            }
        }
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.round((argb >>> 24) * Mth.clamp(alpha, 0, 1));
        return (a << 24) | (argb & 0xFFFFFF);
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

    // Pointing

    /**
     * Works out what is under a point in GUI units. Called when drawing and again when the
     * choice is made, so a mouse move and a release within the same frame still pick the option
     * actually under the cursor.
     */
    private void hoverAt(double mouseX, double mouseY) {
        hovered = -1;
        hoveredButton = null;
        for (int side = 0; side < 2; side++) {
            List<SideButton> column = side == 0 ? left : right;
            for (int i = 0; i < column.size(); i++) {
                int[] box = buttonAt(side == 0, i, column.size());
                if (mouseX >= box[0] && mouseX < box[0] + box[2] && mouseY >= box[1] && mouseY < box[1] + box[2]) {
                    hoveredButton = column.get(i);
                    return;
                }
            }
        }
        if (slider != null && slider.isMouseOver(mouseX, mouseY)) {
            return;
        }
        float dx = (float) ((mouseX - cx()) / menuScale);
        float dy = (float) ((mouseY - cy()) / menuScale);
        // Anything past the inner edge counts, so a quick flick past the wheel still picks.
        if (options.isEmpty() || Mth.sqrt(dx * dx + dy * dy) < INNER) {
            return;
        }
        float angle = (float) Math.atan2(dy, dx) + Mth.HALF_PI;
        if (angle < 0) {
            angle += Mth.TWO_PI;
        }
        hovered = Mth.clamp((int) (angle / wedgeStep()), 0, options.size() - 1);
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

    private void pick(int index) {
        options.get(index).action().run();
        current = index;
    }

    private static void clickSound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        hoverAt(mouseX, mouseY);
        if (button == 0 && hoveredButton != null) {
            clickSound();
            hoveredButton.action().run();
            return true;
        }
        if (button == 0 && hovered >= 0) {
            clickSound();
            pick(hovered);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (BitsmithKeys.RADIAL.matches(keyCode, scanCode)) {
            hoverAtCursor();
            if (hovered >= 0 && hovered != current) {
                pick(hovered);
            }
            onClose();
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    /** The chisel size, one notch per size. Each change is sent as it happens. */
    private final class SizeSlider extends AbstractSliderButton {
        SizeSlider(int x, int y, int width, int height) {
            super(x, y, width, height, Component.empty(), sliderValues.length > 1 ? sliderIndex / (sliderValues.length - 1.0) : 0);
            updateMessage();
        }

        private int index() {
            return (int) Math.round(value * (sliderValues.length - 1));
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("bitsmith.radial.slider", sliderLabel, sliderValues[index()]));
        }

        @Override
        protected void applyValue() {
            int index = index();
            // Snap to the notch.
            value = sliderValues.length > 1 ? index / (sliderValues.length - 1.0) : 0;
            if (index != sliderIndex && sliderAction != null) {
                sliderIndex = index;
                sliderAction.accept(sliderValues[index]);
            }
        }
    }
}
