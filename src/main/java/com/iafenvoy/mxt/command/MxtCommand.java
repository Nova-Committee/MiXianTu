package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment;
import com.iafenvoy.mxt.attachment.CultivationAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.SectAttachment;
import com.iafenvoy.mxt.attachment.SectTerritoryAttachment;
import com.iafenvoy.mxt.attachment.SpiritIdentityAttachment;
import com.iafenvoy.mxt.data.Sect;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceBar;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext.Values;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.ActualConcentrationContext;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.EnvironmentConcentrationContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.BreakthroughResult;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.Failure;
import com.iafenvoy.mxt.runtime.sect.SectService;
import com.iafenvoy.mxt.runtime.sect.SectService.Result;
import com.iafenvoy.mxt.runtime.sect.SectTerritoryEventBridge;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.runtime.world.SoulService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The administration and diagnostics that exist only under {@code /mxt}; it never accepts client
 * attachment payloads.
 *
 * <p>These nodes deliberately have no top-level alias. {@code /registries} or {@code /resource} on
 * their own would claim very short, very generic spellings for what is operator tooling, so the
 * command surface keeps them behind the mod's own prefix.</p>
 *
 * <p>The player-facing subtrees are not built here. Each lives in its own class next to the command it
 * implements - {@link AuraCommand}, {@link AbilityCommand}, {@link TechniqueCommand},
 * {@link DisplayCommand}, {@link TradeCommand} - and {@code CommandManager} is what attaches them both
 * here and at the top level.</p>
 */
public final class MxtCommand {
    /**
     * Attaches the diagnostic-only nodes to an existing root.
     */
    public static void attach(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(literal("registries")
                        .then(literal("list").executes(ctx -> listRegistries(ctx.getSource())))
                        .then(literal("validate").executes(ctx -> validationStatus(ctx.getSource()))))
                .then(literal("attachment").then(literal("status").executes(ctx -> attachmentStatus(ctx.getSource()))))
                .then(literal("resource")
                        .then(argument("id", IdentifierArgument.id())
                                .suggests((ctx, builder) -> suggestRegistry(ctx, builder, MxtResourceKeys.RESOURCE))
                                .executes(ctx -> queryResource(ctx.getSource(), IdentifierArgument.getId(ctx, "id")))
                                .then(literal("set").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                        .then(argument("value", DoubleArgumentType.doubleArg()).executes(ctx -> setResource(ctx.getSource(),
                                                IdentifierArgument.getId(ctx, "id"), DoubleArgumentType.getDouble(ctx, "value")))))))
                .then(literal("resourcebar")
                        .executes(ctx -> listResourceBars(ctx.getSource(), null, null))
                        .then(argument("resource", IdentifierArgument.id())
                                .suggests((ctx, builder) -> suggestRegistry(ctx, builder, MxtResourceKeys.RESOURCE))
                                .executes(ctx -> listResourceBars(ctx.getSource(), IdentifierArgument.getId(ctx, "resource"), null))
                                .then(argument("index", IntegerArgumentType.integer(0, 255))
                                        .executes(ctx -> listResourceBars(ctx.getSource(), IdentifierArgument.getId(ctx, "resource"),
                                                IntegerArgumentType.getInteger(ctx, "index"))))))
                .then(literal("cultivate").then(literal("status").executes(ctx -> cultivateStatus(ctx.getSource()))))
                .then(literal("breakthrough").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(argument("resource", IdentifierArgument.id())
                                .suggests((ctx, builder) -> suggestRegistry(ctx, builder, MxtResourceKeys.RESOURCE))
                                .executes(ctx -> attemptBreakthrough(ctx.getSource(), IdentifierArgument.getId(ctx, "resource")))))
                .then(literal("realm").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(literal("set").then(argument("realm", IdentifierArgument.id())
                                .suggests((ctx, builder) -> suggestRegistry(ctx, builder, MxtResourceKeys.REALM_STAGE))
                                .executes(ctx -> setRealm(ctx.getSource(), IdentifierArgument.getId(ctx, "realm"))))))
                .then(literal("sect").then(literal("claim").executes(ctx -> claimTerritory(ctx.getSource(), false)))
                        .then(literal("release").executes(ctx -> claimTerritory(ctx.getSource(), true))))
                .then(literal("soul").then(literal("reclaim").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> reclaimSoul(ctx.getSource()))));
    }

    private static int listRegistries(CommandSourceStack source) {
        String text = MxtDatapackRegistries.registries().stream()
                .map(key -> key.identifier() + "=" + MxtDatapackRegistries.size(key))
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable("command.mxt.registries.list", text), false);
        return MxtDatapackRegistries.registries().size();
    }

    private static <T> CompletableFuture<Suggestions> suggestRegistry(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            ResourceKey<Registry<T>> key) {
        return SharedSuggestionProvider.suggest(
                MxtDatapackRegistries.holders(context.getSource().getServer().registryAccess(), key)
                        .map(HolderHelper::id).map(Identifier::toString).sorted().toList(), builder);
    }

    private static int validationStatus(CommandSourceStack source) {
        int registryCount = MxtDatapackRegistries.registries().size();
        int entryCount = MxtDatapackRegistries.registries().stream()
                .mapToInt(MxtDatapackRegistries::size).sum();
        source.sendSuccess(() -> Component.translatable("command.mxt.registries.validation_passed", registryCount, entryCount,
                "native datapack registries loaded"), false);
        return 1;
    }

    private static int attachmentStatus(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        AbilityAttachment abilities = player.getData(MxtAttachments.ABILITY_HOLDER);
        CurseHolderAttachment curses = player.getData(MxtAttachments.CURSE_HOLDER);
        ResourceHolderAttachment resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        SpiritIdentityAttachment identity = player.getData(MxtAttachments.SPIRIT_IDENTITY);
        source.sendSuccess(() -> Component.translatable("command.mxt.attachment.status", resources.values().size(), abilities.sources().size(),
                abilities.cooldowns().size(), curses.instances().size(), identity.spiritRoots().size(), identity.physiques().size()), false);
        return 1;
    }

    private static int queryResource(CommandSourceStack source, Identifier id) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        Reference<Resource> resource = MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, id).orElse(null);
        if (resource == null) return 0;
        double value = player.getData(MxtAttachments.RESOURCE_HOLDER).get(resource);
        source.sendSuccess(() -> Component.translatable("command.mxt.resource.query", DefinitionText.name(id, "resource"), value), false);
        return 1;
    }

    private static int setResource(CommandSourceStack source, Identifier id, double value) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        Reference<Resource> resource = MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, id).orElse(null);
        if (resource == null) return 0;
        player.getData(MxtAttachments.RESOURCE_HOLDER).set(resource, value);
        source.sendSuccess(() -> Component.translatable("command.mxt.resource.set", DefinitionText.name(id, "resource"), value), true);
        return 1;
    }

    private static int listResourceBars(CommandSourceStack source, Identifier resourceId, Integer index) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        List<Reference<Resource>> resources = MxtDatapackRegistries.holders(player.level().registryAccess(), MxtResourceKeys.RESOURCE)
                .filter(holder -> resourceId == null || HolderHelper.id(holder).equals(resourceId)).toList();
        if (resources.isEmpty()) {
            source.sendFailure(Component.translatable("command.mxt.resourcebar.unknown_resource",
                    resourceId == null ? "null" : resourceId.toString()));
            return 0;
        }
        int shown = 0;
        for (Reference<Resource> resource : resources) {
            List<ResourceBar> bars = resource.value().bars();
            if (index != null && index >= bars.size()) {
                source.sendFailure(Component.translatable("command.mxt.resourcebar.unknown_index",
                        resourceId == null ? "null" : resourceId.toString(), index, bars.size()));
                return 0;
            }
            for (int barIndex = 0; barIndex < bars.size(); barIndex++) {
                if (index != null && barIndex != index) continue;
                sendResourceBar(source, player, resource, barIndex, bars.get(barIndex));
                shown++;
            }
        }
        if (shown == 0) {
            source.sendFailure(Component.translatable("command.mxt.resourcebar.empty",
                    resourceId == null ? "all" : resourceId.toString()));
            return 0;
        }
        return shown;
    }

    private static void sendResourceBar(CommandSourceStack source, ServerPlayer player, Reference<Resource> resource,
                                        int index, ResourceBar bar) {
        Identifier resourceId = HolderHelper.id(resource);
        String contextId = String.valueOf(MxtRegistries.RESOURCE_BAR_CONTEXT.getKey(bar.context()));
        Component displayName = bar.context().name(resourceId);
        Optional<Values> extracted = extractResourceBarValues(player, resource, bar.context());
        if (extracted.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.resourcebar.unavailable",
                    DefinitionText.name(resourceId, "resource"), index, displayName, contextId), false);
            return;
        }
        Values values = extracted.get();
        double maximum = bar.maximum().orElse(values.maximum());
        double percentage = maximum == values.minimum() ? 100.0D
                : (values.current() - values.minimum()) / (maximum - values.minimum()) * 100.0D;
        source.sendSuccess(() -> Component.translatable("command.mxt.resourcebar.entry",
                DefinitionText.name(resourceId, "resource"), index, displayName, contextId,
                formatRaw(values.current()), formatRaw(values.minimum()), formatRaw(maximum), formatRaw(percentage),
                bar.anchor().getSerializedName(), bar.order()), false);
    }

    private static Optional<Values> extractResourceBarValues(ServerPlayer player, Reference<Resource> resource,
                                                             ResourceBarContext context) {
        if (context == ActualConcentrationContext.INSTANCE) {
            AuraPool pool = AuraService.getPositionAura(player.level(), player.blockPosition()).pool(resource);
            return Optional.of(new Values(pool.amount(), 0.0D, pool.maximum(), -1L));
        }
        if (context == EnvironmentConcentrationContext.INSTANCE) {
            AuraPool pool = AuraService.getSensedAura(player.level(), player.blockPosition()).pool(resource);
            return Optional.of(new Values(pool.amount(), 0.0D, pool.maximum(), -1L));
        }
        return context.extract(player, resource);
    }

    private static String formatRaw(double value) {
        if (Double.isNaN(value)) return "NaN";
        if (value == Double.POSITIVE_INFINITY) return "∞";
        if (value == Double.NEGATIVE_INFINITY) return "-∞";
        return Double.toString(value);
    }

    private static int cultivateStatus(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        CultivationAttachment spirit = player.getData(MxtAttachments.CULTIVATION);
        Component action = spirit.cultivateAction().<Component>map(id -> DefinitionText.name(id, "cultivate_action")).orElseGet(() -> Component.translatable("command.mxt.none"));
        Component progress = spirit.cultivationProgresses().isEmpty() ? Component.translatable("command.mxt.none")
                : Component.literal(spirit.cultivationProgresses().object2DoubleEntrySet().stream()
                .map(entry -> DefinitionText.name(entry.getKey().value().resource(), "resource").getString() + "="
                        + String.format(Locale.ROOT, "%.2f", entry.getDoubleValue()))
                .collect(Collectors.joining(", ")));
        source.sendSuccess(() -> Component.translatable("command.mxt.cultivate.status", action, progress, spirit.nextCultivateTick()), false);
        return 1;
    }

    private static int attemptBreakthrough(CommandSourceStack source, Identifier id) {
        ServerPlayer player = source.getPlayer();
        if (player == null || MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, id).isEmpty())
            return 0;
        BreakthroughResult result = CultivationService.attempt(player, player.getData(MxtAttachments.CULTIVATION), player.getData(MxtAttachments.RESOURCE_HOLDER), id, FormulaContext.of(player), () -> true);
        if (result == null || !result.advanced()) {
            Component reason = result == null || result.failure() == null
                    ? Component.translatable("command.mxt.breakthrough.failure.unknown")
                    : result.failure() == Failure.INSUFFICIENT_RESOURCE && result.failedResource() != null
                    ? Component.translatable("command.mxt.breakthrough.failure.insufficient_resource",
                    DefinitionText.name(result.failedResource(), "resource"))
                    : Component.translatable("command.mxt.breakthrough.failure." + result.failure().name().toLowerCase(Locale.ROOT));
            source.sendFailure(Component.translatable("command.mxt.breakthrough.failed", reason));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.breakthrough.success", DefinitionText.name(id, "resource")), true);
        return 1;
    }

    private static int setRealm(CommandSourceStack source, Identifier realm) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        if (!CultivationService.setRealm(player.getData(MxtAttachments.CULTIVATION), realm)) {
            source.sendFailure(Component.translatable("command.mxt.realm.set_failed", DefinitionText.name(realm, "realm_stage")));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.realm.set_success", DefinitionText.name(realm, "realm_stage")), true);
        return 1;
    }

    private static int claimTerritory(CommandSourceStack source, boolean release) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        SectAttachment membership = player.getData(MxtAttachments.SECT);
        Holder<Sect> sect = membership.sect().orElse(null);
        if (sect == null) {
            source.sendFailure(Component.translatable("command.mxt.sect.not_member"));
            return 0;
        }
        SectTerritoryAttachment territory = player.level().getChunkAt(player.blockPosition()).getData(MxtAttachments.SECT_TERRITORY);
        Result result = release
                ? SectService.releaseTerritory(membership, sect, territory, SectTerritoryEventBridge.CLAIM)
                : SectService.claimTerritory(membership, sect, territory, SectTerritoryEventBridge.CLAIM);
        if (!result.changed()) {
            source.sendFailure(Component.translatable("command.mxt.sect.territory_failed", result.failure()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(release ? "command.mxt.sect.territory_released" : "command.mxt.sect.territory_claimed"), true);
        return 1;
    }

    private static int reclaimSoul(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        if (!SoulService.reclaim(player)) {
            source.sendFailure(Component.translatable("command.mxt.soul.no_reclaimable"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.soul.reclaimed"), true);
        return 1;
    }
}
