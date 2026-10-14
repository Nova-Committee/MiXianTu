package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.cultivation.Cultivation;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationMethodService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationModeService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /cultivate} command; also reachable as {@code /mxt cultivate}. {@code status} reports the session a
 * body is running, and {@code select} names one method to start right away, which is an operator's way to skip
 * {@code priority} without weakening the method's own conditions.
 */
public final class CultivateCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("cultivate")
                .then(literal("status").executes(ctx -> status(ctx.getSource())))
                .then(literal("select")
                        .requires(ServerCommandManager::mayChange)
                        .then(argument("action", ResourceArgument.resource(context, MxtResourceKeys.CULTIVATION))
                                .executes(ctx -> select(ctx.getSource(), cultivation(ctx)))));
    }

    private static Reference<Cultivation> cultivation(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ResourceArgument.getResource(ctx, "action", MxtResourceKeys.CULTIVATION);
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CultivationAttachment spirit = player.getData(MxtAttachments.CULTIVATION);
        Component action = spirit.cultivation().<Component>map(DefinitionText::text).orElseGet(() -> Component.translatable("command.mxt.message.none"));
        Component progress = spirit.cultivationProgresses().isEmpty() ? Component.translatable("command.mxt.message.none")
                : Component.literal(spirit.cultivationProgresses().object2DoubleEntrySet().stream()
                .map(entry -> DefinitionText.text(entry.getKey()).getString() + "="
                        + String.format(Locale.ROOT, "%.2f", entry.getDoubleValue()))
                .collect(Collectors.joining(", ")));
        source.sendSuccess(() -> Component.translatable("command.mxt.cultivate.status", action, progress, spirit.nextCultivateTick()), false);
        return 1;
    }

    // The one-shot explicit pick: the named method runs now even when priority would choose another, and that
    // body's own conditions still decide whether it runs at all.
    private static int select(CommandSourceStack source, Holder<Cultivation> action) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CultivationMethodService.Result result = CultivationModeService.startNamed(player, action);
        if (!result.started()) {
            String failure = result.failure() == null ? "not_applicable" : result.failure().name().toLowerCase(Locale.ROOT);
            source.sendFailure(Component.translatable("command.mxt.cultivate.select.failed",
                    DefinitionText.text(action),
                    Component.translatable("actionbar.mxt.cultivation.failure." + failure)));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.cultivate.select.done",
                DefinitionText.text(action)), true);
        return 1;
    }
}
