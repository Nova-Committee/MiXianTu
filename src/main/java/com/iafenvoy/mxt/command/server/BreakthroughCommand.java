package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /curse} command; also reachable as {@code /mxt curse}. Reading the executing player's own curses needs
 * no permission, while changing anybody's - applying, removing or cleansing - keeps its operator requirement in
 * both positions. It is an administrator's mirror of the transactions content uses, going through
 * {@link CurseService}, so a curse applied here obeys its own condition and stacking.
 */
public final class BreakthroughCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("breakthrough")
                .requires(ServerCommandManager::mayChange)
                .then(argument("aura", ResourceArgument.resource(context, MxtResourceKeys.AURA))
                        .executes(ctx -> attemptBreakthrough(ctx.getSource(), ResourceArgument.getResource(ctx, "aura", MxtResourceKeys.AURA))));
    }

    private static int attemptBreakthrough(CommandSourceStack source, Reference<Aura> aura) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        CultivationService.BreakthroughResult result = CultivationService.attempt(player, player.getData(MxtAttachments.CULTIVATION), player.getData(MxtAttachments.RESOURCE_HOLDER), aura, FormulaContext.of(player), () -> true);
        if (result == null || !result.isSuccess()) {
            Component reason = result == null || result.failure() == null
                    ? Component.translatable("failure.mxt.common.unknown")
                    : result.failure().message(result.failedResource());
            source.sendFailure(Component.translatable("command.mxt.breakthrough.failed", reason));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.breakthrough.success", DefinitionText.nameOrId(aura)), true);
        return 1;
    }
}
