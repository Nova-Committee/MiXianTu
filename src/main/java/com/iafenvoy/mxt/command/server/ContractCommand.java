package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.attachment.ContractAttachment;
import com.iafenvoy.mxt.attachment.CreatureSpiritAttachment;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.creature.ContractBehavior;
import com.iafenvoy.mxt.data.creature.ContractBehaviors;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.creature.*;
import com.iafenvoy.mxt.runtime.progression.ProgressionAdminService;
import com.iafenvoy.mxt.runtime.progression.ProgressionOwners;
import com.iafenvoy.mxt.runtime.progression.ProgressionService;
import com.iafenvoy.mxt.runtime.resulting.Result;
import com.iafenvoy.mxt.runtime.resulting.failures.ContractFailure;
import com.iafenvoy.mxt.runtime.resulting.failures.ProgressionFailure;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.PlayerNames;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /mxt contract} command; also reachable as {@code /contract}. It is the operator entry to the same
 * lifecycle the scroll, the bell and the bag use, so nothing here has a rule of its own.
 */
public final class ContractCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("contract")
                .then(literal("list")
                        .executes(ctx -> list(ctx, ctx.getSource().getPlayerOrException()))
                        .then(argument("player", EntityArgument.player())
                                .executes(ctx -> list(ctx, EntityArgument.getPlayer(ctx, "player")))))
                .then(literal("info")
                        .then(argument("target", EntityArgument.entity())
                                .executes(ContractCommand::info)))
                .then(literal("bind").requires(ServerCommandManager::mayChange)
                        .then(argument("player", EntityArgument.player())
                                .then(argument("target", EntityArgument.entity())
                                        .then(argument("contract_type", ResourceArgument.resource(context, MxtResourceKeys.CONTRACT_TYPE))
                                                .executes(ctx -> bind(ctx, false))
                                                .then(literal("force").executes(ctx -> bind(ctx, true)))))))
                .then(literal("break").requires(ServerCommandManager::mayChange)
                        .then(argument("target", EntityArgument.entity())
                                .executes(ctx -> release(ctx, false))
                                .then(literal("force").executes(ctx -> release(ctx, true)))))
                .then(literal("recall").requires(ServerCommandManager::mayChange)
                        .then(argument("target", EntityArgument.entity())
                                .executes(ctx -> recall(ctx, false))
                                .then(literal("force").executes(ctx -> recall(ctx, true)))))
                .then(literal("behavior").requires(ServerCommandManager::mayChange)
                        .then(argument("target", EntityArgument.entity())
                                .then(argument("behavior", IdentifierArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ContractBehaviors.known().stream().map(behavior -> behavior.id().toString()).sorted().toList(), builder))
                                        .executes(ctx -> behavior(ctx, false))
                                        .then(literal("force").executes(ctx -> behavior(ctx, true))))))
                // One creature holds one chain, so the level alone names what to write; whose chain it is comes
                // from the creature's own profile.
                .then(literal("level").requires(ServerCommandManager::mayChange)
                        .then(argument("target", EntityArgument.entity())
                                .then(argument("level", ResourceArgument.resource(context, MxtResourceKeys.PROGRESSION))
                                        .executes(ctx -> level(ctx, false))
                                        .then(literal("force").executes(ctx -> level(ctx, true))))));
    }

    // The list is the index, so an unloaded beast still counts and still shows; whether it is loaded is answered
    // per row rather than silently dropped.
    private static int list(CommandContext<CommandSourceStack> ctx, ServerPlayer target) {
        CommandSourceStack source = ctx.getSource();
        MinecraftServer server = source.getServer();
        BoundBeastService.prune(server, target.getUUID());
        List<BoundBeastsAttachment.Entry> entries = BoundBeastService.of(server, target.getUUID());
        if (entries.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.contract.list.empty", target.getDisplayName()), false);
            return 0;
        }
        String owner = PlayerNames.knownToServer(target.getUUID()).orElse(target.getUUID().toString());
        source.sendSuccess(() -> Component.translatable("command.mxt.contract.list.header", owner, entries.size()), false);
        for (BoundBeastsAttachment.Entry entry : entries) {
            boolean loaded = BoundBeastService.resolve(server, entry).isPresent();
            Component line = Component.translatable("command.mxt.contract.list.entry", DefinitionText.nameOrId(entry.type()),
                    entry.beast().toString(), Component.translatable(loaded
                            ? "command.mxt.contract.list.loaded" : "command.mxt.contract.list.unloaded"));
            source.sendSuccess(() -> line, false);
        }
        return entries.size();
    }

    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Entity entity = EntityArgument.getEntity(ctx, "target");
        if (!(entity instanceof Mob mob)) {
            source.sendFailure(ContractFailure.NOT_CONTRACTABLE.message());
            return 0;
        }
        ContractAttachment contract = mob.getData(MxtAttachments.CONTRACT);
        if (!contract.bound()) {
            source.sendFailure(ContractFailure.NOT_BOUND.message());
            return 0;
        }
        Holder<ContractType> type = contract.contractType().orElseThrow();
        String owner = Contracts.ownerOf(mob).map(id -> PlayerNames.knownToServer(id).orElse(id.toString())).orElse("-");
        source.sendSuccess(() -> Component.translatable("command.mxt.contract.info.type", DefinitionText.nameOrId(type)), false);
        source.sendSuccess(() -> Component.translatable("command.mxt.contract.info.owner", owner), false);
        source.sendSuccess(() -> Component.translatable("command.mxt.contract.info.bound_at", contract.boundAt()), false);
        source.sendSuccess(() -> Component.translatable("command.mxt.contract.info.recalled", Component.translatable(
                contract.recalled() ? "command.mxt.contract.info.recalled_on" : "command.mxt.contract.info.recalled_off")), false);
        source.sendSuccess(() -> Component.translatable("command.mxt.contract.info.behavior", contract.behavior().name()), false);
        // A profile that owns a chain makes this creature a progression owner; a profile that owns none reports
        // nothing, so a plain stat block reads exactly as it did before.
        ProgressionOwners.held(mob, mob.getExistingData(MxtAttachments.CREATURE_SPIRIT)
                        .flatMap(CreatureSpiritAttachment::profile).map(HolderHelper::id).orElse(null))
                .ifPresent(held -> ProgressionService.current(held).ifPresent(current -> {
                    Component next = ProgressionService.next(held).map(DefinitionText::nameOrId)
                            .orElseGet(() -> Component.translatable("command.mxt.contract.info.level.top"));
                    source.sendSuccess(() -> Component.translatable("command.mxt.contract.info.level",
                            held.id().toString(), DefinitionText.nameOrId(current), next), false);
                    ProgressionService.masteryOf(mob, held).ifPresent(mastery -> source.sendSuccess(
                            () -> Component.translatable("command.mxt.contract.info.mastery", mastery.have(),
                                    mastery.required(), mastery.resource().toString()), false));
                }));
        int cooldown = type.value().recallCooldown();
        if (cooldown > 0) {
            long remaining = Math.max(0L, contract.recallAt() + cooldown - mob.level().getGameTime());
            // A stamp and a length decide this, so a creature that was never recalled reports the whole cooldown
            // rather than a negative remainder.
            if (contract.recallAt() >= 0L)
                source.sendSuccess(() -> Component.translatable("command.mxt.contract.info.cooldown", remaining), false);
        }
        return 1;
    }

    private static int bind(CommandContext<CommandSourceStack> ctx, boolean force) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer owner = EntityArgument.getPlayer(ctx, "player");
        Reference<ContractType> type = ResourceArgument.getResource(ctx, "contract_type", MxtResourceKeys.CONTRACT_TYPE);
        Entity target = EntityArgument.getEntity(ctx, "target");
        if (!(target instanceof Mob mob)) {
            source.sendFailure(ContractFailure.NOT_CONTRACTABLE.message());
            return 0;
        }
        Result result = ContractService.bind(type, owner, mob, force);
        if (result.failure() != null) {
            source.sendFailure(result.failure().message());
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.contract.bind_success", mob.getDisplayName(),
                DefinitionText.nameOrId(type), owner.getDisplayName()), true);
        return 1;
    }

    private static int release(CommandContext<CommandSourceStack> ctx, boolean force) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Entity target = EntityArgument.getEntity(ctx, "target");
        if (!(target instanceof Mob mob)) {
            source.sendFailure(ContractFailure.NOT_BOUND.message());
            return 0;
        }
        Result result = ContractService.release(mob, source.getEntity() == null
                ? mob.getUUID() : source.getEntity().getUUID(), force);
        if (result.failure() != null) {
            source.sendFailure(result.failure().message());
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.contract.break_success", mob.getDisplayName()), true);
        return 1;
    }

    private static int recall(CommandContext<CommandSourceStack> ctx, boolean force) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Entity target = EntityArgument.getEntity(ctx, "target");
        if (!(target instanceof Mob mob)) {
            source.sendFailure(ContractFailure.NOT_BOUND.message());
            return 0;
        }
        Result result = ContractService.requestRecall(mob, source.getEntity() == null
                ? mob.getUUID() : source.getEntity().getUUID(), force);
        if (result.failure() != null) {
            source.sendFailure(result.failure().message());
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.contract.recall_success", mob.getDisplayName()), true);
        return 1;
    }

    // Orders live in code rather than in a registry, so the id stays an IdentifierArgument with the framework's
    // own list as its completion; whether this creature takes the order is the service's answer, not the parser's.
    private static int behavior(CommandContext<CommandSourceStack> ctx, boolean force) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Entity target = EntityArgument.getEntity(ctx, "target");
        ContractBehavior behavior = ContractBehaviors.byId(IdentifierArgument.getId(ctx, "behavior")).orElse(null);
        if (!(target instanceof Mob mob) || behavior == null) {
            source.sendFailure(ContractFailure.UNSUPPORTED_BEHAVIOR.message());
            return 0;
        }
        Result result = ContractBehaviorService.request(mob, source.getEntity() == null
                ? mob.getUUID() : source.getEntity().getUUID(), behavior, force);
        if (result.failure() != null) {
            source.sendFailure(result.failure().message());
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.contract.behavior_success",
                mob.getDisplayName(), behavior.name()), true);
        return 1;
    }

    // A creature climbs one chain, so the level alone names what to write; whose chain it is comes from the
    // creature's own profile, and a profile that owns no chain is refused by the service with UNKNOWN_OWNER.
    private static int level(CommandContext<CommandSourceStack> ctx, boolean force) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Entity target = EntityArgument.getEntity(ctx, "target");
        Reference<Progression> level = ResourceArgument.getResource(ctx, "level", MxtResourceKeys.PROGRESSION);
        if (!(target instanceof LivingEntity living)) {
            source.sendFailure(ProgressionFailure.UNKNOWN_OWNER.message());
            return 0;
        }
        Result result = living.getExistingData(MxtAttachments.CREATURE_SPIRIT)
                .flatMap(CreatureSpiritAttachment::profile)
                .map(profile -> ProgressionAdminService.setLevel(living,
                        new ProgressionOwners.Owner<>(profile, living.getData(MxtAttachments.CREATURE_SPIRIT).profileLevels()),
                        level, force))
                .orElseGet(() -> Result.failure(ProgressionFailure.UNKNOWN_OWNER));
        if (result.failure() != null) {
            source.sendFailure(result.failure().message());
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.contract.level.done", living.getDisplayName(),
                DefinitionText.nameOrId(level)), true);
        return 1;
    }
}
