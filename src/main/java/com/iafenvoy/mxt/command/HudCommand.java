package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.screen.hud.HudEntry;
import com.iafenvoy.mxt.screen.hud.HudManager;
import com.iafenvoy.mxt.screen.hud.ScreenBounds;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Client command for looking at the draggable HUD: a diagnostic for "the editor shows nothing" with no other way of
 * being answered. It prints what the framework actually holds - every registered element, where it is, whether it
 * is visible and movable, and how many blocks it offers to draw. Being a client command it needs no permission, and
 * it is the only command in this mod registered on the client.
 */
@EventBusSubscriber(Dist.CLIENT)
public final class HudCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> ROOT = literal("hud")
            .executes(HudCommand::list)
            .then(literal("open").executes(context -> {
                HudManager.openEditor();
                return 1;
            }))
            .then(argument("element", StringArgumentType.string())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                            HudManager.moveableEntries().stream().map(HudEntry::layoutKey), builder))
                    .then(literal("reset").executes(HudCommand::reset)));

    private static int list(CommandContext<CommandSourceStack> context) {
        List<HudEntry> entries = HudManager.moveableEntries();
        if (entries.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable("command.mxt.hud.none"), false);
            return 0;
        }
        for (HudEntry entry : entries) {
            ScreenBounds bounds = entry.bounds();
            context.getSource().sendSuccess(() -> Component.translatable("command.mxt.hud.line",
                    entry.layoutKey(), entry.displayName(), bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    entry.renderBlocks().size(), entry.visible(), entry.moveable()), false);
        }
        return entries.size();
    }

    private static int reset(CommandContext<CommandSourceStack> context) {
        String key = StringArgumentType.getString(context, "element");
        for (HudEntry entry : HudManager.moveableEntries()) {
            if (!entry.layoutKey().equals(key)) continue;
            entry.resetToDefault();
            context.getSource().sendSuccess(() -> Component.translatable("command.mxt.hud.reset", key), false);
            return 1;
        }
        context.getSource().sendFailure(Component.translatable("command.mxt.hud.unknown", key));
        return 0;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(ROOT);
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        register(event.getDispatcher());
    }
}
