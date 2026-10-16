/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.client.admin;

import com.nokhxyr.bitsmith.network.AdminPayloads;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

/**
 * Client side of the admin screen: keeps the last answers of the server, which the screen reads
 * every frame.
 */
public final class AdminClient {
    /** {@link AdminPayloads.FindRequest#target()} of the search tab. */
    public static final int TARGET_SEARCH = 0;
    /** {@link AdminPayloads.FindRequest#target()} of the chunk selected on the map. */
    public static final int TARGET_CHUNK = 1;

    static @Nullable AdminPayloads.FindResult search;
    static @Nullable AdminPayloads.FindResult chunk;
    static @Nullable AdminPayloads.MapResult map;
    static @Nullable AdminPayloads.PerfReport perfReport;
    static boolean perfRunning;
    static int perfTotalTicks;
    /** Wall clock time the running window started, for the progress bar. */
    static long perfStartMillis;
    /** Seconds left in a window someone else started, when asking for a new one was refused. */
    static int perfBusySeconds = -1;
    /** What the server allows this player, sent when the panel opens. */
    static boolean canTeleport;
    static boolean canProfile;

    private AdminClient() {
    }

    public static void open(AdminPayloads.Open permissions) {
        canTeleport = permissions.canTeleport();
        canProfile = permissions.canProfile();
        search = null;
        chunk = null;
        map = null;
        perfBusySeconds = -1;
        Minecraft.getInstance().setScreen(new AdminScreen());
    }

    public static void onFindResult(AdminPayloads.FindResult result) {
        if (result.target() == TARGET_CHUNK) {
            chunk = result;
        } else {
            search = result;
        }
    }

    public static void onMapResult(AdminPayloads.MapResult result) {
        map = result;
    }

    public static void onPerfState(AdminPayloads.PerfState state) {
        if (state.busy()) {
            perfBusySeconds = (state.ticksLeft() + 19) / 20;
            return;
        }
        perfBusySeconds = -1;
        perfRunning = state.running();
        if (state.running()) {
            perfTotalTicks = state.totalTicks();
            perfStartMillis = Util.getMillis() - (long) (state.totalTicks() - state.ticksLeft()) * 50;
        }
    }

    public static void onPerfReport(AdminPayloads.PerfReport report) {
        perfReport = report;
    }

    public static @Nullable AdminPayloads.FindResult lastSearch() {
        return search;
    }

    public static @Nullable AdminPayloads.FindResult lastChunk() {
        return chunk;
    }

    public static @Nullable AdminPayloads.MapResult lastMap() {
        return map;
    }

    public static @Nullable AdminPayloads.PerfReport lastPerfReport() {
        return perfReport;
    }

    public static boolean canTeleport() {
        return canTeleport;
    }

    public static boolean canProfile() {
        return canProfile;
    }

    public static boolean perfRunning() {
        return perfRunning;
    }

    /** How far the running window is, from 0 to 1. */
    static float perfProgress() {
        if (!perfRunning || perfTotalTicks <= 0) {
            return 0;
        }
        return Math.min(1f, (Util.getMillis() - perfStartMillis) / (perfTotalTicks * 50f));
    }
}
