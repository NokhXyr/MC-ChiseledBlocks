/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.admin;

import com.nokhxyr.bitsmith.Bitsmith;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Permission nodes of the admin panel, through NeoForge's permission API. A permission mod that
 * plugs into it, such as LuckPerms, decides; without one, or when a node is not set there, only
 * operators (level 2) are allowed.
 */
public final class BitsmithPermissions {
    /** {@code bitsmith.panel}: open the panel with {@code /bitsmith}, search and see the chunk map. */
    public static final PermissionNode<Boolean> PANEL = operatorNode("panel");
    /** {@code bitsmith.panel.teleport}: the TP buttons. */
    public static final PermissionNode<Boolean> TELEPORT = operatorNode("panel.teleport");
    /** {@code bitsmith.panel.perf}: the Performance tab, measuring server time per block. */
    public static final PermissionNode<Boolean> PERF = operatorNode("panel.perf");

    private BitsmithPermissions() {
    }

    private static PermissionNode<Boolean> operatorNode(String name) {
        return new PermissionNode<>(Bitsmith.MOD_ID, name, PermissionTypes.BOOLEAN,
                (player, uuid, context) -> isOperator(player));
    }

    private static boolean isOperator(ServerPlayer player) {
        return player != null && player.hasPermissions(Commands.LEVEL_GAMEMASTERS);
    }

    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(PANEL, TELEPORT, PERF);
    }

    /**
     * Whether the player holds the node. Never throws: while a player joins, the command list is
     * sent before some permission mods have loaded that player (LuckPerms throws "Capability has
     * not been initialised"), and an exception there kicks the player. The operator rule answers
     * until the permission mod is ready.
     */
    public static boolean has(ServerPlayer player, PermissionNode<Boolean> node) {
        try {
            return Boolean.TRUE.equals(PermissionAPI.getPermission(player, node));
        } catch (RuntimeException notReady) {
            return isOperator(player);
        }
    }
}
