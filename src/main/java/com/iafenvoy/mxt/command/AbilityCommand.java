package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.ability.AbilityService.UseResult;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /ability} command; also reachable as {@code /mxt ability}. Casting keeps its operator
 * requirement in both positions: an alias changes where a node hangs, never what it may do.
 * <p>
 * Granting and revoking go through the same source ledger content uses, under the command's own source, so an
 * operator cannot take away what a quest reward, a piece of gear or a cultivation identity granted.
 */
public final class AbilityCommand {
    /**
     * What the command records as the source of what it grants, in the same shape the other modules use.
     */
    private static final Identifier SOURCE = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "command");
    public static final LiteralArgumentBuilder<CommandSourceStack> ROOT = literal("ability")
            .then(literal("list")
                    .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayer()))
                    .then(argument("target", EntityArgument.entity())
                            .executes(ctx -> list(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
            .then(literal("cast").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(argument("id", IdentifierArgument.id())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                    MxtDatapackRegistries.holders(ctx.getSource().getServer().registryAccess(), MxtResourceKeys.ABILITY)
                                            .map(HolderHelper::id).map(Identifier::toString).sorted().toList(), builder))
                            .executes(ctx -> castAbility(ctx.getSource(), IdentifierArgument.getId(ctx, "id")))))
            .then(literal("grant").requires(AbilityCommand::mayChange)
                    .then(argument("targets", EntityArgument.entities())
                            .then(argument("ability", IdentifierArgument.id())
                                    .suggests(AbilityCommand::suggestAbilities)
                                    .executes(AbilityCommand::grant))))
            .then(literal("revoke").requires(AbilityCommand::mayChange)
                    .then(argument("targets", EntityArgument.entities())
                            .then(argument("ability", IdentifierArgument.id())
                                    .suggests(AbilityCommand::suggestAbilities)
                                    .executes(AbilityCommand::revoke))));

    private static boolean mayChange(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    private static CompletableFuture<Suggestions> suggestAbilities(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(MxtDatapackRegistries
                .holders(ctx.getSource().getServer().registryAccess(), MxtResourceKeys.ABILITY)
                .map(HolderHelper::id).map(Identifier::toString).sorted().toList(), builder);
    }

    /**
     * Prints what one holder carries, one line per ability, together with the sources that keep it granted. The
     * list is read from the attachment rather than from the registry, so an ability whose definition was
     * disabled or deleted is still reported: it is still held, and revoking it by name is still what takes it off.
     */
    private static int list(CommandSourceStack source, @Nullable Entity target) {
        if (target == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        AbilityAttachment abilities = target.getData(MxtAttachments.ABILITY_HOLDER);
        source.sendSuccess(() -> Component.translatable("command.mxt.ability.list.header", target.getDisplayName()), false);
        if (abilities.sources().keys().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.ability.list.empty"), false);
            return 1;
        }
        for (Holder<Ability> ability : abilities.sources().keys()) {
            Component name = DefinitionText.name(ability, "ability");
            Component from = Component.literal(abilities.sources().of(ability).stream()
                    .map(Identifier::toString).sorted().reduce((a, b) -> a + ", " + b).orElse("-"));
            source.sendSuccess(() -> Component.translatable("command.mxt.ability.list.line", name, from), false);
        }
        return 1;
    }

    /**
     * Grants one ability to every target under the command's own source, reporting each target separately.
     */
    private static int grant(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Identifier id = IdentifierArgument.getId(ctx, "ability");
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
        Holder<Ability> ability = MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, id).orElse(null);
        if (ability == null) {
            source.sendFailure(Component.translatable("command.mxt.ability.unknown", id.toString()));
            return 0;
        }
        int granted = 0;
        for (Entity target : targets) {
            AbilityAttachment abilities = target.getData(MxtAttachments.ABILITY_HOLDER);
            if (abilities.grant(ability, SOURCE)) {
                granted++;
                rebuild(target);
                source.sendSuccess(() -> Component.translatable("command.mxt.ability.granted",
                        DefinitionText.name(ability, "ability"), target.getDisplayName()), true);
            } else {
                source.sendFailure(Component.translatable("command.mxt.ability.grant_failed",
                        target.getDisplayName(), DefinitionText.name(ability, "ability")));
            }
        }
        return granted;
    }

    /**
     * Drops one source, which only removes the ability when that was its last one; nothing else can be revoked,
     * so a target that never held that source is a failure rather than a silent success.
     */
    private static int revoke(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Identifier id = IdentifierArgument.getId(ctx, "ability");
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
        Holder<Ability> ability = MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, id).orElse(null);
        if (ability == null) {
            source.sendFailure(Component.translatable("command.mxt.ability.unknown", id.toString()));
            return 0;
        }
        int revoked = 0;
        for (Entity target : targets) {
            if (target.getData(MxtAttachments.ABILITY_HOLDER).revoke(ability, SOURCE)) {
                revoked++;
                rebuild(target);
                source.sendSuccess(() -> Component.translatable("command.mxt.ability.revoked",
                        DefinitionText.name(ability, "ability"), target.getDisplayName()), true);
            } else {
                source.sendFailure(Component.translatable("command.mxt.ability.revoke_failed",
                        target.getDisplayName(), DefinitionText.name(ability, "ability")));
            }
        }
        return revoked;
    }

    /**
     * A source change moves which triggers the entity listens for, and only a living entity runs them.
     */
    private static void rebuild(Entity target) {
        if (target instanceof LivingEntity living) AbilityEventBridge.rebuildTriggerSubscriptions(living);
    }

    private static int castAbility(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        UseResult result = MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, id).map(ability -> AbilityService.use(ability, ability.value(), player,
                player.getData(MxtAttachments.ABILITY_HOLDER), player.getData(MxtAttachments.RESOURCE_HOLDER), player.level().getGameTime(), FormulaContext.of(player))).orElse(null);
        if (result == null || !result.committed()) {
            source.sendFailure(Component.translatable("command.mxt.ability.cast_failed", result == null ? "unknown_definition" : result.failure().name()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.ability.cast_success", DefinitionText.name(id, "ability")), true);
        return 1;
    }
}
