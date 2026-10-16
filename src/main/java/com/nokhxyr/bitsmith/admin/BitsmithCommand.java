/*
 * Bitsmith
 * Author: NokhXyr
 */
package com.nokhxyr.bitsmith.admin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /bitsmith}: opens the admin panel. Only offered to players holding
 * {@link BitsmithPermissions#PANEL} (operators unless a permission mod says otherwise).
 */
public final class BitsmithCommand {
    private BitsmithCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bitsmith")
                .requires(source -> source.getPlayer() != null && BitsmithPermissions.has(source.getPlayer(), BitsmithPermissions.PANEL))
                .executes(ctx -> open(ctx.getSource())));
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AdminService.open(player);
        return 1;
    }
}
