/*
 * Chiseled Blocks
 * Author: NokhXyr
 */
package com.nokhxyr.chiseledblocks.admin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * {@code /chiseledblocks}: opens the admin panel. Only offered to players holding
 * {@link ChiseledBlocksPermissions#PANEL} (operators unless a permission mod says otherwise).
 */
public final class ChiseledBlocksCommand {
    private ChiseledBlocksCommand() {
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chiseledblocks")
                .requires(source -> source.getPlayer() != null && ChiseledBlocksPermissions.has(source.getPlayer(), ChiseledBlocksPermissions.PANEL))
                .executes(ctx -> open(ctx.getSource())));
    }

    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AdminService.open(player);
        return 1;
    }
}
