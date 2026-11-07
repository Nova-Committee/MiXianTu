package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.runtime.world.SoulService;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class SoulCommand {
    private SoulCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("soul").requires(ServerCommandManager::mayChange);
        for (boolean create : List.of(true, false))
            root.then(literal(create ? "create" : "reclaim")
                    .executes(c -> apply(c.getSource(), List.of(c.getSource().getPlayerOrException()), create))
                    .then(argument("targets", EntityArgument.entities())
                            .executes(c -> apply(c.getSource(), EntityArgument.getEntities(c, "targets"), create))));
        return root;
    }

    private static int apply(CommandSourceStack source, Iterable<? extends Entity> targets, boolean create) {
        int changed = 0;
        for (Entity entity : targets) {
            boolean success = create ? SoulService.transfer(entity, "command") : SoulService.reclaim(entity);
            if (success) {
                changed++;
                source.sendSuccess(() -> Component.translatable(create ? "command.mxt.soul.created" : "command.mxt.soul.reclaimed", entity.getDisplayName()), true);
            } else
                source.sendFailure(Component.translatable(create ? "command.mxt.soul.create_failed" : "command.mxt.soul.reclaim_failed", entity.getDisplayName()));
        }
        return changed;
    }
}
