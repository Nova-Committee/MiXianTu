package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService.Result;
import com.iafenvoy.mxt.runtime.cultivation.CultivationToggleService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationToggleService.Failure;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /identity} command; also reachable as {@code /mxt identity}. The administrative half of the spirit
 * root and physique modules: the definitions are data and the actions are entities, so without it neither can be
 * handed out, taken back or switched off. Disabling keeps the guarantee the modules are built on - the body still
 * holds what it switched off - and results are reported per target.
 */
public final class IdentityCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> ROOT = literal("identity")
            .then(literal("root")
                    .then(literal("list")
                            .executes(ctx -> listRoots(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("target", EntityArgument.entity())
                                    .executes(ctx -> listRoots(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
                    .then(literal("grant").requires(IdentityCommand::mayChange)
                            .then(argument("targets", EntityArgument.entities())
                                    .then(argument("root", IdentifierArgument.id())
                                            .suggests(suggest(MxtResourceKeys.SPIRIT_ROOT))
                                            .executes(IdentityCommand::grantRoot))))
                    .then(literal("remove").requires(IdentityCommand::mayChange)
                            .then(argument("targets", EntityArgument.entities())
                                    .then(argument("root", IdentifierArgument.id())
                                            .suggests(suggest(MxtResourceKeys.SPIRIT_ROOT))
                                            .executes(IdentityCommand::removeRoot))))
                    .then(literal("enable").requires(IdentityCommand::mayChange)
                            .then(argument("targets", EntityArgument.entities())
                                    .then(argument("root", IdentifierArgument.id())
                                            .suggests(suggest(MxtResourceKeys.SPIRIT_ROOT))
                                            .executes(ctx -> toggleRoot(ctx, true)))))
                    .then(literal("disable").requires(IdentityCommand::mayChange)
                            .then(argument("targets", EntityArgument.entities())
                                    .then(argument("root", IdentifierArgument.id())
                                            .suggests(suggest(MxtResourceKeys.SPIRIT_ROOT))
                                            .executes(ctx -> toggleRoot(ctx, false))))))
            .then(literal("physique")
                    .then(literal("list")
                            .executes(ctx -> listPhysiques(ctx.getSource(), ctx.getSource().getPlayer()))
                            .then(argument("target", EntityArgument.entity())
                                    .executes(ctx -> listPhysiques(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
                    .then(literal("grant").requires(IdentityCommand::mayChange)
                            .then(argument("targets", EntityArgument.entities())
                                    .then(argument("physique", IdentifierArgument.id())
                                            .suggests(suggest(MxtResourceKeys.PHYSIQUE))
                                            .executes(IdentityCommand::grantPhysique))))
                    .then(literal("remove").requires(IdentityCommand::mayChange)
                            .then(argument("targets", EntityArgument.entities())
                                    .then(argument("physique", IdentifierArgument.id())
                                            .suggests(suggest(MxtResourceKeys.PHYSIQUE))
                                            .executes(IdentityCommand::removePhysique))))
                    .then(literal("enable").requires(IdentityCommand::mayChange)
                            .then(argument("targets", EntityArgument.entities())
                                    .then(argument("physique", IdentifierArgument.id())
                                            .suggests(suggest(MxtResourceKeys.PHYSIQUE))
                                            .executes(ctx -> togglePhysique(ctx, true)))))
                    .then(literal("disable").requires(IdentityCommand::mayChange)
                            .then(argument("targets", EntityArgument.entities())
                                    .then(argument("physique", IdentifierArgument.id())
                                            .suggests(suggest(MxtResourceKeys.PHYSIQUE))
                                            .executes(ctx -> togglePhysique(ctx, false))))));

    private static boolean mayChange(CommandSourceStack source) {
        return source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    // The live registry drives completion, so a disabled definition is not offered - but a body that still holds
    // one can name it by hand, which is what remove and disable are for.
    private static <T> SuggestionProvider<CommandSourceStack> suggest(ResourceKey<Registry<T>> key) {
        return (ctx, builder) -> SharedSuggestionProvider.suggest(MxtDatapackRegistries
                .holders(ctx.getSource().getServer().registryAccess(), key)
                .map(HolderHelper::id).map(Identifier::toString).sorted().toList(), builder);
    }

    private static int listRoots(CommandSourceStack source, @Nullable Entity target) {
        if (target == null) return noPlayer(source);
        SpiritIdentityAttachment identity = target.getData(MxtAttachments.SPIRIT_IDENTITY);
        return list(source, target, identity.spiritRoots(), "spirit_root", identity::isSpiritRootEnabled,
                root -> definition(MxtResourceKeys.SPIRIT_ROOT, root)
                        .map(SpiritRoot::element).filter(Elements::enabled)
                        .map(element -> (Component) DefinitionText.name(element, "element")).orElse(null),
                root -> definition(MxtResourceKeys.SPIRIT_ROOT, root).map(SpiritRoot::rarity).orElse("?"));
    }

    private static int listPhysiques(CommandSourceStack source, @Nullable Entity target) {
        if (target == null) return noPlayer(source);
        SpiritIdentityAttachment identity = target.getData(MxtAttachments.SPIRIT_IDENTITY);
        return list(source, target, identity.physiques(), "physique", identity::isPhysiqueEnabled,
                physique -> null,
                physique -> definition(MxtResourceKeys.PHYSIQUE, physique).map(Physique::rarity).orElse("?"));
    }

    // Resolved by id rather than through value(): the body can hold a reference to a definition that has since
    // been disabled or deleted. Disabled counts as present, deleted answers empty and reads as unknown.
    private static <T> Optional<T> definition(ResourceKey<Registry<T>> key, Holder<T> holder) {
        return MxtDatapackRegistries.rawHolder(key, HolderHelper.id(holder)).map(Holder::value);
    }

    // A definition whose entry a pack removed still appears, because the body holds a reference to it - exactly
    // the state an operator has to be able to see in order to clean it up.
    private static <T> int list(CommandSourceStack source, Entity target, List<Holder<T>> held, String category,
                                Function<Holder<T>, Boolean> active, Function<Holder<T>, Component> detail,
                                Function<Holder<T>, String> rarity) {
        source.sendSuccess(() -> Component.translatable("command.mxt.identity.header", target.getDisplayName()), false);
        List<Holder<T>> distinct = new ArrayList<>(new LinkedHashSet<>(held));
        if (distinct.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.empty"), false);
            return 1;
        }
        for (Holder<T> holder : distinct) {
            Component name = DefinitionText.name(holder, category);
            Component element = detail.apply(holder);
            Component rarityText = DefinitionText.rarity(rarity.apply(holder));
            Component state = Component.translatable(active.apply(holder)
                    ? "command.mxt.identity.on" : "command.mxt.identity.off");
            // A physique has no element to name, so the slot is empty rather than filled with a placeholder.
            Component bound = element == null ? Component.empty() : Component.literal(" · ").append(element);
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.line",
                    name, rarityText, bound, state), false);
        }
        return distinct.size();
    }

    private static int grantRoot(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(ctx, "root");
        return grant(ctx, MxtResourceKeys.SPIRIT_ROOT, id, "spirit_root",
                (entity, holder) -> CultivationIdentityService.grantSpiritRoot(entity, id, holder.value()));
    }

    private static int grantPhysique(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(ctx, "physique");
        return grant(ctx, MxtResourceKeys.PHYSIQUE, id, "physique",
                (entity, holder) -> CultivationIdentityService.grantPhysique(entity, id, holder.value(), FormulaContext.of(entity)));
    }

    private static <T> int grant(CommandContext<CommandSourceStack> ctx, ResourceKey<Registry<T>> key, Identifier id,
                                 String category, Grant<T> mutation) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
        Holder<T> holder = MxtDatapackRegistries.holder(key, id).orElse(null);
        if (holder == null) return unknown(source, id);
        int granted = 0;
        for (Entity target : targets) {
            if (!(target instanceof LivingEntity living)) {
                source.sendFailure(Component.translatable("command.mxt.identity.not_living", target.getDisplayName()));
                continue;
            }
            Result result = mutation.apply(living, holder);
            if (!result.changed()) {
                source.sendFailure(Component.translatable("command.mxt.identity.grant_failed",
                        DefinitionText.name(holder, category), target.getDisplayName(), reason(result.failure())));
                continue;
            }
            granted++;
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.granted",
                    DefinitionText.name(holder, category), target.getDisplayName()), true);
        }
        return granted;
    }

    private static int removeRoot(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(ctx, "root");
        return remove(ctx, id, "spirit_root", CultivationIdentityService::removeSpiritRoot);
    }

    private static int removePhysique(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(ctx, "physique");
        return remove(ctx, id, "physique", CultivationIdentityService::removePhysique);
    }

    private static int remove(CommandContext<CommandSourceStack> ctx, Identifier id, String category,
                              BiFunction<LivingEntity, Identifier, Boolean> mutation) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
        int removed = 0;
        for (Entity target : targets) {
            if (!(target instanceof LivingEntity living)) {
                source.sendFailure(Component.translatable("command.mxt.identity.not_living", target.getDisplayName()));
                continue;
            }
            if (!mutation.apply(living, id)) {
                source.sendFailure(Component.translatable("command.mxt.identity.remove_failed",
                        target.getDisplayName(), id.toString()));
                continue;
            }
            removed++;
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.removed",
                    DefinitionText.name(id, category), target.getDisplayName()), true);
        }
        return removed;
    }

    private static int toggleRoot(CommandContext<CommandSourceStack> ctx, boolean enabled) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(ctx, "root");
        return toggle(ctx, id, "spirit_root", enabled,
                (living, holder) -> CultivationToggleService.setSpiritRootEnabled(living, holder, enabled),
                SpiritIdentityAttachment::spiritRoots);
    }

    private static int togglePhysique(CommandContext<CommandSourceStack> ctx, boolean enabled) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(ctx, "physique");
        return toggle(ctx, id, "physique", enabled,
                (living, holder) -> CultivationToggleService.setPhysiqueEnabled(living, holder, enabled),
                SpiritIdentityAttachment::physiques);
    }

    // The holder is looked up among the held references, not in the registry, so an entry a pack has since
    // disabled can still be switched off: the state is about the body.
    private static <T> int toggle(CommandContext<CommandSourceStack> ctx, Identifier id, String category, boolean enabled,
                                  BiFunction<LivingEntity, Holder<T>, CultivationToggleService.Result> mutation,
                                  Function<SpiritIdentityAttachment, List<Holder<T>>> held) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
        int changed = 0;
        for (Entity target : targets) {
            if (!(target instanceof LivingEntity living)) {
                source.sendFailure(Component.translatable("command.mxt.identity.not_living", target.getDisplayName()));
                continue;
            }
            Holder<T> holder = find(held.apply(living.getData(MxtAttachments.SPIRIT_IDENTITY)), id);
            CultivationToggleService.Result result = holder == null
                    ? new CultivationToggleService.Result(false, Failure.NOT_HELD)
                    : mutation.apply(living, holder);
            if (!result.changed()) {
                source.sendFailure(Component.translatable("command.mxt.identity.toggle_failed",
                        target.getDisplayName(), id.toString(), reason(result.failure())));
                continue;
            }
            changed++;
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.toggled",
                    DefinitionText.name(id, category), Component.translatable(enabled
                            ? "command.mxt.identity.on" : "command.mxt.identity.off"), target.getDisplayName()), true);
        }
        return changed;
    }

    private static <T> Holder<T> find(List<Holder<T>> held, Identifier id) {
        for (Holder<T> holder : held) if (HolderHelper.id(holder).equals(id)) return holder;
        return null;
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

    @FunctionalInterface
    private interface Grant<T> {
        Result apply(LivingEntity entity, Holder<T> holder);
    }
}
