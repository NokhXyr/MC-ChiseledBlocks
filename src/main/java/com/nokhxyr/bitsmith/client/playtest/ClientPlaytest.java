/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client.playtest;

import com.mojang.blaze3d.platform.InputConstants;
import com.nokhxyr.bitsmith.Bitsmith;
import com.nokhxyr.bitsmith.block.CarvedBlockEntity;
import com.nokhxyr.bitsmith.client.BitsmithKeys;
import com.nokhxyr.bitsmith.client.RadialMenuScreen;
import com.nokhxyr.bitsmith.client.TapeMeasureClient;
import com.nokhxyr.bitsmith.core.edit.ChiselMode;
import com.nokhxyr.bitsmith.core.edit.Symmetry;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.core.voxel.GridTransforms;
import com.nokhxyr.bitsmith.edit.PlayerSymmetry;
import com.nokhxyr.bitsmith.item.BitBagItem;
import com.nokhxyr.bitsmith.item.BitItem;
import com.nokhxyr.bitsmith.item.CarvedBlockItem;
import com.nokhxyr.bitsmith.item.ChiselItem;
import com.nokhxyr.bitsmith.item.PatternItem;
import com.nokhxyr.bitsmith.item.SawItem;
import com.nokhxyr.bitsmith.item.WrenchItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Plays Bitsmith like a player, for the {@code runClientTest} run only (system property
 * {@code bitsmith.clientTest}). Creates a fresh flat world, then drives the game through its own
 * input handlers: key presses through the keyboard handler, cursor moves and clicks in menus
 * through the mouse handler, attack and use through their key bindings. Each step checks the
 * result as the client sees it, screenshots go to {@code screenshots/}, a report to
 * {@code playtest-report.txt}, and the game quits when done.
 */
public final class ClientPlaytest {
    private static final String MODE = System.getProperty("bitsmith.playtest", "main");
    private static final String WORLD = MODE.startsWith("cnb") ? ChiselsAndBitsPlaytest.WORLD : "bitsmith-" + MODE;
    private static final int WORLD_SETTLE_TICKS = 60;
    /** Hard stop: generous for the long runs (bench, soak), 6 minutes for the others. */
    private static final int TIMEOUT_TICKS = 20 * switch (System.getProperty("bitsmith.playtest", "main")) {
        case "bench" -> 900;
        case "soak" -> Integer.getInteger("bitsmith.soak.minutes", 10) * 75 + 300;
        default -> 360;
    };
    /** Extra ticks a failing check is retried for, in case the game runs slow. */
    private static final int GRACE_TICKS = 40;

    private static final BlockPos PLAYER = new BlockPos(0, -60, 0);
    private static final BlockPos A = new BlockPos(0, -60, 3);
    private static final BlockPos B = new BlockPos(1, -60, 3);
    private static final BlockPos C = new BlockPos(-1, -60, 3);
    private static final BlockPos E = new BlockPos(-2, -60, 3);
    private static final BlockPos F = new BlockPos(2, -60, 3);
    private static final BlockPos PIT = new BlockPos(5, -61, 4);

    private record Step(String name, Runnable action, int waitTicks, BooleanSupplier check, String screenshot) {
    }

    private final Minecraft mc = Minecraft.getInstance();
    private final List<Step> steps = new ArrayList<>();
    private final List<String> report = new ArrayList<>();
    private boolean worldRequested;
    private int settled;
    private int index;
    private int waitLeft;
    private boolean actionDone;
    private int graceLeft;
    private int ticks;
    private int failures;
    private boolean finished;
    /** What the last check saw, reported when it fails. */
    private String observed;
    private BitGrid beforeWrench;
    private int sawBefore;
    private BitGrid movedGrid;

    public ClientPlaytest() {
        switch (MODE) {
            case "cnbSource" -> ChiselsAndBitsPlaytest.sourceScript(this);
            case "cnbMigrate" -> ChiselsAndBitsPlaytest.migrateScript(this);
            case "bench" -> BenchPlaytest.script(this);
            case "soak" -> SoakPlaytest.script(this);
            case "admin" -> AdminPlaytest.script(this);
            default -> script();
        }
    }

    // Input, through the game's own handlers

    private long window() {
        return mc.getWindow().getWindow();
    }

    void key(int glfwKey, boolean press) {
        mc.keyboardHandler.keyPress(window(), glfwKey, GLFW.glfwGetKeyScancode(glfwKey),
                press ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
    }

    void tap(int glfwKey) {
        key(glfwKey, true);
        key(glfwKey, false);
    }

    void hotbar(int slot) {
        tap(GLFW.GLFW_KEY_1 + slot);
    }

    void attack() {
        KeyMapping.click(mc.options.keyAttack.getKey());
    }

    void use() {
        KeyMapping.click(mc.options.keyUse.getKey());
    }

    /** Moves the cursor to a point given in GUI units from the screen centre. */
    private void cursorFromCentre(double guiX, double guiY) {
        double scale = mc.getWindow().getGuiScale();
        double x = (mc.getWindow().getGuiScaledWidth() / 2.0 + guiX) * scale;
        double y = (mc.getWindow().getGuiScaledHeight() / 2.0 + guiY) * scale;
        mc.mouseHandler.onMove(window(), x, y);
    }

    private void cursorOnRing(int slot, int slots, double radius) {
        double angle = -Math.PI / 2 + (slot + 0.5) * 2 * Math.PI / slots;
        cursorFromCentre(Math.cos(angle) * radius, Math.sin(angle) * radius);
    }

    void mouseClick() {
        mc.mouseHandler.onPress(window(), GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0);
        mc.mouseHandler.onPress(window(), GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0);
    }

    void command(String command) {
        mc.player.connection.sendCommand(command);
    }

    /** Types text into the focused field, one character at a time, through the keyboard handler. */
    void type(String text) {
        text.codePoints().forEach(codePoint -> mc.keyboardHandler.charTyped(window(), codePoint, 0));
    }

    /** Moves the cursor to a point given in GUI units from the top left corner. */
    void cursorAt(double guiX, double guiY) {
        double scale = mc.getWindow().getGuiScale();
        mc.mouseHandler.onMove(window(), guiX * scale, guiY * scale);
    }

    /**
     * With the chat open, finds where a clickable text matching {@code wanted} is drawn, moves the
     * cursor onto it and clicks, as a player would.
     */
    boolean clickChatLink(Predicate<Style> wanted) {
        ChatComponent chat = mc.gui.getChat();
        int height = mc.getWindow().getGuiScaledHeight();
        for (int y = height - 16; y > 0; y--) {
            for (int x = 2; x < 60; x += 2) {
                Style style = chat.getClickedComponentStyleAt(x, y);
                if (style != null && style.getClickEvent() != null && wanted.test(style)) {
                    cursorAt(x, y);
                    mouseClick();
                    return true;
                }
            }
        }
        return false;
    }

    // System messages received in the chat, newest last

    final List<Component> systemChat = new ArrayList<>();

    @SubscribeEvent
    public void onSystemChat(ClientChatReceivedEvent.System event) {
        if (!event.isOverlay()) {
            systemChat.add(event.getMessage());
        }
    }

    void look(Vec3 point) {
        mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, point);
    }

    void lookAtFace(BlockPos pos, Direction face) {
        look(pos.getCenter().add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)));
    }

    // Reading the game state as the client sees it

    BitGrid gridAt(BlockPos pos) {
        return mc.level.getBlockEntity(pos) instanceof CarvedBlockEntity carved ? carved.getGrid() : null;
    }

    int filled(BlockPos pos) {
        BitGrid grid = gridAt(pos);
        return grid == null ? -1 : grid.filledCount();
    }

    ItemStack held() {
        return mc.player.getMainHandItem();
    }

    private int looseBits(net.minecraft.world.level.block.state.BlockState material) {
        int total = 0;
        for (ItemStack stack : mc.player.getInventory().items) {
            if (stack.getItem() instanceof BitItem && BitItem.material(stack) == material) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private int hotbarSlotOf(Class<?> itemClass) {
        for (int slot = 0; slot < 9; slot++) {
            if (itemClass.isInstance(mc.player.getInventory().getItem(slot).getItem())) {
                return slot;
            }
        }
        return -1;
    }

    // Checks that remember what they saw

    boolean eq(String what, int actual, int expected) {
        observed = what + " = " + actual + ", expected " + expected;
        return actual == expected;
    }

    private boolean between(String what, int actual, int min, int max) {
        observed = what + " = " + actual + ", expected " + min + " to " + max;
        return actual >= min && actual <= max;
    }

    /** Whether the server has moved the player to the given position yet. */
    boolean at(double x, double y, double z) {
        Vec3 position = mc.player.position();
        observed = "player at " + position + ", expected " + x + " " + y + " " + z;
        return position.distanceToSqr(x, y, z) < 0.01;
    }

    boolean that(String what, boolean condition) {
        observed = what;
        return condition;
    }

    private String describeHit() {
        if (mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit) {
            return hit.getType() + " " + hit.getBlockPos() + " " + hit.getDirection() + " " + hit.getLocation();
        }
        return String.valueOf(mc.hitResult);
    }

    // The script

    void step(String name, Runnable action, int wait, BooleanSupplier check) {
        steps.add(new Step(name, action, wait, check, null));
    }

    void shot(String name, Runnable action, int wait, BooleanSupplier check, String screenshot) {
        steps.add(new Step(name, action, wait, check, screenshot));
    }

    private void script() {
        step("set up the test area", () -> {
            command("gamerule doDaylightCycle false");
            command("time set noon");
            command("weather clear");
            command("gamemode creative");
            command("tp @s 0.5 -60 0.5 0 0");
            command("fill -2 -60 3 2 -60 3 minecraft:stone");
            command("clear @s");
            command("item replace entity @s hotbar.0 with bitsmith:iron_chisel");
        }, 30, () -> mc.level.getBlockState(A).is(Blocks.STONE) && held().getItem() instanceof ChiselItem);

        shot("left click carves one bit", () -> {
            hotbar(0);
            lookAtFace(A, Direction.NORTH);
        }, 5, () -> true, null);
        shot("one bit removed", this::attack, 8, () -> eq("bits in A", filled(A), BitGrid.VOLUME - 1), "01_single_bit");

        for (int i = 0; i < 4; i++) {
            step("repeated click " + (i + 1), () -> {
                Bitsmith.LOGGER.info("[playtest] before click: hit={} screen={} bits={}", describeHit(), mc.screen, filled(A));
                attack();
            }, 5, () -> true);
        }
        step("repeated clicks keep carving", () -> { }, 6, () -> between("bits in A", filled(A), BitGrid.VOLUME - 5, BitGrid.VOLUME - 2));

        step("V switches the mode to cube", () -> tap(BitsmithKeys.MODE.getKey().getValue()), 6,
                () -> that("mode " + ChiselItem.settings(held()).mode(), ChiselItem.settings(held()).mode() == ChiselMode.CUBE));
        step("B twice sets the size to 4", () -> {
            tap(BitsmithKeys.SIZE.getKey().getValue());
            tap(BitsmithKeys.SIZE.getKey().getValue());
        }, 6, () -> eq("size", ChiselItem.settings(held()).size(), 4));

        step("holding Left Alt opens the tool menu", () -> key(BitsmithKeys.RADIAL.getKey().getValue(), true), 4,
                () -> mc.screen instanceof RadialMenuScreen);
        shot("pointing at Sphere highlights it", () -> cursorOnRing(4, ChiselMode.values().length, 87), 4,
                () -> mc.screen instanceof RadialMenuScreen, "02_radial_chisel");
        step("releasing Left Alt picks Sphere", () -> key(BitsmithKeys.RADIAL.getKey().getValue(), false), 6,
                () -> that("screen " + mc.screen + ", mode " + ChiselItem.settings(held()).mode(), mc.screen == null && ChiselItem.settings(held()).mode() == ChiselMode.SPHERE));
        step("tool menu again, pick Cube", () -> {
            key(BitsmithKeys.RADIAL.getKey().getValue(), true);
        }, 4, () -> mc.screen instanceof RadialMenuScreen);
        step("release on Cube", () -> {
            cursorOnRing(1, ChiselMode.values().length, 87);
            key(BitsmithKeys.RADIAL.getKey().getValue(), false);
        }, 6, () -> that("mode " + ChiselItem.settings(held()).mode(), ChiselItem.settings(held()).mode() == ChiselMode.CUBE));

        step("walk to the grass", () -> command("tp @s 5.5 -60 1.5 0 30"), 10, () -> at(5.5, -60, 1.5));
        double[][] pitPoints = {{0.3, 0.3}, {0.7, 0.3}, {0.3, 0.7}, {0.7, 0.7}};
        for (double[] p : pitPoints) {
            step("dig into the grass", () -> {
                look(new Vec3(PIT.getX() + p[0], PIT.getY() + 1.0, PIT.getZ() + p[1]));
            }, 3, () -> true);
            step("cube stroke", this::attack, 5, () -> true);
        }
        step("the pit is carved", () -> { }, 6, () -> eq("bits in the grass block", filled(PIT), BitGrid.VOLUME - 4 * 64));
        shot("look into the pit", () -> {
            command("tp @s 5.5 -58.4 2.6 0 55");
        }, 15, () -> true, null);
        shot("inner faces are lit", () -> look(new Vec3(PIT.getX() + 0.5, PIT.getY() + 0.8, PIT.getZ() + 0.5)), 10,
                () -> true, "03_pit_lighting");

        step("survival, back to the row", () -> {
            command("gamemode survival");
            command("tp @s 0.5 -60 0.5 0 0");
        }, 15, () -> at(0.5, -60, 0.5));
        step("survival cube stroke gives 64 bits", () -> {
            hotbar(0);
            lookAtFace(B, Direction.NORTH);
        }, 4, () -> true);
        step("bits arrive in the inventory", this::attack, 10,
                () -> eq("loose stone bits", looseBits(Blocks.STONE.defaultBlockState()), 64));
        shot("open the inventory", () -> tap(mc.options.keyInventory.getKey().getValue()), 8,
                () -> mc.screen != null, "04_inventory_bits");
        step("close the inventory", () -> tap(GLFW.GLFW_KEY_ESCAPE), 5, () -> mc.screen == null);

        step("place a bit with right click", () -> {
            hotbar(hotbarSlotOf(BitItem.class));
            lookAtFace(C, Direction.NORTH);
        }, 4, () -> held().getItem() instanceof BitItem);
        step("the bit is placed in front of the block", this::use, 8,
                () -> eq("bits placed", filled(C.north()), 1) && eq("loose stone bits", looseBits(Blocks.STONE.defaultBlockState()), 63));

        step("undo from the tool menu centre", () -> {
            hotbar(0);
            key(BitsmithKeys.RADIAL.getKey().getValue(), true);
        }, 4, () -> mc.screen instanceof RadialMenuScreen);
        step("click the undo half", () -> {
            cursorFromCentre(-12, 0);
            mouseClick();
        }, 8, () -> that("block after undo " + mc.level.getBlockState(C.north()), mc.level.getBlockState(C.north()).isAir()) && eq("loose stone bits", looseBits(Blocks.STONE.defaultBlockState()), 64));
        step("close the tool menu", () -> key(BitsmithKeys.RADIAL.getKey().getValue(), false), 4, () -> mc.screen == null);

        step("a bag collects the chiseled bits", () -> {
            command("item replace entity @s hotbar.2 with bitsmith:bit_bag");
            hotbar(0);
            lookAtFace(C, Direction.NORTH);
        }, 8, () -> mc.player.getInventory().getItem(2).getItem() instanceof BitBagItem);
        step("64 bits go into the bag", this::attack, 10,
                () -> eq("stone bits in the bag", BitBagItem.contents(mc.player.getInventory().getItem(2)).count(Blocks.STONE.defaultBlockState()), 64));

        step("blank pattern copies the carved block", () -> {
            command("gamemode creative");
            command("item replace entity @s hotbar.3 with bitsmith:blank_pattern");
            hotbar(3);
            lookAtFace(A, Direction.NORTH);
        }, 8, () -> true);
        step("right click records the carving", this::use, 8,
                () -> held().getItem() instanceof PatternItem && gridAt(A) != null && gridAt(A).equals(PatternItem.grid(held())));
        shot("the preview shows where it prints", () -> lookAtFace(E, Direction.NORTH), 6, () -> true, "05_pattern_preview");
        shot("right click prints the copy", this::use, 10,
                () -> gridAt(E.north()) != null && gridAt(E.north()).equals(gridAt(A)), "06_pattern_printed");
        shot("pattern tool menu", () -> key(BitsmithKeys.RADIAL.getKey().getValue(), true), 5,
                () -> mc.screen instanceof RadialMenuScreen, "07_radial_pattern");
        step("close the pattern menu", () -> key(BitsmithKeys.RADIAL.getKey().getValue(), false), 4, () -> mc.screen == null);

        // Advanced tools
        step("N turns the mirror on (X)", () -> {
            hotbar(0);
            tap(BitsmithKeys.SYMMETRY.getKey().getValue());
        }, 4, () -> that("mirror " + PlayerSymmetry.client(), PlayerSymmetry.client() == Symmetry.X));
        shot("the outline shows the mirrored stroke", () -> look(new Vec3(F.getX() + 0.2, F.getY() + 0.6, F.getZ())), 6,
                () -> true, "09_mirror_preview");
        step("a mirrored cube stroke", this::attack, 8, () -> eq("bits in F", filled(F), BitGrid.VOLUME - 128));
        step("mirror back off", () -> {
            for (int i = 0; i < 4; i++) {
                tap(BitsmithKeys.SYMMETRY.getKey().getValue());
            }
        }, 4, () -> that("mirror " + PlayerSymmetry.client(), PlayerSymmetry.client() == Symmetry.NONE));

        step("take the wrench", () -> {
            command("item replace entity @s hotbar.4 with bitsmith:wrench");
        }, 6, () -> true);
        step("aim at the top of the mirrored block", () -> {
            hotbar(4);
            lookAtFace(F, Direction.UP);
            beforeWrench = gridAt(F);
        }, 4, () -> that("wrench in hand", held().getItem() instanceof WrenchItem));
        step("right click turns it a quarter turn", this::use, 8, () -> that("grid turned",
                beforeWrench != null && GridTransforms.rotate(beforeWrench, 1, IntUnaryOperator.identity()).equals(gridAt(F))));

        step("take the saw", () -> command("item replace entity @s hotbar.5 with bitsmith:saw"), 6, () -> true);
        step("select the saw", () -> hotbar(5), 4, () -> that("saw in hand", held().getItem() instanceof SawItem));
        step("B sets the saw to 2 bits", () -> tap(BitsmithKeys.SIZE.getKey().getValue()), 6, () -> eq("saw thickness", SawItem.thickness(held()), 2));
        shot("the outline shows the slice", () -> lookAtFace(C, Direction.UP), 6, () -> true, "10_saw_preview");
        step("right click cuts the slice off", () -> {
            sawBefore = filled(C);
            use();
        }, 8, () -> eq("bits cut from C", sawBefore - filled(C), 512)
                && that("slice in the inventory", mc.player.getInventory().items.stream().anyMatch(
                        stack -> stack.getItem() instanceof CarvedBlockItem)));
        shot("saw tool menu", () -> key(BitsmithKeys.RADIAL.getKey().getValue(), true), 5,
                () -> mc.screen instanceof RadialMenuScreen, "11_radial_saw");
        step("close the saw menu", () -> key(BitsmithKeys.RADIAL.getKey().getValue(), false), 4, () -> mc.screen == null);

        step("take the tape measure", () -> command("item replace entity @s hotbar.6 with bitsmith:tape_measure"), 6, () -> true);
        step("first point on A", () -> {
            hotbar(6);
            lookAtFace(A, Direction.NORTH);
        }, 4, () -> true);
        step("right click sets the first point", this::use, 4, () -> that("first point", TapeMeasureClient.first() != null));
        step("aim at F", () -> look(new Vec3(F.getX() + 0.9, F.getY() + 0.1, F.getZ())), 4, () -> true);
        shot("right click sets the second point", this::use, 6,
                () -> that("second point", TapeMeasureClient.second() != null), "12_tape_measure");

        if (Boolean.getBoolean("bitsmith.compatTest")) {
            compatScript();
        }

        step("large GUI scale", () -> {
            hotbar(0);
            mc.options.guiScale().set(4);
            mc.resizeDisplay();
        // The game caps the scale to what the window allows (3 for 1280x720).
        }, 4, () -> between("gui scale", (int) mc.getWindow().getGuiScale(), 3, 4));
        shot("the chisel menu still fits", () -> key(BitsmithKeys.RADIAL.getKey().getValue(), true), 5,
                () -> mc.screen instanceof RadialMenuScreen, "13_radial_gui_scale_4");
        step("close and restore the GUI scale", () -> {
            key(BitsmithKeys.RADIAL.getKey().getValue(), false);
            mc.options.guiScale().set(2);
            mc.resizeDisplay();
        }, 4, () -> mc.screen == null);

        shot("overview", () -> {
            command("tp @s 0.5 -58.6 -1.5 0 20");
        }, 15, () -> true, null);
        shot("overview of the row", () -> look(new Vec3(0.5, -59.5, 3.5)), 8, () -> true, "08_overview");
    }

    // Compatibility (runClientCompat): KubeJS, WorldEdit, Create, under Sodium, Iris and ModernFix

    private static final BlockPos PISTON = new BlockPos(10, -60, -14);
    private static final BlockPos MOVED = new BlockPos(11, -60, -14);

    private void compatScript() {
        step("KubeJS recipes use Bitsmith items", () -> { }, 1, () -> {
            var recipes = mc.getConnection().getRecipeManager();
            var chisel = recipes.byKey(ResourceLocation.fromNamespaceAndPath("bitsmith_compat", "chisel_from_dirt"));
            var bits = recipes.byKey(ResourceLocation.fromNamespaceAndPath("bitsmith_compat", "stone_bits"));
            ItemStack result = bits.map(r -> r.value().getResultItem(mc.level.registryAccess())).orElse(ItemStack.EMPTY);
            return that("chisel recipe " + chisel.isPresent(), chisel.isPresent())
                    && that("bit recipe result " + result, result.getCount() == 16
                    && BitItem.material(result) == Blocks.STONE.defaultBlockState());
        });

        step("WorldEdit copies the carved row", () -> {
            command("gamemode creative");
            command("tp @s 0.5 -60 0.5 0 0");
            command("/pos1 -2,-60,3");
            command("/pos2 2,-60,3");
            command("/copy");
        }, 15, () -> true);
        step("and pastes it ten blocks north", () -> {
            command("tp @s 0.5 -60 -9.5 0 0");
            command("/paste");
        }, 30, () -> that("pasted A equals A", gridAt(A) != null && gridAt(A).equals(gridAt(A.offset(0, 0, -10))))
                && that("pasted F equals F", gridAt(F) != null && gridAt(F).equals(gridAt(F.offset(0, 0, -10)))));
        step("WorldEdit //rotate then paste", () -> {
            command("/rotate 90");
            command("tp @s 0.5 -60 -19.5 0 0");
            command("/paste");
        }, 30, () -> {
            // After a quarter turn the row runs along Z; look for A's copy in that column.
            BitGrid original = gridAt(A);
            for (int dz = -4; dz <= 4; dz++) {
                for (int dx = -4; dx <= 4; dx++) {
                    BitGrid pasted = gridAt(new BlockPos(dx, -60, -20 + dz));
                    if (pasted != null && pasted.filledCount() == original.filledCount()) {
                        boolean turned = !pasted.equals(original);
                        return that("rotated paste found at " + dx + "," + (-20 + dz) + ", carving turned " + turned, turned);
                    }
                }
            }
            return that("no rotated paste found", false);
        });
        step("WorldEdit //set of an empty carved block leaves no ghost", () -> {
            command("/pos1 4,-60,-12");
            command("/pos2 4,-60,-12");
            command("/set bitsmith:carved_block");
        }, 15, () -> that("block after //set " + mc.level.getBlockState(new BlockPos(4, -60, -12)),
                mc.level.getBlockState(new BlockPos(4, -60, -12)).isAir()));
        shot("look at the pasted row", () -> {
            command("tp @s 0.5 -58.6 -11.5 0 20");
        }, 15, () -> true, null);
        shot("pasted row", () -> look(new Vec3(0.5, -59.5, -6.5)), 8, () -> true, "14_worldedit_paste");

        step("carve the block Create will move", () -> {
            command("setblock 11 -60 -14 minecraft:stone");
            command("tp @s 11.5 -60 -16.5 0 0");
            hotbar(0);
        }, 10, () -> at(11.5, -60, -16.5));
        step("one stroke", () -> lookAtFace(MOVED, Direction.NORTH), 4, () -> true);
        step("the block is carved", () -> {
            attack();
        }, 8, () -> {
            movedGrid = gridAt(MOVED);
            return that("carved before moving", movedGrid != null);
        });
        step("build a powered Create piston behind it", () -> {
            command("setblock 10 -60 -14 create:mechanical_piston[facing=east]");
            command("setblock 9 -60 -14 create:piston_extension_pole[facing=east]");
            command("setblock 8 -60 -14 create:piston_extension_pole[facing=east]");
            // -16 rpm: with this layout a positive speed would retract the piston, which is already in.
            command("setblock 10 -61 -14 create:creative_motor[facing=up]{ScrollValue:-16}");
            command("setblock 10 -60 -15 create:creative_motor[facing=south]{ScrollValue:-16}");
            command("tp @s 11.5 -58 -19.5 0 25");
        }, 12, () -> true);
        step("give the piston time to assemble", () -> look(new Vec3(12, -59.5, -14)), 30, () -> true);
        step("read the piston state for the log", () -> command("data get block 10 -60 -14"), 2, () -> true);
        shot("the carved block rides the contraption", () -> { }, 2,
                () -> that("contraption entities " + contraptions(), contraptions() > 0), "15_create_contraption");
        step("stop the motors", () -> {
            command("setblock 10 -61 -14 minecraft:dirt");
            command("setblock 10 -60 -15 minecraft:air");
        }, 60, () -> true);
        shot("the carving survives the trip", () -> { }, 10, () -> {
            for (int x = 11; x <= 14; x++) {
                BitGrid grid = gridAt(new BlockPos(x, -60, -14));
                if (grid != null && grid.equals(movedGrid)) {
                    return that("found at x " + x, true);
                }
            }
            return that("carving not found at x 11 to 14, contraptions " + contraptions(), false);
        }, "16_create_after");

        shot("shaders view", () -> {
            command("tp @s 0.5 -58.6 -1.5 0 20");
        }, 20, () -> true, null);
        shot("carved blocks under the shader pack", () -> look(new Vec3(0.5, -59.5, 3.5)), 10, () -> true, "17_shaders");
    }

    private long contraptions() {
        long count = 0;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace().equals("create")) {
                count++;
            }
        }
        return count;
    }

    // Frame timing, for the performance runs

    /** Frame rate figures over a recording window. */
    record FrameStats(int frames, double averageFps, double onePercentLowFps, double worstFrameMs) {
        @Override
        public String toString() {
            return String.format(java.util.Locale.ROOT, "%.0f fps average, %.0f fps 1%% low, worst frame %.1f ms (%d frames)",
                    averageFps, onePercentLowFps, worstFrameMs, frames);
        }
    }

    private final List<Long> frameNanos = new ArrayList<>();
    private long lastFrame;
    private boolean recordingFrames;
    /** When set, the player keeps flying even after touching the ground (the client would stop it). */
    boolean keepFlying;

    @SubscribeEvent
    public void onFrame(RenderFrameEvent.Post event) {
        long now = System.nanoTime();
        if (recordingFrames && lastFrame != 0) {
            frameNanos.add(now - lastFrame);
        }
        lastFrame = now;
    }

    void startFrames() {
        frameNanos.clear();
        recordingFrames = true;
    }

    FrameStats stopFrames() {
        recordingFrames = false;
        if (frameNanos.isEmpty()) {
            return new FrameStats(0, 0, 0, 0);
        }
        long total = 0;
        for (long nanos : frameNanos) {
            total += nanos;
        }
        List<Long> sorted = new ArrayList<>(frameNanos);
        sorted.sort(Comparator.reverseOrder());
        int worstCount = Math.max(1, sorted.size() / 100);
        long worstTotal = 0;
        for (int i = 0; i < worstCount; i++) {
            worstTotal += sorted.get(i);
        }
        double averageFps = frameNanos.size() / (total / 1e9);
        double lowFps = 1e9 / ((double) worstTotal / worstCount);
        return new FrameStats(frameNanos.size(), averageFps, lowFps, sorted.get(0) / 1e6);
    }

    /** Adds a free line to the report. */
    void note(String line) {
        report.add(line);
        Bitsmith.LOGGER.info("[playtest] {}", line);
    }

    // Driving

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (worldRequested || !(event.getScreen() instanceof TitleScreen title)) {
            return;
        }
        worldRequested = true;
        if (MODE.equals("cnbMigrate")) {
            mc.createWorldOpenFlows().openWorld(WORLD, () -> {
                report.add("FAIL could not open the saved Chisels & Bits world");
                failures++;
                finish();
            });
            return;
        }
        deleteOldWorld();
        mc.createWorldOpenFlows().createFreshLevel(WORLD,
                new LevelSettings("Bitsmith playtest", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                title);
    }

    private void deleteOldWorld() {
        Path world = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
        if (!Files.exists(world)) {
            return;
        }
        try (Stream<Path> files = Files.walk(world)) {
            files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException e) {
            Bitsmith.LOGGER.warn("Playtest could not delete the previous world", e);
        }
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        if (keepFlying && mc.player != null && !mc.player.getAbilities().flying && mc.player.getAbilities().mayfly) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }
        if (finished || !worldRequested) {
            return;
        }
        if (++ticks > TIMEOUT_TICKS) {
            report.add("TIMEOUT at step " + index + ", screen " + mc.screen);
            failures++;
            Screenshot.grab(mc.gameDirectory, "playtest_timeout.png", mc.getMainRenderTarget(), message -> { });
            finish();
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        if (settled < WORLD_SETTLE_TICKS) {
            if (mc.screen == null) {
                settled++;
            }
            return;
        }
        if (waitLeft > 0) {
            waitLeft--;
            return;
        }

        if (index >= steps.size()) {
            finish();
            return;
        }
        Step step = steps.get(index);
        if (!actionDone) {
            try {
                step.action().run();
            } catch (RuntimeException e) {
                record(step, false, "action threw " + e);
                index++;
                return;
            }
            actionDone = true;
            waitLeft = step.waitTicks();
            graceLeft = GRACE_TICKS;
            return;
        }

        boolean ok;
        observed = null;
        try {
            ok = step.check().getAsBoolean();
        } catch (RuntimeException e) {
            ok = false;
        }
        if (!ok && graceLeft > 0) {
            // The game or its integrated server may be running slow (shader compilation, chunk
            // loading): give the result a little longer to show before calling it a failure.
            graceLeft--;
            return;
        }
        graceLeft = GRACE_TICKS;
        record(step, ok, ok ? null : observed);
        if (step.screenshot() != null) {
            Screenshot.grab(mc.gameDirectory, "playtest_" + step.screenshot() + ".png", mc.getMainRenderTarget(), message -> { });
        }
        actionDone = false;
        index++;
    }

    private void record(Step step, boolean ok, String detail) {
        if (!ok) {
            failures++;
        }
        String line = (ok ? "PASS " : "FAIL ") + step.name() + (detail != null ? " (" + detail + ")" : "");
        report.add(line);
        Bitsmith.LOGGER.info("[playtest] {}", line);
    }

    private void finish() {
        finished = true;
        report.add(failures == 0 ? "RESULT: all steps passed" : "RESULT: " + failures + " step(s) failed");
        try {
            Files.write(mc.gameDirectory.toPath().resolve("playtest-report.txt"), report);
        } catch (IOException e) {
            Bitsmith.LOGGER.error("Playtest could not write its report", e);
        }
        mc.stop();
    }
}
