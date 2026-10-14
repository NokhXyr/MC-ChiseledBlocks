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
import com.nokhxyr.bitsmith.core.edit.ChiselMode;
import com.nokhxyr.bitsmith.core.voxel.BitGrid;
import com.nokhxyr.bitsmith.item.BitBagItem;
import com.nokhxyr.bitsmith.item.BitItem;
import com.nokhxyr.bitsmith.item.ChiselItem;
import com.nokhxyr.bitsmith.item.PatternItem;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;
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
    private static final String WORLD = "bitsmith-playtest";
    private static final int WORLD_SETTLE_TICKS = 60;
    private static final int TIMEOUT_TICKS = 20 * 240;

    private static final BlockPos PLAYER = new BlockPos(0, -60, 0);
    private static final BlockPos A = new BlockPos(0, -60, 3);
    private static final BlockPos B = new BlockPos(1, -60, 3);
    private static final BlockPos C = new BlockPos(-1, -60, 3);
    private static final BlockPos E = new BlockPos(-2, -60, 3);
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
    private int ticks;
    private int failures;
    private boolean finished;
    /** What the last check saw, reported when it fails. */
    private String observed;

    public ClientPlaytest() {
        script();
    }

    // Input, through the game's own handlers

    private long window() {
        return mc.getWindow().getWindow();
    }

    private void key(int glfwKey, boolean press) {
        mc.keyboardHandler.keyPress(window(), glfwKey, GLFW.glfwGetKeyScancode(glfwKey),
                press ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
    }

    private void tap(int glfwKey) {
        key(glfwKey, true);
        key(glfwKey, false);
    }

    private void hotbar(int slot) {
        tap(GLFW.GLFW_KEY_1 + slot);
    }

    private void attack() {
        KeyMapping.click(mc.options.keyAttack.getKey());
    }

    private void use() {
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

    private void mouseClick() {
        mc.mouseHandler.onPress(window(), GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0);
        mc.mouseHandler.onPress(window(), GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0);
    }

    private void command(String command) {
        mc.player.connection.sendCommand(command);
    }

    private void look(Vec3 point) {
        mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, point);
    }

    private void lookAtFace(BlockPos pos, Direction face) {
        look(pos.getCenter().add(Vec3.atLowerCornerOf(face.getNormal()).scale(0.5)));
    }

    // Reading the game state as the client sees it

    private BitGrid gridAt(BlockPos pos) {
        return mc.level.getBlockEntity(pos) instanceof CarvedBlockEntity carved ? carved.getGrid() : null;
    }

    private int filled(BlockPos pos) {
        BitGrid grid = gridAt(pos);
        return grid == null ? -1 : grid.filledCount();
    }

    private ItemStack held() {
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

    private boolean eq(String what, int actual, int expected) {
        observed = what + " = " + actual + ", expected " + expected;
        return actual == expected;
    }

    private boolean between(String what, int actual, int min, int max) {
        observed = what + " = " + actual + ", expected " + min + " to " + max;
        return actual >= min && actual <= max;
    }

    private boolean that(String what, boolean condition) {
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

    private void step(String name, Runnable action, int wait, BooleanSupplier check) {
        steps.add(new Step(name, action, wait, check, null));
    }

    private void shot(String name, Runnable action, int wait, BooleanSupplier check, String screenshot) {
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

        step("walk to the grass", () -> command("tp @s 5.5 -60 1.5 0 30"), 10, () -> true);
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
        }, 15, () -> true);
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

        shot("overview", () -> {
            command("tp @s 0.5 -58.6 -1.5 0 20");
        }, 15, () -> true, null);
        shot("overview of the row", () -> look(new Vec3(0.5, -59.5, 3.5)), 8, () -> true, "08_overview");
    }

    // Driving

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (worldRequested || !(event.getScreen() instanceof TitleScreen title)) {
            return;
        }
        worldRequested = true;
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
        if (finished || !worldRequested) {
            return;
        }
        if (++ticks > TIMEOUT_TICKS) {
            report.add("TIMEOUT at step " + index);
            failures++;
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
            return;
        }

        boolean ok;
        observed = null;
        try {
            ok = step.check().getAsBoolean();
        } catch (RuntimeException e) {
            ok = false;
        }
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
