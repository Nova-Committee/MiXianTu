package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.command.ChainReport;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.DatapackCache;
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
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

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
        if (!CultivationService.setRealm(player.getData(MxtAttachments.CULTIVATION), realm)) {
            source.sendFailure(Component.translatable("command.mxt.realm.set_failed", DefinitionText.text(realm)));
            return 0;
        }
        // Arriving by command is arriving: the new stage's first minor stage is recorded like any other, so its
        // own unlocks apply without waiting for the next point of progress.
        MinorStageService.refresh(player, realm.value().aura(),
                ResourceService.formulaContext(player, realm.value().aura().value().resource(), FormulaContext.of(player)));
        source.sendSuccess(() -> Component.translatable("command.mxt.realm.set_success", DefinitionText.text(realm)), true);
        return 1;
    }

    private static int chain(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Reference<RealmStage> target = ResourceArgument.getResource(ctx, "realm", MxtResourceKeys.REALM_STAGE);
        Identifier start = HolderHelper.id(target);
        Holder<Aura> aura = target.value().aura();
        Identifier auraId = HolderHelper.id(aura);
        // The ladder in use is the loaded one, so a stage the pack no longer provides breaks the chain here for the
        // same reason the server cache refuses to index such a chain.
        Map<Identifier, Reference<RealmStage>> stages = new LinkedHashMap<>();
        MxtDatapackRegistries.holders(source.getServer().registryAccess(), MxtResourceKeys.REALM_STAGE)
                .forEach(stage -> stages.put(HolderHelper.id(stage), stage));
        if (!stages.containsKey(start)) {
            source.sendFailure(Component.translatable("command.mxt.realm.chain.none", start.toString()));
            return 0;
        }
        // A chain the cache validated is read straight from it. One it refused to index whole is still walked here
        // from the loaded stages, so an author sees what is written rather than only "no chain".
        List<Identifier> ids = DatapackCache.get().realmsOf(aura);
        int current = ids.indexOf(start);
        if (current < 0) {
            source.sendFailure(Component.translatable("command.mxt.realm.chain.none", start.toString()));
            return 0;
        }
        Component line = ChainReport.line(ids.stream().map(id -> DefinitionText.text(stages.get(id))).toList(), current);
        source.sendSuccess(() -> Component.translatable("command.mxt.realm.chain", auraId.toString(), line), false);
        return ids.size();
    }
}
