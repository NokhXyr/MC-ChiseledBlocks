/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.edit;

import com.nokhxyr.bitsmith.core.edit.Symmetry;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * The mirror setting of each player. It is a preference, not game state: the client picks it and
 * tells the server, which applies it to that player's chisel strokes and bit placements.
 */
public final class PlayerSymmetry {
    private static final Map<Player, Symmetry> SERVER = new WeakHashMap<>();
    /** The local player's choice, on the client. */
    private static volatile Symmetry client = Symmetry.NONE;

    private PlayerSymmetry() {
    }

    public static Symmetry of(Player player) {
        if (player.level().isClientSide) {
            return client;
        }
        synchronized (SERVER) {
            return SERVER.getOrDefault(player, Symmetry.NONE);
        }
    }

    public static void setOnServer(Player player, Symmetry symmetry) {
        synchronized (SERVER) {
            SERVER.put(player, symmetry);
        }
    }

    public static Symmetry client() {
        return client;
    }

    public static void setOnClient(Symmetry symmetry) {
        client = symmetry;
    }
}
