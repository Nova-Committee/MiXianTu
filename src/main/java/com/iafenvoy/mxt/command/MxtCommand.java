package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.attachment.SectAttachment;
import com.iafenvoy.mxt.attachment.SectTerritoryAttachment;
import com.iafenvoy.mxt.attachment.SpiritAttachment;
import com.iafenvoy.mxt.data.Sect;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.ability.AbilityService.UseResult;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.BreakthroughResult;
import com.iafenvoy.mxt.runtime.sect.SectService;
import com.iafenvoy.mxt.runtime.sect.SectService.Result;
import com.iafenvoy.mxt.runtime.sect.SectTerritoryEventBridge;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.SoulService;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.runtime.world.SpiritStoneVein;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.TooltipText;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.ChatFormatting;

import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Minimal server-only diagnostic surface; it never accepts client attachment payloads.
 */
public final class MxtCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal(MiXianTu.MOD_ID)
                .then(literal("registries")
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
                .then(literal("cultivate").then(literal("status").executes(ctx -> cultivateStatus(ctx.getSource()))))
                .then(literal("aura").then(literal("query")
                                .executes(ctx -> queryAura(ctx.getSource(), null))
                                .then(argument("type", IdentifierArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                MxtDatapackRegistries.holders(ctx.getSource().getServer().registryAccess(), MxtResourceKeys.ELEMENT)
                                                        .map(HolderHelper::id).map(Identifier::toString).sorted().toList(), builder))
                                        .executes(ctx -> queryAura(ctx.getSource(), IdentifierArgument.getId(ctx, "type")))))
                        .then(literal("vein").executes(ctx -> queryVein(ctx.getSource()))))
                .then(literal("ability").then(literal("cast").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(argument("id", IdentifierArgument.id())
                                .suggests((ctx, builder) -> suggestRegistry(ctx, builder, MxtResourceKeys.ABILITY))
                                .executes(ctx -> castAbility(ctx.getSource(), IdentifierArgument.getId(ctx, "id"))))))
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
                        .executes(ctx -> reclaimSoul(ctx.getSource())))));
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
        SpiritAttachment spirit = player.getData(MxtAttachments.SPIRIT_DATA);
        source.sendSuccess(() -> Component.translatable("command.mxt.attachment.status", resources.values().size(), abilities.sources().size(),
                abilities.cooldowns().size(), curses.instances().size(), spirit.spiritRoots().size(), spirit.physiques().size()), false);
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

    private static int cultivateStatus(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        SpiritAttachment spirit = player.getData(MxtAttachments.SPIRIT_DATA);
        Component action = spirit.cultivateAction().<Component>map(id -> DefinitionText.name(id, "cultivate_action")).orElseGet(() -> Component.translatable("command.mxt.none"));
        source.sendSuccess(() -> Component.translatable("command.mxt.cultivate.status", action, spirit.cultivationProgress(), spirit.nextCultivateTick()), false);
        return 1;
    }

    private static int queryAura(CommandSourceStack source, Identifier type) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        AuraResult aura = AuraService.getPositionAura(player.level(), player.blockPosition());
        if (type != null) {
            Reference<Resource> holder = MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, type).orElse(null);
            if (holder == null) {
                source.sendFailure(Component.translatable("command.mxt.aura.unknown_type", type.toString()));
                return 0;
            }
            AuraPool pool = aura.pool(holder);
            source.sendSuccess(() -> auraReport(aura, Map.of(holder, pool)), false);
            return 1;
        }
        source.sendSuccess(() -> auraReport(aura, aura.aura()), false);
        return 1;
    }

    private static Component auraReport(AuraResult aura, Map<? extends Holder<Resource>, AuraPool> pools) {
        MutableComponent report = Component.translatable("command.mxt.aura.query.header").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        report.append(Component.literal("\n")).append(Component.translatable("command.mxt.aura.query.source", sourceName(aura), Component.translatable("command.mxt.aura.source_kind." + aura.sourceKind().name().toLowerCase(Locale.ROOT))));
        report.append(Component.literal("\n")).append(Component.translatable("command.mxt.aura.query.kinds", aura.auraKinds().isEmpty() ? Component.translatable("command.mxt.none") : auraKinds(aura.auraKinds())));
        report.append(Component.literal("\n")).append(Component.translatable("command.mxt.aura.query.suppressed", aura.suppressCultivate()));
        report.append(Component.literal("\n")).append(Component.translatable("command.mxt.aura.query.elements").withStyle(ChatFormatting.GRAY));
        if (pools.isEmpty()) {
            report.append(Component.literal("\n  ")).append(Component.translatable("command.mxt.aura.query.empty").withStyle(ChatFormatting.DARK_GRAY));
            return report;
        }
        pools.forEach((resource, pool) -> report.append(Component.literal("\n  ")).append(Component.translatable("command.mxt.aura.query.element",
                resourceName(resource), auraNumber(pool.amount()), auraNumber(pool.maximum()), TooltipText.signed(pool.regenPerTick()))));
        return report;
    }

    private static Component resourceName(Holder<Resource> resource) {
        MutableComponent base = DefinitionText.name(resource, "resource");
        return resource.value().auraType().map(type -> base.copy().append(" (").append(DefinitionText.name(type, "element")).append(")")).orElse(base);
    }

    private static Component sourceName(AuraResult aura) {
        String category = switch (aura.sourceKind()) {
            case BIOME -> "biome";
            case DIMENSION -> "dimension";
            case FORMATION -> "formation";
            case CUSTOM -> "aura_zone";
            case CHUNK -> "aura_zone";
        };
        return DefinitionText.name(aura.source(), category);
    }

    private static Component auraKinds(List<Identifier> kinds) {
        MutableComponent result = Component.empty();
        for (int index = 0; index < kinds.size(); index++) {
            if (index > 0) result.append(", ");
            result.append(DefinitionText.name(kinds.get(index), "aura_kind"));
        }
        return result;
    }

    private static String auraNumber(double value) {
        return value == Double.POSITIVE_INFINITY ? "∞" : TooltipText.number(value);
    }

    private static int queryVein(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        SpiritStoneVein.Result vein = SpiritStoneVein.inspect(player.level(), player.blockPosition());
        source.sendSuccess(() -> Component.translatable("command.mxt.aura.vein", vein.blocks(), vein.grade().name().toLowerCase(Locale.ROOT)), false);
        return vein.blocks();
    }

    private static int castAbility(CommandSourceStack source, Identifier id) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        UseResult result = MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, id).map(ability -> AbilityService.use(ability, ability.value(), player,
                player.getData(MxtAttachments.ABILITY_HOLDER), player.getData(MxtAttachments.RESOURCE_HOLDER), player.level().getGameTime(), FormulaContext.of(player))).orElse(null);
        if (result == null || !result.committed()) {
            source.sendFailure(Component.translatable("command.mxt.ability.cast_failed", result == null ? "unknown_definition" : result.failure()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.ability.cast_success", DefinitionText.name(id, "ability")), true);
        return 1;
    }

    private static int attemptBreakthrough(CommandSourceStack source, Identifier id) {
        ServerPlayer player = source.getPlayer();
        if (player == null || MxtDatapackRegistries.get(MxtResourceKeys.RESOURCE, id).isEmpty())
            return 0;
        BreakthroughResult result = CultivationService.attempt(player, player.getData(MxtAttachments.SPIRIT_DATA), player.getData(MxtAttachments.RESOURCE_HOLDER), id, FormulaContext.of(player), () -> true);
        if (result == null || !result.advanced()) {
            source.sendFailure(Component.translatable("command.mxt.breakthrough.failed", result == null ? "unknown_definition" : result.failure()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.breakthrough.success", DefinitionText.name(id, "resource")), true);
        return 1;
    }

    private static int setRealm(CommandSourceStack source, Identifier realm) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        if (!CultivationService.setRealm(player.getData(MxtAttachments.SPIRIT_DATA), realm)) {
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
