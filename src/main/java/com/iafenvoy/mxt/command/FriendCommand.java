package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.attachment.FriendAttachment;
import com.iafenvoy.mxt.attachment.FriendAttachment.AddResult;
import com.iafenvoy.mxt.attachment.FriendAttachment.RemoveResult;
import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.ClickEvent.SuggestCommand;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /friend} subtree: the players the holder recognises, and the two ways of adding one. A disabled
 * server option removes the top-level alias and never the command itself. A temporary friend is not in the
 * attachment's codec, so it is gone after a relog, and bare {@code /friend} answers with help rather than a usage
 * error.
 */
public final class FriendCommand {
    private static final SimpleCommandExceptionType ERROR_NOT_SINGLE_PLAYER =
            new SimpleCommandExceptionType(Component.translatable("command.mxt.friend.single"));

    public static final LiteralArgumentBuilder<CommandSourceStack> ROOT = literal("friend")
            .requires(CommandSourceStack::isPlayer)
            .executes(ctx -> help(ctx.getSource()))
            .then(literal("list").executes(ctx -> list(ctx.getSource())))
            .then(literal("add").then(argument("player", GameProfileArgument.gameProfile())
                    .executes(ctx -> add(ctx, false))))
            .then(literal("remove").then(argument("player", GameProfileArgument.gameProfile())
                    .suggests(FriendCommand::suggestRemovable)
                    .executes(ctx -> remove(ctx, false))))
            .then(literal("permanent")
                    .then(literal("add").then(argument("player", GameProfileArgument.gameProfile())
                            .executes(ctx -> add(ctx, true))))
                    .then(literal("remove").then(argument("player", GameProfileArgument.gameProfile())
                            .suggests(FriendCommand::suggestRemovablePermanently)
                            .executes(ctx -> remove(ctx, true)))));

    private FriendCommand() {
    }

    private static int help(CommandSourceStack source) {
        String prefix = root();
        source.sendSuccess(() -> Component.translatable("command.mxt.friend.help.header"), false);
        source.sendSuccess(() -> entry(prefix + "add ", true, "command.mxt.friend.help.add"), false);
        source.sendSuccess(() -> entry(prefix + "list", false, "command.mxt.friend.help.list"), false);
        source.sendSuccess(() -> entry(prefix + "remove ", true, "command.mxt.friend.help.remove"), false);
        source.sendSuccess(() -> entry(prefix + "permanent add ", true, "command.mxt.friend.help.permanent_add"), false);
        source.sendSuccess(() -> entry(prefix + "permanent remove ", true, "command.mxt.friend.help.permanent_remove"), false);
        return 1;
    }

    // The argument is named rather than spelled out so the placeholder stays translated.
    private static Component entry(String suggestion, boolean named, String description) {
        MutableComponent command = named ? usage(suggestion) : Component.literal(suggestion);
        return Component.literal(" ")
                .append(command.withStyle(ChatFormatting.AQUA))
                .append(Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY))
                .append(Component.translatable(description).withStyle(ChatFormatting.GRAY))
                .withStyle(style -> style.withClickEvent(new SuggestCommand(suggestion))
                        .withHoverEvent(new ShowText(Component.translatable("command.mxt.friend.hover", suggestion))));
    }

    private static MutableComponent usage(String command) {
        return Component.literal(command).append(Component.translatable("command.mxt.friend.argument"));
    }

    // Suggestions have to name the root that exists: the alias is only there while the server option allows it.
    private static String root() {
        return MxtServerConfig.INSTANCE.commands.friend.getValue() ? "/friend " : "/mxt friend ";
    }

    private static int list(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        FriendAttachment friends = player.getData(MxtAttachments.FRIEND);
        source.sendSuccess(() -> Component.translatable("command.mxt.friend.list.header",
                friends.permanent().size(), friends.temporary().size()), false);
        source.sendSuccess(() -> Component.translatable("command.mxt.friend.list.permanent",
                listing(friends.permanent(), true)), false);
        source.sendSuccess(() -> Component.translatable("command.mxt.friend.list.temporary",
                listing(friends.temporary(), false)), false);
        return friends.permanent().size() + friends.temporary().size();
    }

    private static Component listing(List<NameAndId> friends, boolean permanent) {
        if (friends.isEmpty())
            return Component.translatable("command.mxt.friend.list.none").withStyle(ChatFormatting.DARK_GRAY);
        MutableComponent row = Component.empty();
        for (int index = 0; index < friends.size(); index++) {
            if (index > 0) row.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
            NameAndId friend = friends.get(index);
            String command = root() + (permanent ? "permanent remove " : "remove ") + friend.name();
            row.append(Component.literal(friend.name()).withStyle(style -> style.withClickEvent(new SuggestCommand(command))
                    .withHoverEvent(new ShowText(Component.translatable("command.mxt.friend.hover", command)))));
        }
        return row;
    }

    private static int add(CommandContext<CommandSourceStack> ctx, boolean permanent) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        NameAndId friend = single(ctx);
        if (player.getUUID().equals(friend.id())) {
            source.sendFailure(Component.translatable("command.mxt.friend.self"));
            return 0;
        }
        FriendAttachment friends = player.getData(MxtAttachments.FRIEND);
        AddResult result = permanent ? friends.addPermanent(friend) : friends.add(friend);
        if (result != AddResult.ADDED) {
            source.sendFailure(Component.translatable(result == AddResult.ALREADY_PERMANENT
                    ? "command.mxt.friend.already_permanent" : "command.mxt.friend.already_temporary", friend.name()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(permanent
                ? "command.mxt.friend.added_permanent" : "command.mxt.friend.added_temporary", friend.name()), false);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx, boolean permanent) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        NameAndId friend = single(ctx);
        FriendAttachment friends = player.getData(MxtAttachments.FRIEND);
        RemoveResult result = permanent ? friends.removePermanent(friend.id()) : friends.remove(friend.id());
        if (result == RemoveResult.PERMANENT) {
            source.sendFailure(Component.translatable("command.mxt.friend.remove_permanent", friend.name(),
                    usage(root() + "permanent remove ")));
            return 0;
        }
        if (result == RemoveResult.NOT_A_FRIEND) {
            source.sendFailure(Component.translatable("command.mxt.friend.not_a_friend", friend.name()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.friend.removed", friend.name()), false);
        return 1;
    }

    // GameProfileArgument resolves through the server's profile cache rather than the online player list, which is
    // what makes a friend removable while offline.
    private static NameAndId single(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Collection<NameAndId> profiles = GameProfileArgument.getGameProfiles(ctx, "player");
        if (profiles.size() != 1) throw ERROR_NOT_SINGLE_PLAYER.create();
        return profiles.iterator().next();
    }

    // Only the temporary friends are offered: a completion that leads straight to a refusal is worse than none.
    private static CompletableFuture<Suggestions> suggestRemovable(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggest(ctx, builder, false);
    }

    private static CompletableFuture<Suggestions> suggestRemovablePermanently(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return suggest(ctx, builder, true);
    }

    private static CompletableFuture<Suggestions> suggest(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder, boolean permanent) {
        ServerPlayer player = ctx.getSource().getPlayer();
        FriendAttachment friends = player == null ? null : player.getExistingData(MxtAttachments.FRIEND).orElse(null);
        if (friends == null) return builder.buildFuture();
        List<String> names = (permanent ? friends.permanent() : friends.temporary()).stream().map(NameAndId::name).toList();
        return SharedSuggestionProvider.suggest(names, builder);
    }
}
