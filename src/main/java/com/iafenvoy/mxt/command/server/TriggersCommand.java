package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.trigger.TriggerContext;
import com.iafenvoy.mxt.data.trigger.TriggerRule;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.runtime.DatapackCache;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.runtime.trigger.TriggerDispatcher;
import com.iafenvoy.mxt.runtime.trigger.TriggerSubscription;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /curse} command; also reachable as {@code /mxt curse}. Reading the executing player's own curses needs
 * no permission, while changing anybody's - applying, removing or cleansing - keeps its operator requirement in
 * both positions. It is an administrator's mirror of the transactions content uses, going through
 * {@link CurseService}, so a curse applied here obeys its own condition and stacking.
 */
public final class TriggersCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("triggers")
                .then(literal("list")
                        .executes(ctx -> listTriggers(ctx.getSource(), null))
                        .then(argument("entity", EntityArgument.entity())
                                .executes(ctx -> listTriggers(ctx.getSource(), EntityArgument.getEntity(ctx, "entity")))))
                .then(literal("rules")
                        .then(argument("signal", IdentifierArgument.id())
                                .suggests((ctx, builder) -> suggestRuleSignals(builder))
                                .executes(ctx -> listTriggerRules(ctx.getSource(), IdentifierArgument.getId(ctx, "signal")))))
                .then(literal("publish").requires(ServerCommandManager::mayChange)
                        .then(argument("signal", IdentifierArgument.id())
                                .suggests((ctx, builder) -> suggestPublishedSignals(builder))
                                .executes(ctx -> publishTrigger(ctx.getSource(), IdentifierArgument.getId(ctx, "signal"), null))
                                .then(argument("entity", EntityArgument.entity())
                                        .executes(ctx -> publishTrigger(ctx.getSource(), IdentifierArgument.getId(ctx, "signal"),
                                                EntityArgument.getEntity(ctx, "entity"))))));
    }

    private static CompletableFuture<Suggestions> suggestRuleSignals(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(DatapackCache.get().triggerSignals().stream().map(Identifier::toString).toList(), builder);
    }

    // Every signal a rule reacts to or a subscription listens to: the two sets a signal can be published against.
    private static CompletableFuture<Suggestions> suggestPublishedSignals(SuggestionsBuilder builder) {
        Set<String> ids = new LinkedHashSet<>();
        DatapackCache.get().triggerSignals().forEach(signal -> ids.add(signal.toString()));
        TriggerDispatcher.signals().forEach(signal -> ids.add(signal.toString()));
        return SharedSuggestionProvider.suggest(ids, builder);
    }

    // Subscriptions are never persisted, so this is the only way to see what a running server has armed.
    private static int listTriggers(CommandSourceStack source, Entity target) throws CommandSyntaxException {
        Entity entity = target == null ? source.getEntityOrException() : target;
        List<TriggerSubscription> subscriptions = TriggerDispatcher.subscriptions(entity.getUUID());
        source.sendSuccess(() -> Component.translatable("command.mxt.trigger.list.header",
                entity.getDisplayName(), subscriptions.size()), false);
        subscriptions.forEach(subscription -> source.sendSuccess(() -> Component.translatable("command.mxt.trigger.list.entry",
                subscription.module(), subscription.identity(), subscription.trigger().signalType().toString(),
                subscription.state().name()), false));
        return subscriptions.size();
    }

    private static int listTriggerRules(CommandSourceStack source, Identifier signal) {
        List<Reference<TriggerRule>> rules = DatapackCache.get().triggerRules(signal);
        source.sendSuccess(() -> Component.translatable("command.mxt.trigger.rules.header", signal.toString(), rules.size()), false);
        rules.forEach(rule -> source.sendSuccess(() -> Component.translatable("command.mxt.trigger.rules.entry",
                HolderHelper.id(rule).toString(),
                String.valueOf(MxtRegistries.ENTITY_ACTION_TYPE.getKey(rule.value().action().codec()))), false));
        return rules.size();
    }

    private static int publishTrigger(CommandSourceStack source, Identifier signal, Entity target) throws CommandSyntaxException {
        Entity entity = target == null ? source.getEntityOrException() : target;
        boolean heard = TriggerDispatcher.hasListener(signal);
        TriggerDispatcher.publish(signal, new TriggerContext().actor(entity).level(entity.level())
                .formula(FormulaContext.of(entity)), entity.level().getGameTime());
        source.sendSuccess(() -> Component.translatable(heard ? "command.mxt.trigger.published"
                : "command.mxt.trigger.published_unheard", signal.toString(), entity.getDisplayName()), true);
        return 1;
    }
}
