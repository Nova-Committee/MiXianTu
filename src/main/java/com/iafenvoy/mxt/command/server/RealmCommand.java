package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.command.ChainReport;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.MinorStageService;
import com.iafenvoy.mxt.runtime.resource.ResourceService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /realm} command; also reachable as {@code /mxt realm}. It deals in cultivation stages
 * ({@code mxt:realm_stage}), which are not secret realms: {@code set} writes the source player's current stage, and
 * {@code chain} prints the ladder the named stage is a rung of.
 */
public final class RealmCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("realm")
                .then(literal("set").requires(ServerCommandManager::mayChange)
                        .then(argument("realm", ResourceArgument.resource(context, MxtResourceKeys.REALM_STAGE))
                                .executes(RealmCommand::set)))
                .then(literal("chain")
                        .then(argument("realm", ResourceArgument.resource(context, MxtResourceKeys.REALM_STAGE))
                                .executes(RealmCommand::chain)));
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Reference<RealmStage> realm = ResourceArgument.getResource(ctx, "realm", MxtResourceKeys.REALM_STAGE);
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        if (!CultivationService.setRealm(player.getData(MxtAttachments.CULTIVATION), HolderHelper.id(realm))) {
            source.sendFailure(Component.translatable("command.mxt.realm.set_failed", DefinitionText.name(realm, "realm_stage")));
            return 0;
        }
        // Arriving by command is arriving: the new stage's first minor stage is recorded like any other, so its
        // own unlocks apply without waiting for the next point of progress.
        MinorStageService.refresh(player, realm.value().aura(),
                ResourceService.formulaContext(player, realm.value().aura().value().resource(), FormulaContext.of(player)));
        source.sendSuccess(() -> Component.translatable("command.mxt.realm.set_success", DefinitionText.name(realm, "realm_stage")), true);
        return 1;
    }

    private static int chain(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Reference<RealmStage> target = ResourceArgument.getResource(ctx, "realm", MxtResourceKeys.REALM_STAGE);
        Identifier start = HolderHelper.id(target);
        Identifier aura = HolderHelper.id(target.value().aura());
        // The ladder in use is the enabled one, so a disabled stage breaks the chain here for the same reason the
        // server cache refuses to index such a chain.
        Map<Identifier, Reference<RealmStage>> stages = new LinkedHashMap<>();
        MxtDatapackRegistries.holders(source.getServer().registryAccess(), MxtResourceKeys.REALM_STAGE)
                .forEach(stage -> stages.put(HolderHelper.id(stage), stage));
        if (!stages.containsKey(start)) {
            source.sendFailure(Component.translatable("command.mxt.realm.chain.none", start.toString()));
            return 0;
        }
        // A ladder is one aura's stages: following the links backwards reaches its first stage. A link that leaves
        // the aura is not one of its rungs, and a stage that already has a predecessor keeps that one, so the walk
        // below cannot be turned into a cycle by hand-written data.
        Map<Identifier, Identifier> previous = new HashMap<>();
        for (Reference<RealmStage> stage : stages.values()) {
            if (!HolderHelper.id(stage.value().aura()).equals(aura)) continue;
            nextOf(stages, stage, aura).ifPresent(next -> previous.putIfAbsent(next, HolderHelper.id(stage)));
        }
        Identifier head = start;
        Set<Identifier> seen = new HashSet<>();
        seen.add(start);
        while (previous.containsKey(head) && seen.add(previous.get(head))) head = previous.get(head);

        List<Identifier> ids = new ArrayList<>();
        Set<Identifier> walked = new HashSet<>();
        Identifier cursor = head;
        while (cursor != null && walked.add(cursor)) {
            Reference<RealmStage> stage = stages.get(cursor);
            if (stage == null) break;
            ids.add(cursor);
            cursor = nextOf(stages, stage, aura).orElse(null);
        }
        int current = ids.indexOf(start);
        if (current < 0) {
            source.sendFailure(Component.translatable("command.mxt.realm.chain.none", start.toString()));
            return 0;
        }
        Component line = ChainReport.line(ids.stream().map(id -> DefinitionText.name(stages.get(id))).toList(), current);
        source.sendSuccess(() -> Component.translatable("command.mxt.realm.chain", aura.toString(), line), false);
        return ids.size();
    }

    // The next rung of the same ladder, or empty at its top: a link that names another aura or an absent stage ends
    // the walk rather than continuing into data that is not this chain.
    private static Optional<Identifier> nextOf(Map<Identifier, Reference<RealmStage>> stages,
                                               Reference<RealmStage> stage, Identifier aura) {
        return stage.value().nextRealm().map(HolderHelper::id).map(stages::get)
                .filter(next -> HolderHelper.id(next.value().aura()).equals(aura))
                .map(HolderHelper::id);
    }
}
