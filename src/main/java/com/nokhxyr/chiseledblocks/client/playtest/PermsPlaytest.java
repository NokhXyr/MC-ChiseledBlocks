/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.client.playtest;

import com.nokhxyr.chiseledblocks.ChiseledBlocks;
import com.nokhxyr.chiseledblocks.client.admin.AdminClient;
import com.nokhxyr.chiseledblocks.client.admin.AdminScreen;
import com.nokhxyr.chiseledblocks.network.AdminPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The panel's permission nodes through a real LuckPerms ({@code runServerPerms}, then
 * {@code runClientPerms}). LuckPerms only runs on a dedicated server, so the client joins one and
 * the test drives LuckPerms from the server console over RCON, the way a server owner would.
 * The player starts as an operator; nodes are denied, then given back, and the panel must follow.
 */
final class PermsPlaytest {
    private static final int SERVER_PORT = 25599;
    private static final int RCON_PORT = 25598;
    private static final String RCON_PASSWORD = "chiseledblocks-playtest";
    private static final Path SERVER_LOG = Path.of("..", "run-perms-server", "logs", "latest.log");
    private static final BlockPos GROUND = new BlockPos(0, -61, 3);
    private static final BlockPos CHISELED = GROUND.above();

    private PermsPlaytest() {
    }

    /** Waits on a background thread until the test server answers, then connects to it. */
    static void joinWhenReady(Screen parent) {
        Thread waiter = new Thread(() -> {
            for (int attempt = 0; attempt < 600; attempt++) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress("127.0.0.1", SERVER_PORT), 500);
                    Minecraft.getInstance().execute(() -> ConnectScreen.startConnecting(parent, Minecraft.getInstance(),
                            ServerAddress.parseString("127.0.0.1:" + SERVER_PORT),
                            new ServerData("Chiseled Blocks permission test", "127.0.0.1:" + SERVER_PORT, ServerData.Type.OTHER),
                            false, null));
                    return;
                } catch (IOException notYet) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
            ChiseledBlocks.LOGGER.error("[playtest] the permission test server never answered on port {}", SERVER_PORT);
        }, "Chiseled Blocks playtest server wait");
        waiter.setDaemon(true);
        waiter.start();
    }

    /** Runs a command on the server console over RCON and returns its output. */
    static String rcon(String command) {
        try (Socket socket = new Socket("127.0.0.1", RCON_PORT)) {
            socket.setSoTimeout(10_000);
            OutputStream out = socket.getOutputStream();
            DataInputStream in = new DataInputStream(socket.getInputStream());
            send(out, 1, 3, RCON_PASSWORD);
            if (readPacket(in).id() == -1) {
                return "rcon login refused";
            }
            send(out, 2, 2, command);
            return readPacket(in).body();
        } catch (IOException e) {
            return "rcon failed: " + e;
        }
    }

    private record Packet(int id, String body) {
    }

    private static void send(OutputStream out, int id, int type, String body) throws IOException {
        byte[] text = body.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(14 + text.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(10 + text.length).putInt(id).putInt(type).put(text).put((byte) 0).put((byte) 0);
        out.write(buffer.array());
        out.flush();
    }

    private static Packet readPacket(InputStream in) throws IOException {
        byte[] header = new DataInputStream(in).readNBytes(4);
        int length = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt();
        byte[] rest = new DataInputStream(in).readNBytes(length);
        ByteBuffer buffer = ByteBuffer.wrap(rest).order(ByteOrder.LITTLE_ENDIAN);
        int id = buffer.getInt();
        buffer.getInt();
        return new Packet(id, new String(rest, 8, Math.max(0, length - 10), StandardCharsets.UTF_8));
    }

    private static void console(ClientPlaytest p, String command) {
        p.note("console> " + command + " -> " + rcon(command).trim());
    }

    private static boolean onScreen() {
        return Minecraft.getInstance().screen instanceof AdminScreen;
    }

    private static boolean serverLogMentions(String text) {
        try {
            return Files.readString(SERVER_LOG).contains(text);
        } catch (IOException e) {
            return false;
        }
    }

    static void script(ClientPlaytest p) {
        p.step("joined the dedicated server as Dev", () -> { }, 1,
                () -> p.that("connected", Minecraft.getInstance().getCurrentServer() != null));
        p.step("LuckPerms handles permissions on the server", () -> { }, 1,
                () -> p.that("server log names the LuckPerms handler",
                        serverLogMentions("Successfully initialized permission handler luckperms:")));
        p.step("set up", () -> {
            p.keepFlying = true;
            p.command("tp @s 0.5 -60 0.5 0 30");
            p.command("clear @s");
            p.command("give @s chiseledblocks:chiseled/oak_planks");
        }, 30, () -> p.at(0.5, -60, 0.5));
        p.step("place a carved block", () -> {
            p.hotbar(0);
            p.lookAtFace(GROUND, Direction.UP);
            p.use();
        }, 20, () -> p.that("carved block placed", p.gridAt(CHISELED) != null));

        // Nothing set in LuckPerms: the default applies, operators may do everything.
        p.step("an operator opens the panel with /chiseledblocks", () -> p.command("chiseledblocks"), 20,
                () -> p.that("open with every tool", onScreen() && AdminClient.canTeleport() && AdminClient.canProfile()));
        p.shot("the search lists the carved block with its TP button", () -> { }, 10,
                () -> p.that("TP button shown", ((AdminScreen) Minecraft.getInstance().screen).locate("tp.search.0") != null),
                "perms_full");
        p.step("close it", () -> p.tap(GLFW.GLFW_KEY_ESCAPE), 5, () -> p.that("closed", !onScreen()));

        // Teleport and profiling denied, the panel itself still allowed.
        p.step("deny chiseledblocks.panel.teleport and chiseledblocks.panel.perf", () -> {
            console(p, "lp user Dev permission set chiseledblocks.panel.teleport false");
            console(p, "lp user Dev permission set chiseledblocks.panel.perf false");
        }, 40, () -> true);
        p.shot("the panel opens without TP buttons or Performance tab", () -> p.command("chiseledblocks"), 20, () -> {
            if (!(Minecraft.getInstance().screen instanceof AdminScreen screen)) {
                return p.that("panel not open", false);
            }
            return p.that("teleport " + AdminClient.canTeleport() + ", profile " + AdminClient.canProfile(),
                    !AdminClient.canTeleport() && !AdminClient.canProfile()
                            && screen.locate("tab.perf") == null && screen.locate("tp.search.0") == null);
        }, "perms_limited");
        p.step("a teleport request is refused by the server anyway", () -> {
            p.tap(GLFW.GLFW_KEY_ESCAPE);
            PacketDistributor.sendToServer(new AdminPayloads.Teleport(
                    Minecraft.getInstance().level.dimension().location(), new BlockPos(30, -60, 30)));
        }, 20, () -> p.at(0.5, -60, 0.5));

        // The panel itself denied.
        p.step("deny chiseledblocks.panel", () -> console(p, "lp user Dev permission set chiseledblocks.panel false"), 40, () -> true);
        p.step("/chiseledblocks no longer opens the panel", () -> p.command("chiseledblocks"), 20, () -> p.that("not open", !onScreen()));
        p.step("nor the panel key", () -> PacketDistributor.sendToServer(new AdminPayloads.RequestOpen()), 20,
                () -> p.that("not open", !onScreen()));

        // Given back to a non operator through a group, the way a server would hand it to staff.
        p.step("remove Dev's operator rights and unset the denials", () -> {
            console(p, "deop Dev");
            console(p, "lp user Dev permission unset chiseledblocks.panel");
            console(p, "lp user Dev permission unset chiseledblocks.panel.teleport");
            console(p, "lp user Dev permission unset chiseledblocks.panel.perf");
        }, 40, () -> true);
        p.step("a plain player cannot open the panel", () -> p.command("chiseledblocks"), 20, () -> p.that("not open", !onScreen()));
        p.step("grant chiseledblocks.panel and teleport to a staff group", () -> {
            console(p, "lp creategroup staff");
            console(p, "lp group staff permission set chiseledblocks.panel true");
            console(p, "lp group staff permission set chiseledblocks.panel.teleport true");
            console(p, "lp user Dev parent add staff");
        }, 40, () -> true);
        p.shot("the staff member opens the panel, with TP but no profiling", () -> p.command("chiseledblocks"), 20,
                () -> p.that("open, teleport " + AdminClient.canTeleport() + ", profile " + AdminClient.canProfile(),
                        onScreen() && AdminClient.canTeleport() && !AdminClient.canProfile()), "perms_staff");
        p.step("close and clean up", () -> {
            p.tap(GLFW.GLFW_KEY_ESCAPE);
            console(p, "lp user Dev parent remove staff");
            console(p, "lp deletegroup staff");
            console(p, "op Dev");
        }, 20, () -> p.that("closed", !onScreen()));
    }
}
