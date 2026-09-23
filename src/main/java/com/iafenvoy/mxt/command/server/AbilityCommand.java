package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.command.Suggestions;
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
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /ability} command; also reachable as {@code /mxt ability}. An alias changes where a node hangs, never
 * what it may do, so casting keeps its operator requirement in both positions. Granting and revoking go through the
 * same source ledger content uses, under the command's own source, so an operator cannot take away what a quest
 * reward, a piece of gear or a cultivation identity granted.
 */
public final class AbilityCommand {
    // Recorded as the source of what the command grants, in the same shape the other modules use.
    private static final Identifier SOURCE = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "command");

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("ability")
                .then(literal("list")
                        .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayer()))
                        .then(argument("target", EntityArgument.entity())
                                .executes(ctx -> list(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
                .then(literal("cast").requires(ServerCommandManager::mayChange)
                        .then(argument("id", ResourceArgument.resource(context, MxtResourceKeys.ABILITY))
                                .executes(ctx -> castAbility(ctx.getSource(), id(ctx)))))
                .then(literal("grant").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("ability", ResourceArgument.resource(context, MxtResourceKeys.ABILITY))
                                        .executes(AbilityCommand::grant))))
                // IdentifierArgument, not ResourceArgument: a revoked ability only has to be held by the source
                // ledger, so an id whose definition is gone is still exactly what has to be named here.
                .then(literal("revoke").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("ability", IdentifierArgument.id())
                                        .suggests(Suggestions.enabledIds(MxtResourceKeys.ABILITY))
                                        .executes(AbilityCommand::revoke))));
    }

    private static Reference<Ability> id(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ResourceArgument.getResource(ctx, "id", MxtResourceKeys.ABILITY);
    }

    // Read from the attachment rather than the registry, so an ability whose definition was disabled or deleted is
    // still reported: it is still held, and revoking it by name is still what takes it off.
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
        for (Identifier ability : abilities.sources().keys()) {
            Component name = DefinitionText.name(ability, "ability");
            Component from = Component.literal(abilities.sources().of(ability).stream()
                    .map(Identifier::toString).sorted().reduce((a, b) -> a + ", " + b).orElse("-"));
            source.sendSuccess(() -> Component.translatable("command.mxt.ability.list.line", name, from), false);
        }
        return 1;
    }

    private static int grant(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Reference<Ability> ability = ResourceArgument.getResource(ctx, "ability", MxtResourceKeys.ABILITY);
        Identifier id = HolderHelper.id(ability);
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
        if (MxtDatapackRegistries.isDisabled(MxtResourceKeys.ABILITY, ability)) {
            source.sendFailure(Component.translatable("command.mxt.ability.unknown", id.toString()));
            return 0;
        }
        int granted = 0;
        for (Entity target : targets) {
            AbilityAttachment abilities = target.getData(MxtAttachments.ABILITY_HOLDER);
            if (abilities.grant(id, SOURCE)) {
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

    // Dropping a source only removes the ability when that was its last one, so a target that never held the
    // source is a failure rather than a silent success.
    private static int revoke(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Identifier id = IdentifierArgument.getId(ctx, "ability");
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
        int revoked = 0;
        for (Entity target : targets) {
            if (target.getData(MxtAttachments.ABILITY_HOLDER).revoke(id, SOURCE)) {
                revoked++;
                rebuild(target);
                source.sendSuccess(() -> Component.translatable("command.mxt.ability.revoked",
                        DefinitionText.name(id, "ability"), target.getDisplayName()), true);
            } else {
                source.sendFailure(Component.translatable("command.mxt.ability.revoke_failed",
                        target.getDisplayName(), DefinitionText.name(id, "ability")));
            }
        }
        return revoked;
    }

    // A source change moves which triggers the entity listens for.
    private static void rebuild(Entity target) {
        if (target instanceof LivingEntity living) AbilityEventBridge.rebuildTriggerSubscriptions(living);
    }

    private static int castAbility(CommandSourceStack source, Reference<Ability> ability) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (MxtDatapackRegistries.isDisabled(MxtResourceKeys.ABILITY, ability)) {
            source.sendFailure(Component.translatable("command.mxt.ability.cast_failed", "unknown_definition"));
            return 0;
        }
        UseResult result = AbilityService.use(ability, player, player.getData(MxtAttachments.ABILITY_HOLDER),
                player.getData(MxtAttachments.RESOURCE_HOLDER), player.level().getGameTime(), FormulaContext.of(player));
        if (!result.committed()) {
            source.sendFailure(Component.translatable("command.mxt.ability.cast_failed", result.failure().name()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.ability.cast_success", DefinitionText.name(ability, "ability")), true);
        return 1;
    }
}
