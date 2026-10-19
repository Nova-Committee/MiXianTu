package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationToggleService;
import com.iafenvoy.mxt.runtime.item.QualityService;
import com.iafenvoy.mxt.runtime.resulting.Failure;
import com.iafenvoy.mxt.runtime.resulting.Result;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.ResourceKeyArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

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
                        .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(argument("target", EntityArgument.entity())
                                .executes(ctx -> list(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
                .then(literal("grant").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("physique", ResourceArgument.resource(context, MxtResourceKeys.PHYSIQUE))
                                        .executes(PhysiqueCommand::grant))))
                // remove, enable and disable all name a reference the body holds rather than one the pack still
                // provides, so they take a bare registry key: ResourceArgument would refuse that id while parsing.
                .then(literal("remove").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("physique", ResourceArgument.resource(context, MxtResourceKeys.PHYSIQUE))
                                        .executes(PhysiqueCommand::remove))))
                .then(literal("enable").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("physique", ResourceKeyArgument.key(MxtResourceKeys.PHYSIQUE))
                                        .executes(ctx -> toggle(ctx, true)))))
                .then(literal("disable").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("physique", ResourceKeyArgument.key(MxtResourceKeys.PHYSIQUE))
                                        .executes(ctx -> toggle(ctx, false)))));
    }

    // A definition whose entry a pack removed still appears, because the body holds a reference to it - exactly
    // the state an operator has to be able to see in order to clean it up.
    private static int list(CommandSourceStack source, @NotNull Entity target) {
        SpiritIdentityAttachment identity = target.getData(MxtAttachments.SPIRIT_IDENTITY);
        source.sendSuccess(() -> Component.translatable("command.mxt.identity.header.physique", target.getDisplayName()), false);
        List<Holder<Physique>> distinct = new ArrayList<>(new LinkedHashSet<>(identity.physiques()));
        if (distinct.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.empty"), false);
            return 1;
        }
        for (Holder<Physique> physique : distinct) {
            Physique definition = physique.value();
            Component quality = definition.quality().isEmpty() ? Component.literal("-") : QualityService.displayName(definition.quality().orElseThrow());
            Component state = Component.translatable(identity.isPhysiqueEnabled(physique) ? "command.mxt.identity.on" : "command.mxt.identity.off");
            // A physique has no element to name, so the slot is empty rather than filled with a placeholder.
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.line",
                    DefinitionText.nameOrId(physique), quality, Component.empty(), state), false);
        }
        return distinct.size();
    }

    private static int grant(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Reference<Physique> physique = ResourceArgument.getResource(ctx, "physique", MxtResourceKeys.PHYSIQUE);
        int granted = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            if (!(target instanceof LivingEntity living)) {
                source.sendFailure(Component.translatable("command.mxt.identity.not_living", target.getDisplayName()));
                continue;
            }
            Result result = CultivationIdentityService.grantPhysique(living, physique, FormulaContext.of(living));
            if (result.failure() != null) {
                source.sendFailure(Component.translatable("command.mxt.identity.grant_failed",
                        DefinitionText.nameOrId(physique), target.getDisplayName(), reason(result.failure())));
                continue;
            }
            granted++;
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.granted",
                    DefinitionText.nameOrId(physique), target.getDisplayName()), true);
        }
        return granted;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Reference<Physique> physique = ResourceArgument.getResource(ctx, "physique", MxtResourceKeys.PHYSIQUE);
        int removed = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            if (!(target instanceof LivingEntity living)) {
                source.sendFailure(Component.translatable("command.mxt.identity.not_living", target.getDisplayName()));
                continue;
            }
            if (!CultivationIdentityService.removePhysique(living, physique)) {
                source.sendFailure(Component.translatable("command.mxt.identity.remove_failed", target.getDisplayName(), DefinitionText.nameOrId(physique)));
                continue;
            }
            removed++;
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.removed", DefinitionText.nameOrId(physique), target.getDisplayName()), true);
        }
        return removed;
    }

    // The holder is looked up among the held references, not in the registry, so an entry whose definition the
    // current pack no longer provides can still be switched off: the state is about the body.
    private static int toggle(CommandContext<CommandSourceStack> ctx, boolean enabled) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Holder<Physique> physique = ResourceArgument.getResource(ctx, "physique", MxtResourceKeys.PHYSIQUE);
        int changed = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            if (!(target instanceof LivingEntity living)) {
                source.sendFailure(Component.translatable("command.mxt.identity.not_living", target.getDisplayName()));
                continue;
            }
            CultivationToggleService.ToggleResult result = CultivationToggleService.setPhysiqueEnabled(living, physique, enabled);
            if (!result.changed()) {
                source.sendFailure(Component.translatable("command.mxt.identity.toggle_failed", target.getDisplayName(), HolderHelper.id(physique), reason(result.failure())));
                continue;
            }
            changed++;
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.toggled",
                    DefinitionText.nameOrId(physique), Component.translatable(enabled
                            ? "command.mxt.identity.on" : "command.mxt.identity.off"), target.getDisplayName()), true);
        }
        return changed;
    }

    // A switch that was already in the state asked for was not refused, so it has no reason to read out.
    private static Component reason(@Nullable Failure failure) {
        return failure == null ? Component.translatable("command.mxt.identity.unchanged") : failure.message();
    }
}
