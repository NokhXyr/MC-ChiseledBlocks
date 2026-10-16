/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client.admin;

import com.mojang.blaze3d.platform.InputConstants;
import com.nokhxyr.chiseledblocks.admin.ChiseledSearch;
import com.nokhxyr.chiseledblocks.network.AdminPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.DoubleConsumer;

/**
 * The admin screen: search for carved blocks, a map of the chunks around the player, and the
 * server time each carved block costs. Drawn with its own flat widgets; every list row can
 * teleport the player to its block.
 */
public final class AdminScreen extends Screen {
    private enum Tab {
        SEARCH("chiseledblocks.admin.tab.search"),
        MAP("chiseledblocks.admin.tab.map"),
        PERF("chiseledblocks.admin.tab.perf");

        final String key;

        Tab(String key) {
            this.key = key;
        }
    }

    private enum Look { PRIMARY, SECONDARY, GHOST, DANGER }

    private static final int HEADER_H = 26;
    private static final int FOOTER_H = 16;
    private static final int ROW_H = 20;
    private static final int TABLE_HEADER_H = 14;
    private static final int[] BLOCK_RADII = {8, 16, 32, 64, 128, 256, 512};
    private static final int[] PERF_SECONDS = {5, 10, 15, 30, 60, 120, 300};
    private static final int TABLE_SEARCH = 0;
    private static final int TABLE_CHUNK = 1;
    private static final int TABLE_PERF = 2;
    private static final int MAP_REFRESH_TICKS = 40;

    // Remembered while the game runs, so the screen opens as it was left.
    private static Tab tab = Tab.SEARCH;
    private static ChiseledSearch.Scope scope = ChiseledSearch.Scope.BLOCKS;
    private static int blockRadiusIndex = 3;
    private static int chunkRadius = 4;
    private static String material = "";
    private static int mapRadius = 8;
    private static int perfSecondsIndex = 1;

    private final List<Hotspot> hotspots = new ArrayList<>();
    private final double[] scroll = new double[3];
    private final Map<ResourceLocation, ItemStack> icons = new HashMap<>();
    private EditBox materialField;
    private List<ResourceLocation> suggestions = List.of();
    private @Nullable List<Component> tooltip;
    private @Nullable int[] clip;
    private @Nullable int[] overlay;
    private int mouseX;
    private int mouseY;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int ticks;
    private boolean selected;
    private int selectedX;
    private int selectedZ;
    private int dragTable = -1;
    /** Name given to the next registered area, so the playtest can find it on screen. */
    private @Nullable String nextId;
    private final int[][] scrollTracks = new int[3][];

    private record Hotspot(int x, int y, int w, int h, @Nullable Runnable click, @Nullable DoubleConsumer scroll,
            @Nullable String id) {
        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    public AdminScreen() {
        super(Component.translatable("chiseledblocks.admin.title"));
    }

    @Override
    protected void init() {
        if (tab == Tab.PERF && !AdminClient.canProfile) {
            tab = Tab.SEARCH;
        }
        panelW = Math.min(width - 16, 580);
        panelH = Math.min(height - 16, 350);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        materialField = new EditBox(font, 0, 0, 100, 12, Component.translatable("chiseledblocks.admin.material"));
        materialField.setBordered(false);
        materialField.setMaxLength(128);
        materialField.setTextColor(UiKit.TEXT);
        materialField.setHint(Component.translatable("chiseledblocks.admin.material.hint").withColor(UiKit.FAINT));
        materialField.setValue(material);
        materialField.setResponder(text -> {
            material = text;
            updateSuggestions();
        });
        addWidget(materialField);
        if (AdminClient.search == null) {
            search();
        }
        if (tab == Tab.MAP) {
            requestMap();
        }
    }

    @Override
    public boolean isPauseScreen() {
        // A paused integrated server would stop the very ticks being measured.
        return false;
    }

    @Override
    public void tick() {
        ticks++;
        if (tab == Tab.MAP && ticks % MAP_REFRESH_TICKS == 0) {
            requestMap();
        }
    }

    // Requests

    private void search() {
        int radius = scope == ChiseledSearch.Scope.BLOCKS ? BLOCK_RADII[blockRadiusIndex] : chunkRadius;
        scroll[TABLE_SEARCH] = 0;
        PacketDistributor.sendToServer(new AdminPayloads.FindRequest(AdminClient.TARGET_SEARCH, scope.ordinal(), radius,
                0, 0, material.trim()));
    }

    private void requestMap() {
        PacketDistributor.sendToServer(new AdminPayloads.MapRequest(mapRadius));
    }

    private void selectChunk(int chunkX, int chunkZ) {
        if (selected && selectedX == chunkX && selectedZ == chunkZ) {
            selected = false;
            return;
        }
        selected = true;
        selectedX = chunkX;
        selectedZ = chunkZ;
        AdminClient.chunk = null;
        scroll[TABLE_CHUNK] = 0;
        PacketDistributor.sendToServer(new AdminPayloads.FindRequest(AdminClient.TARGET_CHUNK,
                ChiseledSearch.Scope.CHUNK.ordinal(), 0, chunkX, chunkZ, ""));
    }

    private void teleport(ResourceLocation dimension, BlockPos pos) {
        PacketDistributor.sendToServer(new AdminPayloads.Teleport(dimension, pos));
        onClose();
    }

    private void switchTab(Tab next) {
        tab = next;
        materialField.setFocused(false);
        setFocused(null);
        if (next == Tab.MAP) {
            requestMap();
        }
    }

    // Drawing

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Drawn in render: a plain scrim, no blur.
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        hotspots.clear();
        tooltip = null;
        overlay = null;
        g.fill(0, 0, width, height, UiKit.SCRIM);
        UiKit.box(g, panelX - 1, panelY - 1, panelW + 2, panelH + 2, UiKit.BORDER);
        UiKit.box(g, panelX, panelY, panelW, panelH, UiKit.PANEL);
        renderHeader(g);

        int x = panelX + 10;
        int y = panelY + HEADER_H + 8;
        int w = panelW - 20;
        int h = panelH - HEADER_H - 8 - FOOTER_H - 4;
        switch (tab) {
            case SEARCH -> renderSearch(g, x, y, w, h, partialTick);
            case MAP -> renderMap(g, x, y, w, h);
            case PERF -> renderPerf(g, x, y, w, h);
        }
        renderFooter(g);
        if (tooltip != null) {
            renderTooltip(g, tooltip);
        }
    }

    /** A flat tooltip in the screen's colours, kept inside the window. */
    private void renderTooltip(GuiGraphics g, List<Component> lines) {
        int w = 0;
        for (Component line : lines) {
            w = Math.max(w, font.width(line));
        }
        w += 12;
        int h = lines.size() * 11 + 7;
        int x = mouseX + 12;
        int y = mouseY - 4;
        if (x + w > width - 4) {
            x = mouseX - 12 - w;
        }
        y = Mth.clamp(y, 4, height - h - 4);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        UiKit.box(g, x - 1, y - 1, w + 2, h + 2, UiKit.BORDER);
        UiKit.box(g, x, y, w, h, UiKit.HEADER);
        g.fill(x, y + 1, x + 2, y + h - 1, UiKit.ACCENT);
        for (int i = 0; i < lines.size(); i++) {
            UiKit.text(g, font, lines.get(i), x + 7, y + 4 + i * 11, i == 0 ? UiKit.TEXT : UiKit.MUTED);
        }
        g.pose().popPose();
    }

    private void renderHeader(GuiGraphics g) {
        g.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + HEADER_H, UiKit.HEADER);
        g.fill(panelX + 1, panelY + HEADER_H, panelX + panelW - 1, panelY + HEADER_H + 1, UiKit.BORDER);
        int textY = panelY + (HEADER_H - 8) / 2;
        // A small mark made of three bits, then the name.
        g.fill(panelX + 10, textY + 1, panelX + 13, textY + 4, UiKit.ACCENT);
        g.fill(panelX + 14, textY + 1, panelX + 17, textY + 4, UiKit.mix(UiKit.ACCENT, UiKit.PANEL, 0.45f));
        g.fill(panelX + 10, textY + 5, panelX + 13, textY + 8, UiKit.mix(UiKit.ACCENT, UiKit.PANEL, 0.25f));
        UiKit.text(g, font, title, panelX + 22, textY, UiKit.TEXT);

        int tabX = panelX + 22 + font.width(title) + 18;
        for (Tab each : Tab.values()) {
            if (each == Tab.PERF && !AdminClient.canProfile) {
                continue;
            }
            Component label = Component.translatable(each.key);
            int tw = font.width(label) + 16;
            boolean active = each == tab;
            boolean hover = hovered(tabX, panelY, tw, HEADER_H);
            if (hover && !active) {
                g.fill(tabX, panelY + 4, tabX + tw, panelY + HEADER_H - 2, UiKit.ROW_HOVER);
            }
            UiKit.text(g, font, label, tabX + 8, textY, active ? UiKit.TEXT : hover ? UiKit.TEXT : UiKit.MUTED);
            if (active) {
                g.fill(tabX + 4, panelY + HEADER_H - 2, tabX + tw - 4, panelY + HEADER_H, UiKit.ACCENT);
            }
            nextId = "tab." + each.name().toLowerCase(Locale.ROOT);
            region(tabX, panelY, tw, HEADER_H, () -> switchTab(each), null);
            tabX += tw + 2;
        }

        int closeX = panelX + panelW - 22;
        int closeY = panelY + (HEADER_H - 16) / 2;
        boolean hover = hovered(closeX, closeY, 16, 16);
        if (hover) {
            UiKit.box(g, closeX, closeY, 16, 16, UiKit.BAD);
        }
        UiKit.centred(g, font, Component.literal("×"), closeX + 8, closeY + 4, hover ? UiKit.TEXT : UiKit.MUTED);
        region(closeX, closeY, 16, 16, this::onClose, null);
    }

    private void renderFooter(GuiGraphics g) {
        int y = panelY + panelH - FOOTER_H;
        g.fill(panelX + 1, y - 1, panelX + panelW - 1, y, UiKit.BORDER);
        Component hint = Component.translatable(switch (tab) {
            case SEARCH -> "chiseledblocks.admin.hint.search";
            case MAP -> "chiseledblocks.admin.hint.map";
            case PERF -> "chiseledblocks.admin.hint.perf";
        });
        UiKit.textFit(g, font, hint, panelX + 10, y + 4, panelW - 20, UiKit.FAINT);
    }

    // Search tab

    private void renderSearch(GuiGraphics g, int x, int y, int w, int h, float partialTick) {
        int segmentEnd = segmented(g, x, y, new Component[]{
                Component.translatable("chiseledblocks.admin.scope.blocks"),
                Component.translatable("chiseledblocks.admin.scope.chunks"),
                Component.translatable("chiseledblocks.admin.scope.loaded")}, scope.ordinal(), index -> {
            scope = ChiseledSearch.Scope.byOrdinal(index);
            search();
        });
        if (scope == ChiseledSearch.Scope.BLOCKS) {
            stepper(g, segmentEnd + 8, y, 96, Component.translatable("chiseledblocks.admin.radius.blocks", BLOCK_RADII[blockRadiusIndex]),
                    () -> {
                        blockRadiusIndex = Math.max(0, blockRadiusIndex - 1);
                        search();
                    }, () -> {
                        blockRadiusIndex = Math.min(BLOCK_RADII.length - 1, blockRadiusIndex + 1);
                        search();
                    });
        } else if (scope == ChiseledSearch.Scope.CHUNKS) {
            stepper(g, segmentEnd + 8, y, 96, Component.translatable("chiseledblocks.admin.radius.chunks", chunkRadius),
                    () -> {
                        chunkRadius = Math.max(0, chunkRadius - 1);
                        search();
                    }, () -> {
                        chunkRadius = Math.min(ChiseledSearch.MAX_RADIUS_CHUNKS, chunkRadius + 1);
                        search();
                    });
        }

        // Material filter and the search button.
        int rowY = y + 24;
        int buttonW = Math.max(84, font.width(Component.translatable("chiseledblocks.admin.search")) + 20);
        int fieldW = w - buttonW - 6;
        boolean fieldHover = hovered(x, rowY, fieldW, 18);
        UiKit.box(g, x, rowY, fieldW, 18, materialField.isFocused() ? UiKit.SURFACE_HOVER : fieldHover ? UiKit.SURFACE_HOVER : UiKit.SURFACE);
        if (materialField.isFocused()) {
            UiKit.outline(g, x, rowY, fieldW, 18, UiKit.ACCENT);
        }
        ResourceLocation typed = ResourceLocation.tryParse(material.trim().toLowerCase(Locale.ROOT));
        Block typedBlock = typed == null ? null : BuiltInRegistries.BLOCK.getOptional(typed).orElse(null);
        int fieldTextX = x + 6;
        if (typedBlock != null && typedBlock != Blocks.AIR && typedBlock.asItem() != Items.AIR) {
            renderIcon(g, typed, x + 2, rowY + 1);
            fieldTextX = x + 21;
        }
        materialField.setX(fieldTextX);
        materialField.setY(rowY + 5);
        materialField.setWidth(fieldW - (fieldTextX - x) - 18);
        materialField.render(g, mouseX, mouseY, partialTick);
        if (!material.isEmpty()) {
            int clearX = x + fieldW - 16;
            boolean clearHover = hovered(clearX, rowY + 2, 14, 14);
            UiKit.centred(g, font, Component.literal("×"), clearX + 7, rowY + 5, clearHover ? UiKit.TEXT : UiKit.FAINT);
            region(clearX, rowY + 2, 14, 14, () -> {
                materialField.setValue("");
                search();
            }, null);
        }
        nextId = "search";
        button(g, x + fieldW + 6, rowY, buttonW, 18, Component.translatable("chiseledblocks.admin.search"), Look.PRIMARY, true, this::search);

        // Suggestions open over the results: known now so the rows under them do not react.
        int dropY = rowY + 19;
        boolean dropdown = materialField.isFocused() && !suggestions.isEmpty();
        if (dropdown) {
            overlay = new int[]{x, dropY, fieldW, suggestions.size() * 18 + 2};
        }

        // Summary line.
        int summaryY = rowY + 26;
        AdminPayloads.FindResult result = AdminClient.search;
        if (result == null) {
            UiKit.text(g, font, Component.translatable("chiseledblocks.admin.searching"), x, summaryY, UiKit.MUTED);
        } else if (result.unknownMaterial()) {
            UiKit.text(g, font, Component.translatable("chiseledblocks.admin.material.unknown", material.trim()), x, summaryY, UiKit.BAD);
        } else {
            UiKit.text(g, font, Component.translatable("chiseledblocks.admin.found", result.total(), result.chunks()), x, summaryY, UiKit.TEXT);
            if (result.total() > result.hits().size()) {
                UiKit.rightAligned(g, font, Component.translatable("chiseledblocks.admin.shown", result.hits().size()).getString(),
                        x + w, summaryY, UiKit.MUTED);
            }
        }

        int tableY = summaryY + 14;
        int tableH = y + h - tableY;
        List<AdminPayloads.Hit> hits = result == null || result.unknownMaterial() ? List.of() : result.hits();
        ResourceLocation dimension = result == null ? null : result.dimension();
        int iconsW = 4 * 17 + 6;
        int[] widths = {iconsW, -1, 64, 52, 40};
        Component[] titles = {
                Component.translatable("chiseledblocks.admin.col.materials"),
                Component.translatable("chiseledblocks.admin.col.position"),
                Component.translatable("chiseledblocks.admin.col.distance"),
                Component.translatable("chiseledblocks.admin.col.pixels"),
                Component.empty()};
        int[] cols = layout(x, w, widths);
        table(g, TABLE_SEARCH, x, tableY, w, tableH, titles, cols, widths, hits.size(), (index, rowX, rowY2, rowW, hover) -> {
            AdminPayloads.Hit hit = hits.get(index);
            int textY = rowY2 + (ROW_H - 8) / 2;
            renderMaterials(g, hit.materials(), cols[0] + 2, rowY2 + 2, 4);
            BlockPos pos = hit.pos();
            UiKit.text(g, font, pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), cols[1] + 4, textY, UiKit.TEXT);
            UiKit.text(g, font, String.format(Locale.ROOT, "%.0f m", hit.distance()), cols[2] + 4, textY, UiKit.MUTED);
            UiKit.text(g, font, String.valueOf(hit.bits()), cols[3] + 4, textY, UiKit.MUTED);
            nextId = "tp.search." + index;
            button(g, cols[4] + 2, rowY2 + 3, 34, ROW_H - 6, Component.translatable("chiseledblocks.admin.tp"), Look.GHOST, AdminClient.canTeleport,
                    () -> teleport(dimension, pos));
        }, Component.translatable(result == null ? "chiseledblocks.admin.searching" : "chiseledblocks.admin.none"));

        if (dropdown) {
            renderSuggestions(g, x, dropY, fieldW);
        }
    }

    private void renderSuggestions(GuiGraphics g, int x, int y, int w) {
        int[] blocked = overlay;
        overlay = null;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        UiKit.box(g, x - 1, y - 1, w + 2, suggestions.size() * 18 + 4, UiKit.BORDER);
        UiKit.box(g, x, y, w, suggestions.size() * 18 + 2, UiKit.HEADER);
        for (int i = 0; i < suggestions.size(); i++) {
            ResourceLocation id = suggestions.get(i);
            int rowY = y + 1 + i * 18;
            boolean hover = hovered(x, rowY, w, 18);
            if (hover) {
                g.fill(x + 1, rowY, x + w - 1, rowY + 18, UiKit.SURFACE_HOVER);
            }
            renderIcon(g, id, x + 3, rowY + 1);
            UiKit.textFit(g, font, BuiltInRegistries.BLOCK.get(id).getName(), x + 23, rowY + 5, w / 2, UiKit.TEXT);
            UiKit.rightAligned(g, font, id.toString(), x + w - 6, rowY + 5, UiKit.FAINT);
            region(x, rowY, w, 18, () -> {
                materialField.setValue(id.toString());
                materialField.moveCursorToEnd(false);
                setFocused(null);
                materialField.setFocused(false);
                search();
            }, null);
        }
        g.pose().popPose();
        overlay = blocked;
    }

    private void updateSuggestions() {
        String text = material.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            suggestions = List.of();
            return;
        }
        List<ResourceLocation> matches = new ArrayList<>();
        for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (block == Blocks.AIR || block.asItem() == Items.AIR) {
                continue;
            }
            String name = block.getName().getString().toLowerCase(Locale.ROOT);
            if (id.toString().contains(text) || name.contains(text)) {
                matches.add(id);
            }
        }
        matches.sort(Comparator.comparingInt((ResourceLocation id) -> id.getPath().startsWith(text) ? 0 : 1)
                .thenComparingInt(id -> id.getPath().length()));
        if (matches.size() == 1 && matches.get(0).toString().equals(text)) {
            matches.clear();
        }
        suggestions = matches.subList(0, Math.min(6, matches.size()));
    }

    // Map tab

    private void renderMap(GuiGraphics g, int x, int y, int w, int h) {
        UiKit.text(g, font, Component.translatable("chiseledblocks.admin.map.radius"), x, y + 5, UiKit.MUTED);
        int labelW = font.width(Component.translatable("chiseledblocks.admin.map.radius")) + 8;
        stepper(g, x + labelW, y, 96, Component.translatable("chiseledblocks.admin.radius.chunks", mapRadius),
                () -> {
                    mapRadius = Math.max(1, mapRadius - 1);
                    requestMap();
                }, () -> {
                    mapRadius = Math.min(ChiseledSearch.MAX_MAP_RADIUS, mapRadius + 1);
                    requestMap();
                });
        button(g, x + labelW + 104, y, font.width(Component.translatable("chiseledblocks.admin.refresh")) + 20, 18,
                Component.translatable("chiseledblocks.admin.refresh"), Look.SECONDARY, true, this::requestMap);

        int areaY = y + 26;
        int areaH = h - 26;
        int size = Math.min(areaH, (int) (w * 0.6));
        AdminPayloads.MapResult map = AdminClient.map;
        int sideX = x + size + 12;
        int sideW = x + w - sideX;
        if (map == null) {
            UiKit.box(g, x, areaY, size, size, UiKit.SURFACE);
            UiKit.centred(g, font, Component.translatable("chiseledblocks.admin.loading"), x + size / 2, areaY + size / 2 - 4, UiKit.MUTED);
            return;
        }
        int side = map.side();
        int cell = Math.max(2, size / side);
        int gridSize = cell * side;
        int gx = x + (size - gridSize) / 2;
        int gy = areaY + (size - gridSize) / 2;
        boolean gaps = cell >= 6;
        UiKit.box(g, gx - 3, gy - 3, gridSize + 6, gridSize + 6, UiKit.HEADER);
        int loadedChunks = 0;
        int hoveredCell = -1;
        for (int row = 0; row < side; row++) {
            for (int column = 0; column < side; column++) {
                int index = row * side + column;
                int count = map.counts()[index];
                boolean loaded = map.loaded()[index];
                loadedChunks += loaded ? 1 : 0;
                int colour = !loaded ? 0xFF181C22 : count == 0 ? 0xFF2C333D : UiKit.heat(count);
                int cx = gx + column * cell;
                int cy = gy + row * cell;
                int inner = gaps ? cell - 1 : cell;
                g.fill(cx, cy, cx + inner, cy + inner, colour);
                int chunkX = map.centreX() - map.radius() + column;
                int chunkZ = map.centreZ() - map.radius() + row;
                if (hovered(cx, cy, cell, cell)) {
                    hoveredCell = index;
                    tooltip = List.of(
                            Component.translatable("chiseledblocks.admin.map.chunk", chunkX, chunkZ).withColor(UiKit.TEXT),
                            Component.translatable(loaded ? "chiseledblocks.admin.map.loaded" : "chiseledblocks.admin.map.unloaded")
                                    .withColor(loaded ? UiKit.GOOD : UiKit.MUTED),
                            Component.translatable("chiseledblocks.admin.map.count", count).withColor(count > 0 ? UiKit.heat(count) : UiKit.MUTED));
                }
                nextId = "cell." + chunkX + "." + chunkZ;
                region(cx, cy, cell, cell, () -> selectChunk(chunkX, chunkZ), null);
            }
        }
        if (hoveredCell >= 0) {
            int cx = gx + (hoveredCell % side) * cell;
            int cy = gy + (hoveredCell / side) * cell;
            UiKit.outline(g, cx - 1, cy - 1, cell + 1, cell + 1, 0xCCFFFFFF);
        }
        if (selected) {
            int column = selectedX - (map.centreX() - map.radius());
            int row = selectedZ - (map.centreZ() - map.radius());
            if (column >= 0 && column < side && row >= 0 && row < side) {
                UiKit.outline(g, gx + column * cell - 1, gy + row * cell - 1, cell + 1, cell + 1, UiKit.ACCENT);
            }
        }
        renderPlayerMarker(g, map, gx, gy, cell);
        UiKit.centred(g, font, Component.translatable("chiseledblocks.admin.map.north"), gx + gridSize / 2, gy - 12, UiKit.FAINT);

        if (sideW < 80) {
            return;
        }
        if (selected) {
            renderChunkDetail(g, map, sideX, areaY, sideW, areaH);
        } else {
            renderMapOverview(g, map, loadedChunks, sideX, areaY, sideW);
        }
    }

    private void renderPlayerMarker(GuiGraphics g, AdminPayloads.MapResult map, int gx, int gy, int cell) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        double fx = (player.getX() / 16.0 - (map.centreX() - map.radius())) * cell;
        double fz = (player.getZ() / 16.0 - (map.centreZ() - map.radius())) * cell;
        int px = gx + Mth.floor(fx);
        int pz = gy + Mth.floor(fz);
        if (px < gx || pz < gy || px >= gx + map.side() * cell || pz >= gy + map.side() * cell) {
            return;
        }
        // Facing: a short line of dots in the look direction.
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double dx = -Mth.sin(yaw);
        double dz = Mth.cos(yaw);
        for (int i = 2; i <= 6; i++) {
            int lx = px + (int) Math.round(dx * i);
            int lz = pz + (int) Math.round(dz * i);
            g.fill(lx, lz, lx + 1, lz + 1, UiKit.ACCENT);
        }
        g.fill(px - 2, pz - 2, px + 3, pz + 3, 0xFF0B1A22);
        g.fill(px - 1, pz - 1, px + 2, pz + 2, UiKit.ACCENT);
    }

    private void renderMapOverview(GuiGraphics g, AdminPayloads.MapResult map, int loadedChunks, int x, int y, int w) {
        int total = 0;
        int withBlocks = 0;
        for (int count : map.counts()) {
            total += count;
            withBlocks += count > 0 ? 1 : 0;
        }
        int cardW = (w - 6) / 2;
        card(g, x, y, cardW, Component.translatable("chiseledblocks.admin.map.card.loaded"), String.valueOf(loadedChunks), UiKit.TEXT);
        card(g, x + cardW + 6, y, cardW, Component.translatable("chiseledblocks.admin.map.card.chiseled"),
                total + " / " + withBlocks, total > 0 ? UiKit.ACCENT : UiKit.TEXT);

        int legendY = y + 42;
        UiKit.text(g, font, Component.translatable("chiseledblocks.admin.map.legend"), x, legendY, UiKit.MUTED);
        legendY += 14;
        legend(g, x, legendY, UiKit.ACCENT, Component.translatable("chiseledblocks.admin.map.you"));
        legend(g, x, legendY + 14, 0xFF181C22, Component.translatable("chiseledblocks.admin.map.unloaded"));
        legend(g, x, legendY + 28, 0xFF2C333D, Component.translatable("chiseledblocks.admin.map.empty"));
        legend(g, x, legendY + 42, UiKit.heat(1), Component.translatable("chiseledblocks.admin.map.few"));
        legend(g, x, legendY + 56, UiKit.heat(16), Component.translatable("chiseledblocks.admin.map.some"));
        legend(g, x, legendY + 70, UiKit.heat(256), Component.translatable("chiseledblocks.admin.map.many"));
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.translatable("chiseledblocks.admin.map.help"), w);
        int helpY = legendY + 92;
        for (net.minecraft.util.FormattedCharSequence line : lines) {
            g.drawString(font, line, x, helpY, UiKit.FAINT, false);
            helpY += 10;
        }
    }

    private void legend(GuiGraphics g, int x, int y, int colour, Component label) {
        UiKit.box(g, x, y, 10, 10, UiKit.BORDER);
        g.fill(x + 1, y + 1, x + 9, y + 9, colour);
        UiKit.text(g, font, label, x + 16, y + 1, UiKit.TEXT);
    }

    private void renderChunkDetail(GuiGraphics g, AdminPayloads.MapResult map, int x, int y, int w, int h) {
        UiKit.text(g, font, Component.translatable("chiseledblocks.admin.map.chunk", selectedX, selectedZ), x, y + 2, UiKit.TEXT);
        int closeX = x + w - 14;
        boolean closeHover = hovered(closeX, y, 14, 12);
        UiKit.centred(g, font, Component.literal("×"), closeX + 7, y + 2, closeHover ? UiKit.TEXT : UiKit.FAINT);
        region(closeX, y, 14, 12, () -> selected = false, null);
        UiKit.text(g, font, Component.translatable("chiseledblocks.admin.map.blocks", selectedX * 16, selectedZ * 16,
                selectedX * 16 + 15, selectedZ * 16 + 15), x, y + 14, UiKit.FAINT);

        AdminPayloads.FindResult result = AdminClient.chunk;
        List<AdminPayloads.Hit> hits = result == null ? List.of() : result.hits();
        ResourceLocation dimension = result == null ? null : result.dimension();
        int[] widths = {2 * 17 + 4, -1, 40};
        int[] cols = layout(x, w, widths);
        Component[] titles = {Component.empty(), Component.translatable("chiseledblocks.admin.col.position"), Component.empty()};
        table(g, TABLE_CHUNK, x, y + 28, w, h - 28, titles, cols, widths, hits.size(), (index, rowX, rowY, rowW, hover) -> {
            AdminPayloads.Hit hit = hits.get(index);
            renderMaterials(g, hit.materials(), cols[0] + 2, rowY + 2, 2);
            BlockPos pos = hit.pos();
            UiKit.textFit(g, font, pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), cols[1] + 4, rowY + 6, cols[2] - cols[1] - 6,
                    UiKit.TEXT);
            nextId = "tp.chunk." + index;
            button(g, cols[2] + 2, rowY + 3, 34, ROW_H - 6, Component.translatable("chiseledblocks.admin.tp"), Look.GHOST, AdminClient.canTeleport,
                    () -> teleport(dimension, pos));
        }, Component.translatable(result == null ? "chiseledblocks.admin.loading" : "chiseledblocks.admin.map.none"));
    }

    // Performance tab

    private void renderPerf(GuiGraphics g, int x, int y, int w, int h) {
        UiKit.text(g, font, Component.translatable("chiseledblocks.admin.perf.duration"), x, y + 5, UiKit.MUTED);
        int labelW = font.width(Component.translatable("chiseledblocks.admin.perf.duration")) + 8;
        boolean running = AdminClient.perfRunning;
        stepper(g, x + labelW, y, 80, Component.translatable("chiseledblocks.admin.seconds", PERF_SECONDS[perfSecondsIndex]),
                () -> perfSecondsIndex = Math.max(0, perfSecondsIndex - 1),
                () -> perfSecondsIndex = Math.min(PERF_SECONDS.length - 1, perfSecondsIndex + 1));
        Component action = Component.translatable(running ? "chiseledblocks.admin.perf.stop" : "chiseledblocks.admin.perf.start");
        int buttonX = x + labelW + 88;
        int buttonW = font.width(action) + 24;
        nextId = "perf.toggle";
        button(g, buttonX, y, buttonW, 18, action, running ? Look.DANGER : Look.PRIMARY, true, () -> {
            AdminClient.perfBusySeconds = -1;
            PacketDistributor.sendToServer(new AdminPayloads.PerfRequest(running ? 0 : PERF_SECONDS[perfSecondsIndex]));
        });

        // Progress of the running window.
        int barX = buttonX + buttonW + 8;
        int barW = x + w - barX;
        UiKit.box(g, barX, y, barW, 18, UiKit.SURFACE);
        Component state;
        int stateColour = UiKit.MUTED;
        if (running) {
            float progress = AdminClient.perfProgress();
            UiKit.box(g, barX, y, Math.max(2, (int) (barW * progress)), 18, UiKit.mix(UiKit.ACCENT, UiKit.SURFACE, 0.55f));
            int total = AdminClient.perfTotalTicks / 20;
            state = Component.translatable("chiseledblocks.admin.perf.running", Math.min(total, (int) (progress * total)), total);
            stateColour = UiKit.TEXT;
        } else if (AdminClient.perfBusySeconds >= 0) {
            state = Component.translatable("chiseledblocks.admin.perf.busy", AdminClient.perfBusySeconds);
            stateColour = UiKit.WARN;
        } else {
            state = Component.translatable("chiseledblocks.admin.perf.ready");
        }
        UiKit.textFit(g, font, state, barX + 8, y + 5, barW - 16, stateColour);

        AdminPayloads.PerfReport report = AdminClient.perfReport;
        int cardsY = y + 26;
        int cardW = (w - 12) / 3;
        if (report != null) {
            double total = report.perTick(report.totalNanos());
            card(g, x, cardsY, cardW, Component.translatable("chiseledblocks.admin.perf.card.total"),
                    Component.translatable("chiseledblocks.admin.perf.micros", micros(total)).getString(), UiKit.cost(total));
            card(g, x + cardW + 6, cardsY, cardW, Component.translatable("chiseledblocks.admin.perf.card.blocks"),
                    String.valueOf(report.blocks()), UiKit.TEXT);
            card(g, x + 2 * (cardW + 6), cardsY, cardW, Component.translatable("chiseledblocks.admin.perf.card.window"),
                    Component.translatable("chiseledblocks.admin.perf.ticks", report.ticks(),
                            String.format(Locale.ROOT, "%.1f", report.ticks() / 20.0)).getString(), UiKit.TEXT);
        } else {
            for (int i = 0; i < 3; i++) {
                card(g, x + i * (cardW + 6), cardsY, cardW, Component.translatable(switch (i) {
                    case 0 -> "chiseledblocks.admin.perf.card.total";
                    case 1 -> "chiseledblocks.admin.perf.card.blocks";
                    default -> "chiseledblocks.admin.perf.card.window";
                }), "-", UiKit.FAINT);
            }
        }

        int tableY = cardsY + 38;
        List<AdminPayloads.PerfRow> rows = report == null ? List.of() : report.rows();
        int kinds = 5;
        int kindW = Math.max(40, Math.min(58, (w - 180) / kinds));
        int[] widths = {-1, 74, kindW, kindW, kindW, kindW, kindW, 40};
        Component[] titles = {
                Component.translatable("chiseledblocks.admin.col.position"),
                Component.translatable("chiseledblocks.admin.col.total"),
                Component.translatable("chiseledblocks.admin.kind.chisel"),
                Component.translatable("chiseledblocks.admin.kind.apply"),
                Component.translatable("chiseledblocks.admin.kind.shape"),
                Component.translatable("chiseledblocks.admin.kind.save"),
                Component.translatable("chiseledblocks.admin.kind.load"),
                Component.empty()};
        int[] cols = layout(x, w, widths);
        long max = rows.isEmpty() ? 1 : Math.max(1, rows.get(0).totalNanos());
        table(g, TABLE_PERF, x, tableY, w, y + h - tableY, titles, cols, widths, rows.size(), (index, rowX, rowY, rowW, hover) -> {
            AdminPayloads.PerfRow row = rows.get(index);
            int textY = rowY + (ROW_H - 8) / 2;
            BlockPos pos = row.pos();
            UiKit.textFit(g, font, pos.getX() + ", " + pos.getY() + ", " + pos.getZ(), cols[0] + 4, textY, cols[1] - cols[0] - 6,
                    UiKit.TEXT);
            double total = report.perTick(row.totalNanos());
            int fillW = Math.max(1, (int) ((cols[2] - cols[1] - 6) * (row.totalNanos() / (double) max)));
            g.fill(cols[1] + 2, rowY + 4, cols[1] + 2 + fillW, rowY + ROW_H - 4, UiKit.mix(UiKit.cost(total), UiKit.PANEL, 0.6f));
            UiKit.text(g, font, micros(total), cols[1] + 6, textY, UiKit.cost(total));
            for (int kind = 0; kind < kinds && kind < row.nanos().length; kind++) {
                int cx = cols[2 + kind];
                if (row.calls()[kind] == 0) {
                    UiKit.text(g, font, "-", cx + 4, textY, UiKit.FAINT);
                    continue;
                }
                double value = report.perTick(row.nanos()[kind]);
                UiKit.text(g, font, micros(value), cx + 4, textY, UiKit.MUTED);
                if (hovered(cx, rowY, widths[2 + kind], ROW_H)) {
                    tooltip = List.of(Component.translatable("chiseledblocks.admin.kind." + kindKey(kind)).withColor(UiKit.TEXT),
                            Component.translatable("chiseledblocks.admin.perf.detail", micros(value), row.calls()[kind])
                                    .withColor(UiKit.MUTED));
                }
            }
            nextId = "tp.perf." + index;
            button(g, cols[7] + 2, rowY + 3, 34, ROW_H - 6, Component.translatable("chiseledblocks.admin.tp"), Look.GHOST, AdminClient.canTeleport,
                    () -> teleport(row.dimension(), pos));
        }, Component.translatable(report == null ? "chiseledblocks.admin.perf.empty" : "chiseledblocks.admin.perf.nothing"));
    }

    private static String kindKey(int kind) {
        return switch (kind) {
            case 0 -> "chisel";
            case 1 -> "apply";
            case 2 -> "shape";
            case 3 -> "save";
            default -> "load";
        };
    }

    private static String micros(double value) {
        return String.format(Locale.ROOT, value < 10 ? "%.2f" : value < 1000 ? "%.1f" : "%.0f", value);
    }

    // Widgets

    private void card(GuiGraphics g, int x, int y, int w, Component label, String value, int valueColour) {
        UiKit.box(g, x, y, w, 32, UiKit.SURFACE);
        UiKit.textFit(g, font, label, x + 8, y + 5, w - 16, UiKit.MUTED);
        UiKit.textFit(g, font, value, x + 8, y + 18, w - 16, valueColour);
    }

    private void button(GuiGraphics g, int x, int y, int w, int h, Component label, Look look, boolean enabled, Runnable action) {
        boolean hover = enabled && hovered(x, y, w, h);
        int background;
        int text;
        switch (look) {
            case PRIMARY -> {
                background = hover ? UiKit.ACCENT_HOVER : UiKit.ACCENT;
                text = UiKit.ON_ACCENT;
            }
            case DANGER -> {
                background = hover ? UiKit.BAD_HOVER : UiKit.BAD;
                text = UiKit.TEXT;
            }
            case SECONDARY -> {
                background = hover ? UiKit.SURFACE_HOVER : UiKit.SURFACE;
                text = UiKit.TEXT;
            }
            default -> {
                background = hover ? UiKit.mix(UiKit.ACCENT, UiKit.PANEL, 0.7f) : 0;
                text = hover ? UiKit.ACCENT_HOVER : UiKit.ACCENT;
            }
        }
        if (!enabled) {
            background = UiKit.SURFACE;
            text = UiKit.FAINT;
        }
        if (background != 0) {
            UiKit.box(g, x, y, w, h, background);
        }
        if (look == Look.GHOST && !hover) {
            UiKit.outline(g, x, y, w, h, UiKit.mix(UiKit.ACCENT, UiKit.PANEL, 0.6f));
        }
        UiKit.centred(g, font, label, x + w / 2, y + (h - 8) / 2, text);
        if (enabled) {
            region(x, y, w, h, action, null);
        } else {
            nextId = null;
        }
    }

    /**
     * Centre of a named clickable area as drawn in the last frame, or of the material field
     * ({@code "material"}); null when not on screen. Used by the playtest to click like a player.
     */
    public @Nullable int[] locate(String id) {
        if (id.equals("material")) {
            return tab == Tab.SEARCH ? new int[]{materialField.getX() + 10, materialField.getY() + 4} : null;
        }
        for (Hotspot hotspot : hotspots) {
            if (id.equals(hotspot.id())) {
                return new int[]{hotspot.x() + hotspot.w() / 2, hotspot.y() + hotspot.h() / 2};
            }
        }
        return null;
    }

    /** Joined toggle buttons; returns the right edge. */
    private int segmented(GuiGraphics g, int x, int y, Component[] labels, int active, java.util.function.IntConsumer select) {
        int total = 0;
        for (Component label : labels) {
            total += font.width(label) + 18;
        }
        UiKit.box(g, x, y, total + 4, 18, UiKit.SURFACE);
        int sx = x + 2;
        for (int i = 0; i < labels.length; i++) {
            int sw = font.width(labels[i]) + 18;
            boolean hover = hovered(sx, y + 2, sw, 14);
            if (i == active) {
                UiKit.box(g, sx, y + 2, sw, 14, UiKit.ACCENT);
            } else if (hover) {
                UiKit.box(g, sx, y + 2, sw, 14, UiKit.SURFACE_HOVER);
            }
            UiKit.centred(g, font, labels[i], sx + sw / 2, y + 5, i == active ? UiKit.ON_ACCENT : hover ? UiKit.TEXT : UiKit.MUTED);
            int index = i;
            nextId = "segment." + i;
            region(sx, y + 2, sw, 14, () -> select.accept(index), null);
            sx += sw;
        }
        return x + total + 4;
    }

    /** A value between minus and plus buttons; the mouse wheel over it changes it too. */
    private void stepper(GuiGraphics g, int x, int y, int w, Component value, Runnable minus, Runnable plus) {
        UiKit.box(g, x, y, w, 18, UiKit.SURFACE);
        for (int side = 0; side < 2; side++) {
            int bx = side == 0 ? x : x + w - 18;
            boolean hover = hovered(bx, y, 18, 18);
            if (hover) {
                UiKit.box(g, bx, y, 18, 18, UiKit.SURFACE_HOVER);
            }
            UiKit.centred(g, font, Component.literal(side == 0 ? "-" : "+"), bx + 9, y + 5, hover ? UiKit.ACCENT : UiKit.MUTED);
            region(bx, y, 18, 18, side == 0 ? minus : plus, null);
        }
        UiKit.centred(g, font, value, x + w / 2, y + 5, UiKit.TEXT);
        region(x + 18, y, w - 36, 18, null, delta -> (delta > 0 ? plus : minus).run());
    }

    private void renderMaterials(GuiGraphics g, List<ResourceLocation> materials, int x, int y, int max) {
        for (int i = 0; i < Math.min(max, materials.size()); i++) {
            if (i == max - 1 && materials.size() > max) {
                UiKit.text(g, font, "+" + (materials.size() - max + 1), x + i * 17 + 3, y + 4, UiKit.MUTED);
                break;
            }
            renderIcon(g, materials.get(i), x + i * 17, y);
        }
        if (hovered(x, y, max * 17, 16) && !materials.isEmpty()) {
            List<Component> names = new ArrayList<>();
            for (ResourceLocation id : materials) {
                names.add(BuiltInRegistries.BLOCK.get(id).getName().withColor(UiKit.TEXT));
            }
            tooltip = names;
        }
    }

    private void renderIcon(GuiGraphics g, ResourceLocation id, int x, int y) {
        ItemStack stack = icons.computeIfAbsent(id, key -> new ItemStack(BuiltInRegistries.BLOCK.get(key)));
        if (!stack.isEmpty()) {
            g.renderItem(stack, x, y);
        }
    }

    // Tables

    private interface RowPainter {
        void paint(int index, int x, int y, int w, boolean hovered);
    }

    /** Column left edges for the given widths, -1 meaning "the rest". */
    private static int[] layout(int x, int w, int[] widths) {
        int usable = w - 6;
        int fixed = 0;
        for (int width : widths) {
            fixed += Math.max(0, width);
        }
        int[] starts = new int[widths.length];
        int cx = x;
        for (int i = 0; i < widths.length; i++) {
            starts[i] = cx;
            int width = widths[i] < 0 ? Math.max(40, usable - fixed) : widths[i];
            widths[i] = width;
            cx += width;
        }
        return starts;
    }

    private void table(GuiGraphics g, int id, int x, int y, int w, int h, Component[] titles, int[] cols, int[] widths,
            int rows, RowPainter painter, Component empty) {
        UiKit.box(g, x, y, w, TABLE_HEADER_H, UiKit.SURFACE);
        for (int i = 0; i < titles.length; i++) {
            UiKit.textFit(g, font, titles[i], cols[i] + 4, y + 3, widths[i] - 6, UiKit.MUTED);
        }
        int bodyY = y + TABLE_HEADER_H + 2;
        int bodyH = h - TABLE_HEADER_H - 2;
        if (bodyH <= 0) {
            return;
        }
        if (rows == 0) {
            UiKit.centred(g, font, empty, x + w / 2, bodyY + Math.min(bodyH / 2, 30), UiKit.FAINT);
            scrollTracks[id] = null;
            return;
        }
        int content = rows * ROW_H;
        double max = Math.max(0, content - bodyH);
        scroll[id] = Mth.clamp(scroll[id], 0, max);
        clip = new int[]{x, bodyY, w, bodyH};
        g.enableScissor(x, bodyY, x + w, bodyY + bodyH);
        int first = (int) (scroll[id] / ROW_H);
        for (int i = first; i < rows; i++) {
            int rowY = bodyY + i * ROW_H - (int) scroll[id];
            if (rowY >= bodyY + bodyH) {
                break;
            }
            boolean hover = hovered(x, rowY, w - 6, ROW_H);
            if (i % 2 == 1) {
                g.fill(x, rowY, x + w - 6, rowY + ROW_H, UiKit.ROW_ALT);
            }
            if (hover) {
                g.fill(x, rowY, x + w - 6, rowY + ROW_H, UiKit.ROW_HOVER);
            }
            painter.paint(i, x, rowY, w - 6, hover);
        }
        g.disableScissor();
        clip = null;
        region(x, bodyY, w, bodyH, null, delta -> scroll[id] = Mth.clamp(scroll[id] - delta * ROW_H * 2, 0, max));
        if (max > 0) {
            int trackX = x + w - 4;
            int thumbH = Math.max(14, bodyH * bodyH / content);
            int thumbY = bodyY + (int) ((bodyH - thumbH) * (scroll[id] / max));
            g.fill(trackX, bodyY, trackX + 3, bodyY + bodyH, UiKit.SURFACE);
            boolean hover = hovered(trackX - 2, bodyY, 7, bodyH) || dragTable == id;
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, hover ? UiKit.ACCENT : UiKit.FAINT);
            scrollTracks[id] = new int[]{bodyY, bodyH, thumbH, (int) max};
            region(trackX - 2, bodyY, 7, bodyH, () -> {
                dragTable = id;
                dragTo(id, mouseY);
            }, null);
        } else {
            scrollTracks[id] = null;
        }
    }

    private void dragTo(int id, double mouse) {
        int[] track = scrollTracks[id];
        if (track == null) {
            return;
        }
        double t = (mouse - track[0] - track[2] / 2.0) / Math.max(1, track[1] - track[2]);
        scroll[id] = Mth.clamp(t, 0, 1) * track[3];
    }

    // Input

    private boolean hovered(int x, int y, int w, int h) {
        if (mouseX < x || mouseX >= x + w || mouseY < y || mouseY >= y + h) {
            return false;
        }
        if (overlay != null && mouseX >= overlay[0] && mouseX < overlay[0] + overlay[2]
                && mouseY >= overlay[1] && mouseY < overlay[1] + overlay[3]) {
            return false;
        }
        return clip == null || (mouseX >= clip[0] && mouseX < clip[0] + clip[2] && mouseY >= clip[1] && mouseY < clip[1] + clip[3]);
    }

    /** Registers a clickable or scrollable area, cut to the table being drawn. */
    private void region(int x, int y, int w, int h, @Nullable Runnable click, @Nullable DoubleConsumer scroll) {
        if (clip != null) {
            int x1 = Math.max(x, clip[0]);
            int y1 = Math.max(y, clip[1]);
            int x2 = Math.min(x + w, clip[0] + clip[2]);
            int y2 = Math.min(y + h, clip[1] + clip[3]);
            if (x2 <= x1 || y2 <= y1) {
                nextId = null;
                return;
            }
            x = x1;
            y = y1;
            w = x2 - x1;
            h = y2 - y1;
        }
        if (overlay != null && click != null && x >= overlay[0] && x + w <= overlay[0] + overlay[2]
                && y >= overlay[1] && y + h <= overlay[1] + overlay[3]) {
            nextId = null;
            return;
        }
        hotspots.add(new Hotspot(x, y, w, h, click, scroll, nextId));
        nextId = null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            for (int i = hotspots.size() - 1; i >= 0; i--) {
                Hotspot hotspot = hotspots.get(i);
                if (hotspot.click() != null && hotspot.contains(mouseX, mouseY)) {
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    hotspot.click().run();
                    return true;
                }
            }
        }
        if (tab == Tab.SEARCH && materialField.isMouseOver(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        materialField.setFocused(false);
        setFocused(null);
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragTable >= 0) {
            dragTo(dragTable, mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragTable = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        for (int i = hotspots.size() - 1; i >= 0; i--) {
            Hotspot hotspot = hotspots.get(i);
            if (hotspot.scroll() != null && hotspot.contains(mouseX, mouseY)) {
                hotspot.scroll().accept(scrollY);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (tab == Tab.SEARCH && materialField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (!suggestions.isEmpty() && !material.contains(":")) {
                    materialField.setValue(suggestions.get(0).toString());
                }
                materialField.setFocused(false);
                setFocused(null);
                search();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                materialField.setFocused(false);
                setFocused(null);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
