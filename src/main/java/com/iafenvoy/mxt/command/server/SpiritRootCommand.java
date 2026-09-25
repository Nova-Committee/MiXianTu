package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.command.Suggestions;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationIdentityService.Result;
import com.iafenvoy.mxt.runtime.cultivation.CultivationToggleService;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.TooltipText;
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
import net.minecraft.network.chat.MutableComponent;
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
 * The {@code /spirit_root} command; also reachable as {@code /mxt spirit_root}. The administrative half of the
 * spirit root module: the definitions are data and the actions are entities, so without it a root can be neither
 * handed out, taken back nor switched off. Disabling keeps the guarantee the module is built on - the body still
 * holds what it switched off - and results are reported per target.
 */
public final class SpiritRootCommand {
    private SpiritRootCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("spirit_root")
                .then(literal("list")
                        .executes(ctx -> list(ctx.getSource(), ctx.getSource().getPlayer()))
                        .then(argument("target", EntityArgument.entity())
                                .executes(ctx -> list(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
                .then(literal("grant").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("root", ResourceArgument.resource(context, MxtResourceKeys.SPIRIT_ROOT))
                                        .executes(SpiritRootCommand::grant))))
                // remove, enable and disable all name a reference the body holds rather than one the pack
                // still provides, so they stay IdentifierArgument.
                .then(literal("remove").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("root", IdentifierArgument.id())
                                        .suggests(Suggestions.enabledIds(MxtResourceKeys.SPIRIT_ROOT))
                                        .executes(SpiritRootCommand::remove))))
                .then(literal("enable").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("root", IdentifierArgument.id())
                                        .suggests(Suggestions.enabledIds(MxtResourceKeys.SPIRIT_ROOT))
                                        .executes(ctx -> toggle(ctx, true)))))
                .then(literal("disable").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("root", IdentifierArgument.id())
                                        .suggests(Suggestions.enabledIds(MxtResourceKeys.SPIRIT_ROOT))
                                        .executes(ctx -> toggle(ctx, false)))));
    }

    // A definition whose entry a pack removed still appears, because the body holds a reference to it - exactly
    // the state an operator has to be able to see in order to clean it up.
    private static int list(CommandSourceStack source, @Nullable Entity target) {
        if (target == null) return noPlayer(source);
        SpiritIdentityAttachment identity = target.getData(MxtAttachments.SPIRIT_IDENTITY);
        source.sendSuccess(() -> Component.translatable("command.mxt.identity.header.spirit_root", target.getDisplayName()), false);
        List<Holder<SpiritRoot>> distinct = new ArrayList<>(new LinkedHashSet<>(identity.spiritRoots()));
        if (distinct.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.empty"), false);
            return 1;
        }
        for (Holder<SpiritRoot> root : distinct) {
            SpiritRoot definition = definition(root).orElse(null);
            // A root whose definition is gone has neither elements to name nor a rarity to read.
            Component elements = definition == null ? null : elements(definition);
            Component rarity = DefinitionText.rarity(definition == null ? "?" : definition.rarity());
            Component state = Component.translatable(identity.isSpiritRootEnabled(root)
                    ? "command.mxt.identity.on" : "command.mxt.identity.off");
            Component bound = elements == null ? Component.empty() : Component.literal(" · ").append(elements);
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.line",
                    DefinitionText.name(root, "spirit_root"), rarity, bound, state), false);
        }
        return distinct.size();
    }

    // A root of several elements names every live one, with its share when the share is not the whole of it; a
    // root whose elements are all switched off names none.
    private static @Nullable Component elements(SpiritRoot definition) {
        List<MutableComponent> live = definition.elements().stream().filter(entry -> Elements.enabled(entry.element()))
                .map(SpiritRootCommand::elementName).toList();
        if (live.isEmpty()) return null;
        MutableComponent joined = Component.empty();
        for (int index = 0; index < live.size(); index++) {
            if (index > 0) joined.append("/");
            joined.append(live.get(index));
        }
        return joined;
    }

    private static MutableComponent elementName(SpiritRoot.ElementWeight entry) {
        MutableComponent name = Component.empty().append(DefinitionText.name(entry.element(), "element"));
        return entry.weight() == 1.0D ? name : name.append(Component.literal(" " + TooltipText.number(entry.weight())));
    }

    private static int grant(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Reference<SpiritRoot> root = ResourceArgument.getResource(ctx, "root", MxtResourceKeys.SPIRIT_ROOT);
        // The argument has already found the entry, so this is only the mxt:disabled half of the old lookup.
        if (MxtDatapackRegistries.isDisabled(MxtResourceKeys.SPIRIT_ROOT, root))
            return unknown(source, HolderHelper.id(root));
        int granted = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            if (!(target instanceof LivingEntity living)) {
                source.sendFailure(Component.translatable("command.mxt.identity.not_living", target.getDisplayName()));
                continue;
            }
            Result result = CultivationIdentityService.grantSpiritRoot(living, HolderHelper.id(root), root.value());
            if (!result.changed()) {
                source.sendFailure(Component.translatable("command.mxt.identity.grant_failed",
                        DefinitionText.name(root, "spirit_root"), target.getDisplayName(), reason(result.failure())));
                continue;
            }
            granted++;
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.granted",
                    DefinitionText.name(root, "spirit_root"), target.getDisplayName()), true);
        }
        return granted;
    }

    private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Identifier id = IdentifierArgument.getId(ctx, "root");
        int removed = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            if (!(target instanceof LivingEntity living)) {
                source.sendFailure(Component.translatable("command.mxt.identity.not_living", target.getDisplayName()));
                continue;
            }
            if (!CultivationIdentityService.removeSpiritRoot(living, id)) {
                source.sendFailure(Component.translatable("command.mxt.identity.remove_failed",
                        target.getDisplayName(), id.toString()));
                continue;
            }
            removed++;
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.removed",
                    DefinitionText.name(id, "spirit_root"), target.getDisplayName()), true);
        }
        return removed;
    }

    // The holder is looked up among the held references, not in the registry, so an entry a pack has since
    // disabled can still be switched off: the state is about the body.
    private static int toggle(CommandContext<CommandSourceStack> ctx, boolean enabled) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Identifier id = IdentifierArgument.getId(ctx, "root");
        int changed = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            if (!(target instanceof LivingEntity living)) {
                source.sendFailure(Component.translatable("command.mxt.identity.not_living", target.getDisplayName()));
                continue;
            }
            Holder<SpiritRoot> root = find(living.getData(MxtAttachments.SPIRIT_IDENTITY), id);
            CultivationToggleService.Result result = root == null
                    ? new CultivationToggleService.Result(false, CultivationToggleService.Failure.NOT_HELD)
                    : CultivationToggleService.setSpiritRootEnabled(living, root, enabled);
            if (!result.changed()) {
                source.sendFailure(Component.translatable("command.mxt.identity.toggle_failed",
                        target.getDisplayName(), id.toString(), reason(result.failure())));
                continue;
            }
            changed++;
            source.sendSuccess(() -> Component.translatable("command.mxt.identity.toggled",
                    DefinitionText.name(id, "spirit_root"), Component.translatable(enabled
                            ? "command.mxt.identity.on" : "command.mxt.identity.off"), target.getDisplayName()), true);
        }
        return changed;
    }

    private static Holder<SpiritRoot> find(SpiritIdentityAttachment identity, Identifier id) {
        for (Holder<SpiritRoot> root : identity.spiritRoots()) if (HolderHelper.id(root).equals(id)) return root;
        return null;
    }

    // Resolved by id rather than through value(): the body can hold a reference to a definition that has since
    // been disabled or deleted. Disabled counts as present, deleted answers empty and reads as unknown.
    private static Optional<SpiritRoot> definition(Holder<SpiritRoot> root) {
        return MxtDatapackRegistries.rawHolder(MxtResourceKeys.SPIRIT_ROOT, HolderHelper.id(root)).map(Holder::value);
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
