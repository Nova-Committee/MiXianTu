package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.network.payload.ItemPickerS2CPayload;
import com.iafenvoy.mxt.screen.picker.ItemPickerManager;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class PickerCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> ROOT = literal("picker")
            .requires(source -> source.isPlayer() && source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
            .executes(context -> open(context, List.of()))
            .then(argument("category", IdentifierArgument.id())
                    .suggests(PickerCommand::suggestCategories)
                    .executes(context -> open(context, categories(context))));

    private static CompletableFuture<Suggestions> suggestCategories(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ItemPickerManager.categories().stream().map(key -> key.identifier().toString()), builder);
    }

    /**
     * Resolves the category argument into the id the client will walk, rejecting an id this mod does not
     * offer rather than letting the picker open on nothing.
     */
    private static List<Identifier> categories(CommandContext<CommandSourceStack> context) {
        Identifier id = IdentifierArgument.getId(context, "category");
        return List.of(ItemPickerManager.categories().stream()
                .map(ResourceKey::identifier)
                .filter(id::equals)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown picker category " + id)));
    }

    private static int open(CommandContext<CommandSourceStack> context, List<Identifier> categories) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        if (!player.hasInfiniteMaterials()) {
            source.sendFailure(Component.translatable("command.mxt.picker.creative_only"));
            return 0;
        }
        // "No category" is sent as the explicit list of every category: the client builds the grid from the
        // registries it has synced, so the categories it walks have to be named rather than implied.
        List<Identifier> ids = categories.isEmpty()
                ? ItemPickerManager.categories().stream().map(ResourceKey::identifier).toList()
                : categories;
        PacketDistributor.sendToPlayer(player, new ItemPickerS2CPayload(Component.translatable("command.mxt.picker"), ids));
        source.sendSuccess(() -> Component.translatable("command.mxt.picker.opened", ids.size()), false);
        return ids.size();
    }
}
