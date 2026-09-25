package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.command.Suggestions;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService.Result;
import com.iafenvoy.mxt.runtime.cultivation.CultivationToggleService;
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
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /physique} command; also reachable as {@code /mxt physique}. The administrative half of the physique
 * module: the definitions are data and the actions are entities, so without it a physique can be neither handed
 * out, taken back nor switched off. Disabling keeps the guarantee the module is built on - the body still holds
 * what it switched off - and results are reported per target.
 *
 * <p>A physique has no element, so its listing leaves that slot empty instead of showing a placeholder.
 */
public final class PhysiqueCommand {
    private PhysiqueCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("physique")
                .then(literal("list")
                        .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayer()))
                        .then(argument("target", EntityArgument.entity())
                                .executes(ctx -> list(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
                .then(literal("grant").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("physique", ResourceArgument.resource(context, MxtResourceKeys.PHYSIQUE))
                                        .executes(PhysiqueCommand::grant))))
                // remove, enable and disable all name a reference the body holds rather than one the pack
                // still provides, so they stay IdentifierArgument.
                .then(literal("remove").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("physique", IdentifierArgument.id())
                                        .suggests(Suggestions.enabledIds(MxtResourceKeys.PHYSIQUE))
                                        .executes(PhysiqueCommand::remove))))
                .then(literal("enable").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("physique", IdentifierArgument.id())
                                        .suggests(Suggestions.enabledIds(MxtResourceKeys.PHYSIQUE))
                                        .executes(ctx -> toggle(ctx, true)))))
                .then(literal("disable").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("physique", IdentifierArgument.id())
                                        .suggests(Suggestions.enabledIds(MxtResourceKeys.PHYSIQUE))
                                        .executes(ctx -> toggle(ctx, false)))));
    }

    // A definition whose entry a pack removed still appears, because the body holds a reference to it - exactly
    // the state an operator has to be able to see in order to clean it up.
    private static int list(CommandSourceStack source, @Nullable Entity target) {
        if (target == null) return noPlayer(source);
        SpiritIdentityAttachment identity = target.getData(MxtAttachments.SPIRIT_IDENTITY);
        source.sendSuccess(() -> Component.translatable("command.mxt.identity.header.physique", target.getDisplayName()), false);
        List<Holder<Physique>> distinct = new ArrayList<>(new LinkedHashSet<>(identity.physiques()));
        if (distinct.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.empty"), false);
            return 1;
        }
        for (Holder<Physique> physique : distinct) {
            Physique definition = definition(physique).orElse(null);
            Component rarity = DefinitionText.rarity(definition == null ? "?" : definition.rarity());
            Component state = Component.translatable(identity.isPhysiqueEnabled(physique)
                    ? "command.mxt.identity.on" : "command.mxt.identity.off");
            // A physique has no element to name, so the slot is empty rather than filled with a placeholder.
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.line",
                    DefinitionText.name(physique, "physique"), rarity, Component.empty(), state), false);
        }
        return distinct.size();
    }

    private static int grant(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Reference<Physique> physique = ResourceArgument.getResource(ctx, "physique", MxtResourceKeys.PHYSIQUE);
        // The argument has already found the entry, so this is only the mxt:disabled half of the old lookup.
        if (MxtDatapackRegistries.isDisabled(MxtResourceKeys.PHYSIQUE, physique))
            return unknown(source, HolderHelper.id(physique));
        int granted = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            if (!(target instanceof LivingEntity living)) {
                source.sendFailure(Component.translatable("command.mxt.identity.not_living", target.getDisplayName()));
                continue;
            }
            Result result = CultivationIdentityService.grantPhysique(living, HolderHelper.id(physique), physique.value(),
                    FormulaContext.of(living));
            if (!result.changed()) {
                source.sendFailure(Component.translatable("command.mxt.identity.grant_failed",
                        DefinitionText.name(physique, "physique"), target.getDisplayName(), reason(result.failure())));
                continue;
            }
            granted++;
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.granted",
                    DefinitionText.name(physique, "physique"), target.getDisplayName()), true);
        }
        return granted;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Identifier id = IdentifierArgument.getId(ctx, "physique");
        int removed = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            if (!(target instanceof LivingEntity living)) {
                source.sendFailure(Component.translatable("command.mxt.identity.not_living", target.getDisplayName()));
                continue;
            }
            if (!CultivationIdentityService.removePhysique(living, id)) {
                source.sendFailure(Component.translatable("command.mxt.identity.remove_failed",
                        target.getDisplayName(), id.toString()));
                continue;
            }
            removed++;
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.removed",
                    DefinitionText.name(id, "physique"), target.getDisplayName()), true);
        }
        return removed;
    }

    // The holder is looked up among the held references, not in the registry, so an entry a pack has since
    // disabled can still be switched off: the state is about the body.
    private static int toggle(CommandContext<CommandSourceStack> ctx, boolean enabled) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Identifier id = IdentifierArgument.getId(ctx, "physique");
        int changed = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            if (!(target instanceof LivingEntity living)) {
                source.sendFailure(Component.translatable("command.mxt.identity.not_living", target.getDisplayName()));
                continue;
            }
            Holder<Physique> physique = find(living.getData(MxtAttachments.SPIRIT_IDENTITY), id);
            CultivationToggleService.Result result = physique == null
                    ? new CultivationToggleService.Result(false, CultivationToggleService.Failure.NOT_HELD)
                    : CultivationToggleService.setPhysiqueEnabled(living, physique, enabled);
            if (!result.changed()) {
                source.sendFailure(Component.translatable("command.mxt.identity.toggle_failed",
                        target.getDisplayName(), id.toString(), reason(result.failure())));
                continue;
            }
            changed++;
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.toggled",
                    DefinitionText.name(id, "physique"), Component.translatable(enabled
                            ? "command.mxt.identity.on" : "command.mxt.identity.off"), target.getDisplayName()), true);
        }
        return changed;
    }

    private static Holder<Physique> find(SpiritIdentityAttachment identity, Identifier id) {
        for (Holder<Physique> physique : identity.physiques())
            if (HolderHelper.id(physique).equals(id)) return physique;
        return null;
    }

    // Resolved by id rather than through value(): the body can hold a reference to a definition that has since
    // been disabled or deleted. Disabled counts as present, deleted answers empty and reads as unknown.
    private static Optional<Physique> definition(Holder<Physique> physique) {
        return MxtDatapackRegistries.rawHolder(MxtResourceKeys.PHYSIQUE, HolderHelper.id(physique)).map(Holder::value);
    }

    private static int unknown(CommandSourceStack source, Identifier id) {
        source.sendFailure(Component.translatable("command.mxt.identity.unknown", id.toString()));
        return 0;
    }

    private static int noPlayer(CommandSourceStack source) {
        source.sendFailure(Component.translatable("command.mxt.requires_player"));
        return 0;
    }

    // Printed as its own name rather than as a sentence: the values are a fixed vocabulary shared with the
    // services, and a pack author reading the log wants the identifier.
    private static Component reason(@Nullable Enum<?> failure) {
        return Component.literal(failure == null ? "NO_CHANGE" : failure.name());
    }
}
