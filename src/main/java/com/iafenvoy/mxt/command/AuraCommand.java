package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.cultivation.CultivationProfile;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.network.payload.HotbarConfigurationS2CPayload;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationProfiles;
import com.iafenvoy.mxt.runtime.world.AuraChunkTicker;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.runtime.world.SpiritStoneVein;
import com.iafenvoy.mxt.runtime.world.SpiritStoneVein.Result;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.TooltipText;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /aura} command; also reachable as {@code /mxt aura}. The subtree is built per call rather
 * than cached: a {@code LiteralArgumentBuilder} is one mutable node, and the two surfaces it is
 * registered in must not share it.
 */
public final class AuraCommand {
    private static final Identifier SPIRIT_HOTBAR_MODE = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "spirit");
    public static final LiteralArgumentBuilder<CommandSourceStack> ROOT = literal("aura")
            .executes(ctx -> openHotbarConfiguration(ctx.getSource()))
            .then(literal("query")
                    .executes(ctx -> queryAura(ctx.getSource(), null))
                    .then(argument("type", IdentifierArgument.id())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                    MxtDatapackRegistries.holders(ctx.getSource().getServer().registryAccess(), MxtResourceKeys.ELEMENT)
                                            .map(HolderHelper::id).map(Identifier::toString).sorted().toList(), builder))
                            .executes(ctx -> queryAura(ctx.getSource(), IdentifierArgument.getId(ctx, "type")))))
            .then(literal("vein").executes(ctx -> queryVein(ctx.getSource())))
            .then(literal("cache")
                    .then(literal("clear")
                            .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                            .executes(ctx -> clearAuraCache(ctx.getSource(), 3))
                            .then(argument("radius", IntegerArgumentType.integer(0, 32))
                                    .executes(ctx -> clearAuraCache(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))))));

    private static int openHotbarConfiguration(CommandSourceStack source) throws CommandSyntaxException {
        PacketDistributor.sendToPlayer(source.getPlayerOrException(), new HotbarConfigurationS2CPayload(SPIRIT_HOTBAR_MODE));
        return 1;
    }

    private static int queryAura(CommandSourceStack source, Identifier type) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
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

    private static int queryVein(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Result vein = SpiritStoneVein.inspect(player.level(), player.blockPosition());
        source.sendSuccess(() -> Component.translatable("command.mxt.aura.vein", vein.blocks(), vein.grade().name().toLowerCase(Locale.ROOT)), false);
        return vein.blocks();
    }

    private static int clearAuraCache(CommandSourceStack source, int radius) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int cleared = AuraChunkTicker.clearBlockAuraCachesAround(player.level(), player.blockPosition(), radius);
        source.sendSuccess(() -> Component.translatable("command.mxt.aura.cache.cleared", cleared, radius), false);
        return cleared;
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
        return CultivationProfiles.findServer(resource).flatMap(CultivationProfile::auraType)
                .map(type -> base.copy().append(" (").append(DefinitionText.name(type, "element")).append(")")).orElse(base);
    }

    private static Component sourceName(AuraResult aura) {
        String category = switch (aura.sourceKind()) {
            case BIOME -> "biome";
            case DIMENSION -> "dimension";
            case FORMATION -> "formation";
            case CUSTOM, CHUNK -> "aura_zone";
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
}
