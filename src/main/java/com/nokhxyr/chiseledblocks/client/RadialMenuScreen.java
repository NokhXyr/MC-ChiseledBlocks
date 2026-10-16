/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nokhxyr.chiseledblocks.core.edit.ChiselMode;
import com.nokhxyr.chiseledblocks.core.edit.ChiselShape;
import com.nokhxyr.chiseledblocks.core.edit.PlacementMode;
import com.nokhxyr.chiseledblocks.core.edit.Symmetry;
import com.nokhxyr.chiseledblocks.edit.PlayerSymmetry;
import com.nokhxyr.chiseledblocks.item.BagContents;
import com.nokhxyr.chiseledblocks.item.PixelBagItem;
import com.nokhxyr.chiseledblocks.item.PixelItem;
import com.nokhxyr.chiseledblocks.item.ChiselItem;
import com.nokhxyr.chiseledblocks.item.ChiselSettings;
import com.nokhxyr.chiseledblocks.item.PatternItem;
import com.nokhxyr.chiseledblocks.item.PatternMode;
import com.nokhxyr.chiseledblocks.item.SawItem;
import com.nokhxyr.chiseledblocks.network.ToolOptionPayload;
import com.nokhxyr.chiseledblocks.network.UndoPayload;
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
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * The tool menu, shown while the menu key is held with a Chiseled Blocks tool in hand. One wheel of
 * choices around the tool, square buttons on the sides (undo and redo on the left, the mirror and
 * the page arrows on the right) and a row below: the chisel size slider, or the placement modes
 * of a bag. A bag's materials are shown twelve per page.
 *
 * <p>Point at a wedge and release the key to pick it, or click it: a click keeps the menu open.
 * Choices are applied by the server, like the V and B keys.
 */
public final class RadialMenuScreen extends Screen {
    /** Wedges per page of the wheel. */
    public static final int PAGE = 12;
    private static final float INNER = 26;
    private static final float OUTER = 60;
    /** Icons sit at this distance from the centre. */
    private static final float ICON_RADIUS = 43;
    private static final float GAP = 0.02F;
    private static final int BUTTON = 24;
    private static final int BUTTON_STEP = 34;
    private static final int ROW_STEP = 28;
    private static final float COLUMN_OFFSET = 88;
    private static final int SLIDER_W = 90;
    private static final int SLIDER_H = 14;
    /** Top of the row below the wheel. */
    private static final float ROW_OFFSET = 74;
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

    /** One square button around the wheel. */
    private record SideButton(String id, Component label, RadialIcons.Icon icon, BooleanSupplier toggled, Runnable action) {
    }

    /** Where the buttons go: a column on each side, and a row below the wheel. */
    private enum Place { LEFT, RIGHT, BOTTOM }

    private final List<Option> all = new ArrayList<>();
    private final List<Option> options = new ArrayList<>();
    private final List<SideButton> left = new ArrayList<>();
    private final List<SideButton> right = new ArrayList<>();
    private final List<SideButton> bottom = new ArrayList<>();
    private ItemStack held = ItemStack.EMPTY;
    private int page;
    /** Index in {@link #all} of the current choice, or -1. */
    private int currentAll = -1;
    /** Index on the shown page of the current choice, or -1. */
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
        super(Component.translatable("chiseledblocks.radial.title"));
    }

    /** Builds the menu for the tool in hand, or returns null when it has no options. */
    public static @Nullable RadialMenuScreen forItem(ItemStack held) {
        RadialMenuScreen screen = new RadialMenuScreen();
        screen.held = held.copy();
        if (held.getItem() instanceof ChiselItem) {
            ChiselSettings settings = ChiselItem.settings(held);
            for (ChiselMode mode : ChiselMode.values()) {
                screen.all.add(new Option(Component.translatable("chiseledblocks.mode." + mode.name().toLowerCase(Locale.ROOT)),
                        RadialIcons.mode(mode), () -> send(ToolOptionPayload.Option.CHISEL_MODE, mode.ordinal())));
            }
            screen.currentAll = settings.mode().ordinal();
            screen.slider(ChiselShape.SIZES, settings.size(), "chiseledblocks.radial.size",
                    size -> send(ToolOptionPayload.Option.CHISEL_SIZE, size));
            screen.undoButtons();
            screen.mirrorButton();
        } else if (held.getItem() instanceof PatternItem) {
            for (PatternMode mode : PatternMode.values()) {
                screen.all.add(new Option(mode.displayName(), RadialIcons.pattern(mode),
                        () -> send(ToolOptionPayload.Option.PATTERN_MODE, mode.ordinal())));
            }
            screen.currentAll = PatternItem.mode(held).ordinal();
            screen.undoButtons();
        } else if (held.getItem() instanceof PixelBagItem bag) {
            BagContents contents = PixelBagItem.contents(held);
            for (int i = 0; i < contents.entries().size(); i++) {
                BagContents.Entry entry = contents.entries().get(i);
                int index = i;
                screen.all.add(new Option(bag.describeEntry(entry), RadialIcons.item(PixelItem.of(entry.material(), 1)),
                        () -> send(ToolOptionPayload.Option.BAG_MATERIAL, index)));
            }
            if (contents.isEmpty()) {
                screen.emptyHint = Component.translatable("chiseledblocks.bag.empty");
            } else {
                screen.currentAll = contents.selected();
                screen.page = contents.selected() / PAGE;
            }
            screen.undoButtons();
            screen.mirrorButton();
            screen.pageButtons();
            screen.placementRow();
        } else if (held.getItem() instanceof SawItem) {
            int thickness = SawItem.thickness(held);
            for (int size : ChiselShape.SIZES) {
                screen.all.add(new Option(Component.translatable("chiseledblocks.saw.thickness", size), RadialIcons.slice(size),
                        () -> send(ToolOptionPayload.Option.SAW_THICKNESS, size)));
                if (size == thickness) {
                    screen.currentAll = screen.all.size() - 1;
                }
            }
            screen.undoButtons();
        } else if (held.getItem() instanceof PixelItem) {
            // Loose bits: the wheel holds the placement modes.
            for (PlacementMode mode : PlacementMode.values()) {
                screen.all.add(new Option(PlacementControl.name(mode), RadialIcons.placement(mode),
                        () -> PlacementControl.set(mode)));
            }
            screen.currentAll = PlacementControl.mode().ordinal();
            screen.undoButtons();
            screen.mirrorButton();
        } else {
            return null;
        }
        screen.showPage();
        return screen;
    }

    private void undoButtons() {
        left.add(new SideButton("undo", Component.translatable("chiseledblocks.radial.undo"), RadialIcons.undo(false), () -> false,
                () -> PacketDistributor.sendToServer(new UndoPayload(false))));
        left.add(new SideButton("redo", Component.translatable("chiseledblocks.radial.redo"), RadialIcons.undo(true), () -> false,
                () -> PacketDistributor.sendToServer(new UndoPayload(true))));
    }

    /** One button showing the mirror; each click moves to the next setting. */
    private void mirrorButton() {
        Symmetry symmetry = PlayerSymmetry.client();
        SideButton button = new SideButton("mirror", Component.translatable("chiseledblocks.symmetry.set", SymmetryControl.name(symmetry)),
                RadialIcons.text(SymmetryControl.name(symmetry)), () -> PlayerSymmetry.client() != Symmetry.NONE, () -> {
                    Symmetry[] values = Symmetry.values();
                    SymmetryControl.set(values[(PlayerSymmetry.client().ordinal() + 1) % values.length]);
                    right.removeIf(each -> each.id().equals("mirror"));
                    mirrorButton();
                });
        right.add(0, button);
    }

    private void pageButtons() {
        if (pageCount() <= 1) {
            return;
        }
        right.add(new SideButton("page.prev", Component.translatable("chiseledblocks.radial.page.previous"), RadialIcons.arrow(false),
                () -> false, () -> turnPage(-1)));
        right.add(new SideButton("page.next", Component.translatable("chiseledblocks.radial.page.next"), RadialIcons.arrow(true),
                () -> false, () -> turnPage(1)));
    }

    /** A bag's placement modes, as a row of buttons under the wheel. */
    private void placementRow() {
        for (PlacementMode mode : PlacementMode.values()) {
            bottom.add(new SideButton("place." + mode.name().toLowerCase(Locale.ROOT), PlacementControl.name(mode),
                    RadialIcons.placement(mode), () -> PlacementControl.mode() == mode, () -> PlacementControl.set(mode)));
        }
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

    // Pages

    private int pageCount() {
        return Math.max(1, (all.size() + PAGE - 1) / PAGE);
    }

    /** The page shown, from 0, for the playtest. */
    public int page() {
        return page;
    }

    private void turnPage(int by) {
        page = Math.floorMod(page + by, pageCount());
        showPage();
    }

    private void showPage() {
        page = Mth.clamp(page, 0, pageCount() - 1);
        options.clear();
        options.addAll(all.subList(Math.min(all.size(), page * PAGE), Math.min(all.size(), (page + 1) * PAGE)));
        int onPage = currentAll - page * PAGE;
        current = onPage >= 0 && onPage < options.size() ? onPage : -1;
    }

    // Layout, in GUI units, without the opening animation

    @Override
    protected void init() {
        super.init();
        // Wheel, buttons, labels and the row below must fit, at any GUI scale.
        float halfWidth = Math.max(COLUMN_OFFSET + BUTTON / 2.0F + 8, bottom.size() * ROW_STEP / 2.0F + 8);
        float halfHeight = ROW_OFFSET + BUTTON + 4;
        menuScale = Math.min(1.0F, Math.min((width / 2.0F - 8) / halfWidth, (height / 2.0F - 8) / halfHeight));
        if (sliderValues != null) {
            int sx = Math.round(width / 2.0F - SLIDER_W * menuScale / 2);
            int sy = Math.round(height / 2.0F + ROW_OFFSET * menuScale);
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

    private float cx() {
        return width / 2.0F;
    }

    private float cy() {
        return height / 2.0F;
    }

    private List<SideButton> buttons(Place place) {
        return switch (place) {
            case LEFT -> left;
            case RIGHT -> right;
            case BOTTOM -> bottom;
        };
    }

    /** Top left corner and size of a button. */
    private int[] boxOf(Place place, int index, int count) {
        float x;
        float y;
        if (place == Place.BOTTOM) {
            float rowWidth = count * ROW_STEP - (ROW_STEP - BUTTON);
            x = -rowWidth / 2.0F + index * ROW_STEP;
            y = ROW_OFFSET;
        } else {
            float column = place == Place.LEFT ? -COLUMN_OFFSET : COLUMN_OFFSET;
            x = column - BUTTON / 2.0F;
            y = -(count * BUTTON_STEP - (BUTTON_STEP - BUTTON)) / 2.0F + index * BUTTON_STEP;
        }
        return new int[]{Math.round(cx() + x * menuScale), Math.round(cy() + y * menuScale), Math.round(BUTTON * menuScale)};
    }

    /** Centre of a named button or of a wedge of the shown page ({@code "option.N"}), for the playtest. */
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
        for (Place place : Place.values()) {
            List<SideButton> list = buttons(place);
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id().equals(id)) {
                    int[] box = boxOf(place, i, list.size());
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
        if (pageCount() > 1) {
            Component pageLabel = Component.translatable("chiseledblocks.radial.page", page + 1, pageCount());
            graphics.drawCenteredString(font, pageLabel, (int) cx, (int) (cy + OUTER + 3), 0xFFDDDDDD);
        }
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
        // While the cursor is on the wheel, the buttons step back.
        float alpha = eased * (hovered >= 0 ? 0.45F : 1.0F);
        for (Place place : Place.values()) {
            List<SideButton> list = buttons(place);
            for (int i = 0; i < list.size(); i++) {
                SideButton button = list.get(i);
                int[] box = boxOf(place, i, list.size());
                // Slides out from the centre while opening.
                int x = Math.round(cx() + (box[0] - cx()) * eased);
                int y = Math.round(cy() + (box[1] - cy()) * eased);
                int size = box[2];
                boolean hover = button == hoveredButton;
                int background = hover ? 0xA0FFFFFF : button.toggled().getAsBoolean() ? 0x9040A040 : 0x90000000;
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
        for (Place place : Place.values()) {
            List<SideButton> list = buttons(place);
            for (int i = 0; i < list.size(); i++) {
                int[] box = boxOf(place, i, list.size());
                if (mouseX >= box[0] && mouseX < box[0] + box[2] && mouseY >= box[1] && mouseY < box[1] + box[2]) {
                    hoveredButton = list.get(i);
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
        currentAll = page * PAGE + index;
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (pageCount() > 1 && scrollY != 0) {
            turnPage(scrollY < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ChiseledBlocksKeys.RADIAL.matches(keyCode, scanCode)) {
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
            setMessage(Component.translatable("chiseledblocks.radial.slider", sliderLabel, sliderValues[index()]));
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
