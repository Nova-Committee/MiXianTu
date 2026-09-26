package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.attachment.*;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.item.RiftComponent;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.resource.ResourceBar;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext.Values;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.ActualConcentrationContext;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.EnvironmentConcentrationContext;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.data.storage.builtin.CooldownDataStorage;
import com.iafenvoy.mxt.data.trigger.TriggerContext;
import com.iafenvoy.mxt.data.trigger.TriggerRule;
import com.iafenvoy.mxt.item.RiftAnchorItem;
import com.iafenvoy.mxt.item.block.entity.RiftBlockEntity;
import com.iafenvoy.mxt.registry.*;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.runtime.aura.AuraLookup;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.BreakthroughResult;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.Failure;
import com.iafenvoy.mxt.runtime.rift.RiftColors;
import com.iafenvoy.mxt.runtime.rift.RiftConnections;
import com.iafenvoy.mxt.runtime.trigger.TriggerDispatcher;
import com.iafenvoy.mxt.runtime.trigger.TriggerSubscription;
import com.iafenvoy.mxt.runtime.world.*;
import com.iafenvoy.mxt.runtime.world.SecretRealmService.Result;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Administration and diagnostics that exist only under {@code /mxt}: these nodes take no top-level alias, so
 * operator tooling never claims short generic spellings such as {@code /registries}.
 */
public final class MxtCommand {
    // The chat report lists at most this many problems; the log keeps all of them.
    private static final int MAX_REPORTED_PROBLEMS = 12;

    public static void attach(CommandBuildContext context, LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(literal("registries")
                        .then(literal("list").executes(ctx -> listRegistries(ctx.getSource())))
                        .then(literal("validate").executes(ctx -> validationStatus(ctx.getSource()))))
                .then(literal("attachment").then(literal("status").executes(ctx -> attachmentStatus(ctx.getSource()))))
                .then(literal("resource")
                        .then(argument("id", ResourceArgument.resource(context, MxtResourceKeys.RESOURCE))
                                .executes(ctx -> queryResource(ctx.getSource(), resource(ctx, "id")))
                                .then(literal("set").requires(ServerCommandManager::mayChange)
                                        .then(argument("value", DoubleArgumentType.doubleArg()).executes(ctx -> setResource(ctx.getSource(),
                                                resource(ctx, "id"), DoubleArgumentType.getDouble(ctx, "value")))))))
                .then(literal("resourcebar")
                        .executes(ctx -> listResourceBars(ctx.getSource(), null, null))
                        .then(argument("resource", ResourceArgument.resource(context, MxtResourceKeys.RESOURCE))
                                .executes(ctx -> listResourceBars(ctx.getSource(), resource(ctx, "resource"), null))
                                .then(argument("index", IntegerArgumentType.integer(0, 255))
                                        .executes(ctx -> listResourceBars(ctx.getSource(), resource(ctx, "resource"),
                                                IntegerArgumentType.getInteger(ctx, "index"))))))
                .then(literal("cultivate").then(literal("status").executes(ctx -> cultivateStatus(ctx.getSource()))))
                .then(literal("breakthrough").requires(ServerCommandManager::mayChange)
                        .then(argument("aura", ResourceArgument.resource(context, MxtResourceKeys.AURA))
                                .executes(ctx -> attemptBreakthrough(ctx.getSource(), aura(ctx)))))
                .then(literal("secret_realm").requires(ServerCommandManager::mayChange)
                        .then(literal("list").executes(ctx -> listSecretRealms(ctx.getSource())))
                        .then(literal("info").then(argument("dimension", IdentifierArgument.id())
                                .executes(ctx -> secretRealmInfo(ctx.getSource(), IdentifierArgument.getId(ctx, "dimension")))))
                        .then(literal("enter").then(argument("definition", ResourceArgument.resource(context, MxtResourceKeys.SECRET_REALM))
                                .executes(ctx -> enterSecretRealm(ctx.getSource(), secretRealm(ctx)))))
                        .then(literal("exit").executes(ctx -> exitSecretRealm(ctx.getSource())))
                        .then(literal("destroy").then(argument("dimension", IdentifierArgument.id())
                                .executes(ctx -> destroySecretRealm(ctx.getSource(), IdentifierArgument.getId(ctx, "dimension"))))))
                .then(literal("rift").requires(ServerCommandManager::mayChange)
                        .then(literal("info").then(argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> riftInfo(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos")))))
                        .then(literal("target").then(argument("pos", BlockPosArgument.blockPos())
                                .then(argument("dimension", IdentifierArgument.id())
                                        .suggests(MxtCommand::suggestDimensions)
                                        .executes(ctx -> setRiftTarget(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"),
                                                IdentifierArgument.getId(ctx, "dimension"))))))
                        .then(literal("color").then(argument("pos", BlockPosArgument.blockPos())
                                .then(argument("color", StringArgumentType.word())
                                        .executes(ctx -> setRiftColor(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"),
                                                StringArgumentType.getString(ctx, "color"))))))
                        .then(literal("place").then(argument("pos", BlockPosArgument.blockPos())
                                .then(argument("dimension", IdentifierArgument.id())
                                        .suggests(MxtCommand::suggestDimensions)
                                        .executes(ctx -> placeRift(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"),
                                                IdentifierArgument.getId(ctx, "dimension"))))))
                        .then(literal("bind").then(argument("dimension", IdentifierArgument.id())
                                .suggests(MxtCommand::suggestDimensions)
                                .executes(ctx -> bindRiftAnchor(ctx.getSource(), IdentifierArgument.getId(ctx, "dimension"), null))
                                .then(argument("color", StringArgumentType.word())
                                        .executes(ctx -> bindRiftAnchor(ctx.getSource(), IdentifierArgument.getId(ctx, "dimension"),
                                                StringArgumentType.getString(ctx, "color")))))))
                .then(literal("soul").then(literal("reclaim").requires(ServerCommandManager::mayChange)
                        .executes(ctx -> reclaimSoul(ctx.getSource()))))
                .then(literal("trigger")
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
                                                        EntityArgument.getEntity(ctx, "entity")))))));
    }

    private static int listRegistries(CommandSourceStack source) {
        String text = MxtDatapackRegistries.registries().stream()
                .map(key -> key.identifier() + "=" + MxtDatapackRegistries.size(key))
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable("command.mxt.registries.list", text), false);
        return MxtDatapackRegistries.registries().size();
    }

    // The registry arguments have already resolved their entries; naming the four lookups keeps the node bodies
    // readable, and each one passes the registry the argument was built from.
    private static Reference<Resource> resource(CommandContext<CommandSourceStack> ctx, String name) throws CommandSyntaxException {
        return ResourceArgument.getResource(ctx, name, MxtResourceKeys.RESOURCE);
    }

    private static Reference<Aura> aura(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ResourceArgument.getResource(ctx, "aura", MxtResourceKeys.AURA);
    }

    private static Reference<SecretRealm> secretRealm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ResourceArgument.getResource(ctx, "definition", MxtResourceKeys.SECRET_REALM);
    }

    private static int validationStatus(CommandSourceStack source) {
        int registryCount = MxtDatapackRegistries.registries().size();
        int entryCount = MxtDatapackRegistries.registries().stream()
                .mapToInt(MxtDatapackRegistries::size).sum();
        // The cache collects every problem it finds while it builds its indexes, so this reports the whole
        // list instead of the first thing that failed.
        List<String> problems = ServerCache.get().map(ServerCache::problems).orElse(List.of());
        if (problems.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.registries.validation_passed", registryCount, entryCount,
                    Component.translatable("command.mxt.registries.no_problems")), false);
            return 1;
        }
        source.sendFailure(Component.translatable("command.mxt.registries.validation_failed", registryCount, entryCount,
                Component.literal(summarize(problems))));
        return 0;
    }

    private static String summarize(List<String> problems) {
        int shown = Math.min(problems.size(), MAX_REPORTED_PROBLEMS);
        String text = String.join("; ", problems.subList(0, shown));
        return problems.size() > shown ? text + "; ...(+" + (problems.size() - shown) + ")" : text;
    }

    private static CompletableFuture<Suggestions> suggestRuleSignals(SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(ServerCache.get()
                .map(cache -> cache.triggerSignals().stream().map(Identifier::toString).toList())
                .orElse(List.of()), builder);
    }

    // Every signal a rule reacts to or a subscription listens to: the two sets a signal can be published against.
    private static CompletableFuture<Suggestions> suggestPublishedSignals(SuggestionsBuilder builder) {
        Set<String> ids = new LinkedHashSet<>();
        ServerCache.get().ifPresent(cache -> cache.triggerSignals().forEach(signal -> ids.add(signal.toString())));
        TriggerDispatcher.signals().forEach(signal -> ids.add(signal.toString()));
        return SharedSuggestionProvider.suggest(ids, builder);
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
                abilities.storage().count(CooldownDataStorage.class), curses.instances().size(), identity.spiritRoots().size(), identity.physiques().size()), false);
        return 1;
    }

    private static int queryResource(CommandSourceStack source, Reference<Resource> resource) {
        ServerPlayer player = source.getPlayer();
        if (player == null || MxtDatapackRegistries.isDisabled(MxtResourceKeys.RESOURCE, resource)) return 0;
        double value = player.getData(MxtAttachments.RESOURCE_HOLDER).get(resource);
        source.sendSuccess(() -> Component.translatable("command.mxt.resource.query", DefinitionText.name(resource, "resource"), value), false);
        return 1;
    }

    private static int setResource(CommandSourceStack source, Reference<Resource> resource, double value) {
        ServerPlayer player = source.getPlayer();
        if (player == null || MxtDatapackRegistries.isDisabled(MxtResourceKeys.RESOURCE, resource)) return 0;
        player.getData(MxtAttachments.RESOURCE_HOLDER).set(resource, value);
        source.sendSuccess(() -> Component.translatable("command.mxt.resource.set", DefinitionText.name(resource, "resource"), value), true);
        return 1;
    }

    private static int listResourceBars(CommandSourceStack source, @Nullable Reference<Resource> selection, Integer index) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        // A disabled definition is no more usable here than a missing one, which is what the old lookup said.
        if (selection != null && MxtDatapackRegistries.isDisabled(MxtResourceKeys.RESOURCE, selection)) {
            source.sendFailure(Component.translatable("command.mxt.resourcebar.unknown_resource", HolderHelper.id(selection).toString()));
            return 0;
        }
        List<Reference<Resource>> resources = selection != null ? List.of(selection)
                : MxtDatapackRegistries.holders(player.level().registryAccess(), MxtResourceKeys.RESOURCE).toList();
        if (resources.isEmpty()) {
            source.sendFailure(Component.translatable("command.mxt.resourcebar.unknown_resource", "null"));
            return 0;
        }
        int shown = 0;
        for (Reference<Resource> resource : resources) {
            List<ResourceBar> bars = resource.value().bars();
            if (index != null && index >= bars.size()) {
                source.sendFailure(Component.translatable("command.mxt.resourcebar.unknown_index",
                        HolderHelper.id(resource).toString(), index, bars.size()));
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
                    selection == null ? "all" : HolderHelper.id(selection).toString()));
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
        // A bar is declared on a value, but the two concentration sources are aura pools: the value names the
        // aura it carries, and a value that carries none has no concentration to report.
        Holder<Aura> aura = AuraLookup.holder(player, resource).orElse(null);
        if (context == ActualConcentrationContext.INSTANCE) {
            if (aura == null) return Optional.empty();
            AuraPool pool = AuraService.getPositionAura(player.level(), player.blockPosition()).pool(aura);
            return Optional.of(new Values(pool.amount(), 0.0D, pool.maximum(), -1L));
        }
        if (context == EnvironmentConcentrationContext.INSTANCE) {
            if (aura == null) return Optional.empty();
            AuraPool pool = AuraService.getSensedAura(player.level(), player.blockPosition()).pool(aura);
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
                .map(entry -> DefinitionText.name(entry.getKey(), "aura").getString() + "="
                        + String.format(Locale.ROOT, "%.2f", entry.getDoubleValue()))
                .collect(Collectors.joining(", ")));
        source.sendSuccess(() -> Component.translatable("command.mxt.cultivate.status", action, progress, spirit.nextCultivateTick()), false);
        return 1;
    }

    private static int attemptBreakthrough(CommandSourceStack source, Reference<Aura> aura) {
        ServerPlayer player = source.getPlayer();
        if (player == null || MxtDatapackRegistries.isDisabled(MxtResourceKeys.AURA, aura)) return 0;
        BreakthroughResult result = CultivationService.attempt(player, player.getData(MxtAttachments.CULTIVATION), player.getData(MxtAttachments.RESOURCE_HOLDER), HolderHelper.id(aura), FormulaContext.of(player), () -> true);
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
        source.sendSuccess(() -> Component.translatable("command.mxt.breakthrough.success", DefinitionText.name(aura, "aura")), true);
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

    private static int listSecretRealms(CommandSourceStack source) {
        List<SecretRealmRecord> records = SecretRealmRegistry.all();
        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.secret_realm.list.empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.secret_realm.list", records.size()), false);
        for (SecretRealmRecord record : records)
            source.sendSuccess(() -> Component.literal(describe(record, source.getServer())), false);
        return records.size();
    }

    private static int secretRealmInfo(CommandSourceStack source, Identifier dimension) {
        SecretRealmRecord record = SecretRealmRegistry.at(ResourceKey.create(Registries.DIMENSION, dimension)).orElse(null);
        if (record == null) {
            source.sendFailure(Component.translatable("command.mxt.secret_realm.unknown", dimension.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(describe(record, source.getServer())), false);
        return 1;
    }

    private static int enterSecretRealm(CommandSourceStack source, Reference<SecretRealm> definition) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        if (MxtDatapackRegistries.isDisabled(MxtResourceKeys.SECRET_REALM, definition)) {
            source.sendFailure(Component.translatable("command.mxt.secret_realm.unknown", HolderHelper.id(definition).toString()));
            return 0;
        }
        Result result = SecretRealmService.enter(player, source.getServer(), definition);
        if (!result.changed()) {
            source.sendFailure(result.message().orElseGet(() -> Component.translatable("command.mxt.secret_realm.enter_failed",
                    result.failure().name())));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.secret_realm.entered", DefinitionText.name(definition, "secret_realm")), true);
        return 1;
    }

    private static int exitSecretRealm(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        Result result = SecretRealmService.exit(player);
        if (!result.changed()) {
            source.sendFailure(result.message().orElseGet(() -> Component.translatable("command.mxt.secret_realm.exit_failed",
                    result.failure() == null ? "unknown" : result.failure().name())));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.secret_realm.exited"), true);
        return 1;
    }

    private static int destroySecretRealm(CommandSourceStack source, Identifier dimension) {
        SecretRealmRecord record = SecretRealmRegistry.at(ResourceKey.create(Registries.DIMENSION, dimension)).orElse(null);
        if (record == null) {
            source.sendFailure(Component.translatable("command.mxt.secret_realm.unknown", dimension.toString()));
            return 0;
        }
        if (!SecretRealmService.destroy(source.getServer(), record)) {
            source.sendFailure(Component.translatable("command.mxt.secret_realm.destroy_failed", dimension.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.secret_realm.destroyed", dimension.toString()), true);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggestDimensions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(context.getSource().getServer().levelKeys().stream()
                .map(key -> key.identifier().toString()).sorted().toList(), builder);
    }

    @Nullable
    private static RiftBlockEntity riftAt(CommandSourceStack source, BlockPos pos) {
        if (source.getLevel().getBlockEntity(pos) instanceof RiftBlockEntity rift) return rift;
        source.sendFailure(Component.translatable("command.mxt.rift.missing", formatPos(pos)));
        return null;
    }

    private static int riftInfo(CommandSourceStack source, BlockPos pos) {
        RiftBlockEntity rift = riftAt(source, pos);
        if (rift == null) return 0;
        ServerLevel level = source.getLevel();
        List<BlockPos> links = RiftConnections.connected(level, pos);
        int triangles = RiftConnections.loops(pos, links).size();
        source.sendSuccess(() -> Component.translatable("command.mxt.rift.info", formatPos(pos)), false);
        source.sendSuccess(() -> Component.translatable("command.mxt.rift.info.target", rift.target().toString()), false);
        source.sendSuccess(() -> Component.translatable("command.mxt.rift.info.color", RiftColors.format(RiftColors.resolve(rift)),
                Component.translatable(rift.color() == RiftColors.AUTO
                        ? "command.mxt.rift.color.auto" : "command.mxt.rift.color.override")), false);
        source.sendSuccess(() -> Component.translatable("command.mxt.rift.info.shape", links.size(), triangles,
                links.isEmpty() ? 1 : RiftConnections.componentSize(level, pos)), false);
        source.sendSuccess(() -> Component.translatable("command.mxt.rift.info.isolated",
                Component.translatable(RiftConnections.isolated(level, pos)
                        ? "command.mxt.rift.info.isolated.yes" : "command.mxt.rift.info.isolated.no")), false);
        return 1;
    }

    private static int setRiftTarget(CommandSourceStack source, BlockPos pos, Identifier dimension) {
        RiftBlockEntity rift = riftAt(source, pos);
        if (rift == null) return 0;
        rift.setTarget(dimension);
        source.sendSuccess(() -> Component.translatable("block.mxt.rift.retargeted", dimension.toString()), true);
        return 1;
    }

    private static int setRiftColor(CommandSourceStack source, BlockPos pos, String color) {
        RiftBlockEntity rift = riftAt(source, pos);
        if (rift == null) return 0;
        if (color.equalsIgnoreCase("auto")) {
            rift.setColor(RiftColors.AUTO);
            source.sendSuccess(() -> Component.translatable("command.mxt.rift.color.reset"), true);
            return 1;
        }
        int parsed = RiftColors.parse(color);
        if (parsed == RiftColors.AUTO) {
            source.sendFailure(Component.translatable("command.mxt.rift.color.invalid", color));
            return 0;
        }
        rift.setColor(parsed);
        source.sendSuccess(() -> Component.translatable("block.mxt.rift.recolored", RiftColors.format(parsed)), true);
        return 1;
    }

    private static int placeRift(CommandSourceStack source, BlockPos pos, Identifier dimension) {
        ServerLevel level = source.getLevel();
        if (level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).canBeReplaced()) {
            source.sendFailure(Component.translatable("command.mxt.rift.place.blocked", formatPos(pos)));
            return 0;
        }
        level.setBlock(pos, MxtBlocks.RIFT.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof RiftBlockEntity rift)
            rift.configure(dimension, RiftColors.AUTO);
        source.sendSuccess(() -> Component.translatable("command.mxt.rift.place.done", formatPos(pos), dimension.toString()), true);
        return 1;
    }

    private static int bindRiftAnchor(CommandSourceStack source, Identifier dimension, @Nullable String color) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof RiftAnchorItem)) {
            source.sendFailure(Component.translatable("command.mxt.rift.bind.missing"));
            return 0;
        }
        int parsed = RiftColors.AUTO;
        if (color != null && !color.equalsIgnoreCase("auto")) {
            parsed = RiftColors.parse(color);
            if (parsed == RiftColors.AUTO) {
                source.sendFailure(Component.translatable("command.mxt.rift.color.invalid", color));
                return 0;
            }
        }
        stack.set(MxtDataComponents.RIFT, new RiftComponent(dimension, parsed));
        source.sendSuccess(() -> Component.translatable("command.mxt.rift.bind.done", dimension.toString()), true);
        return 1;
    }

    private static String formatPos(BlockPos pos) {
        return "%d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ());
    }

    private static String describe(SecretRealmRecord record, MinecraftServer server) {
        return "%s #%d %s members=%d limit=%s owner=%s prepared=%s loaded=%s".formatted(
                record.dimension().identifier(), record.index(),
                record.definition().unwrapKey().map(key -> key.identifier().toString()).orElse("?"),
                record.members().size(), record.instance().maxMembers().map(String::valueOf).orElse("unlimited"),
                record.owner().map(UUID::toString).orElse("-"), record.prepared(),
                server.getLevel(record.dimension()) != null);
    }

    // Subscriptions are never persisted, so this is the only way to see what a running server has armed.
    private static int listTriggers(CommandSourceStack source, Entity target) {
        Entity entity = target == null ? source.getPlayer() : target;
        if (entity == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        List<TriggerSubscription> subscriptions = TriggerDispatcher.subscriptions(entity.getUUID());
        source.sendSuccess(() -> Component.translatable("command.mxt.trigger.list.header",
                entity.getDisplayName(), subscriptions.size()), false);
        subscriptions.forEach(subscription -> source.sendSuccess(() -> Component.translatable("command.mxt.trigger.list.entry",
                subscription.module(), subscription.identity(), subscription.trigger().signalType().toString(),
                subscription.state().name()), false));
        return subscriptions.size();
    }

    private static int listTriggerRules(CommandSourceStack source, Identifier signal) {
        List<Reference<TriggerRule>> rules = ServerCache.get().map(cache -> cache.triggerRules(signal)).orElse(List.of());
        source.sendSuccess(() -> Component.translatable("command.mxt.trigger.rules.header", signal.toString(), rules.size()), false);
        rules.forEach(rule -> source.sendSuccess(() -> Component.translatable("command.mxt.trigger.rules.entry",
                HolderHelper.id(rule).toString(),
                String.valueOf(MxtRegistries.ENTITY_ACTION_TYPE.getKey(rule.value().action().codec()))), false));
        return rules.size();
    }

    private static int publishTrigger(CommandSourceStack source, Identifier signal, Entity target) {
        Entity entity = target == null ? source.getPlayer() : target;
        if (entity == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        boolean heard = TriggerDispatcher.hasListener(signal);
        TriggerDispatcher.publish(signal, new TriggerContext().actor(entity).level(entity.level())
                .formula(FormulaContext.of(entity)), entity.level().getGameTime());
        source.sendSuccess(() -> Component.translatable(heard ? "command.mxt.trigger.published"
                : "command.mxt.trigger.published_unheard", signal.toString(), entity.getDisplayName()), true);
        return 1;
    }
}
