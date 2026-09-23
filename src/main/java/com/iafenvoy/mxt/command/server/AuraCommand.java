package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.runtime.world.*;
import com.iafenvoy.mxt.runtime.world.SpiritStoneVein.Result;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.TooltipText;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /aura} command; also reachable as {@code /mxt aura}.
 */
public final class AuraCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("aura")
                .then(literal("query")
                        .executes(ctx -> queryAura(ctx.getSource(), null))
                        .then(literal("element")
                                .then(argument("element", ResourceArgument.resource(context, MxtResourceKeys.ELEMENT))
                                        .executes(ctx -> queryElement(ctx.getSource(),
                                                ResourceArgument.getResource(ctx, "element", MxtResourceKeys.ELEMENT)))))
                        .then(argument("type", ResourceArgument.resource(context, MxtResourceKeys.AURA))
                                .executes(ctx -> queryAura(ctx.getSource(),
                                        ResourceArgument.getResource(ctx, "type", MxtResourceKeys.AURA)))))
                .then(literal("vein").executes(ctx -> queryVein(ctx.getSource())))
                .then(literal("cache")
                        .then(literal("clear")
                                .requires(ServerCommandManager::mayChange)
                                .executes(ctx -> clearAuraCache(ctx.getSource(), 3))
                                .then(argument("radius", IntegerArgumentType.integer(0, 32))
                                        .executes(ctx -> clearAuraCache(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "radius"))))));
    }

    private static int queryAura(CommandSourceStack source, @Nullable Reference<Aura> selection) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        AuraResult aura = AuraService.getPositionAura(player.level(), player.blockPosition());
        if (selection != null) {
            if (MxtDatapackRegistries.isDisabled(MxtResourceKeys.AURA, selection)) {
                source.sendFailure(Component.translatable("command.mxt.aura.unknown_type", HolderHelper.id(selection).toString()));
                return 0;
            }
            AuraPool pool = aura.pool(selection);
            source.sendSuccess(() -> auraReport(aura, Map.of(selection, pool), null), false);
            return 1;
        }
        source.sendSuccess(() -> auraReport(aura, aura.aura(), null), false);
        return 1;
    }

    // The question is asked of the element because several auras can carry the same aura_type.
    private static int queryElement(CommandSourceStack source, Reference<Element> element) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (MxtDatapackRegistries.isDisabled(MxtResourceKeys.ELEMENT, element)) {
            source.sendFailure(Component.translatable("command.mxt.aura.unknown_element", HolderHelper.id(element).toString()));
            return 0;
        }
        AuraResult aura = AuraService.getPositionAura(player.level(), player.blockPosition());
        Map<Holder<Aura>, AuraPool> pools = new LinkedHashMap<>();
        aura.aura().forEach((holder, pool) -> {
            if (holder.value().auraType().filter(element::equals).isPresent()) pools.put(holder, pool);
        });
        source.sendSuccess(() -> auraReport(aura, pools, DefinitionText.name(element, "element")), false);
        return pools.size();
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

    private static Component auraReport(AuraResult aura, Map<? extends Holder<Aura>, AuraPool> pools, @Nullable Component filter) {
        MutableComponent report = Component.translatable("command.mxt.aura.query.header").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD);
        if (filter != null)
            report.append(Component.literal("\n")).append(Component.translatable("command.mxt.aura.query.filter", filter).withStyle(ChatFormatting.GRAY));
        report.append(Component.literal("\n")).append(Component.translatable("command.mxt.aura.query.source", sourceName(aura), Component.translatable("command.mxt.aura.source_kind." + aura.sourceKind().name().toLowerCase(Locale.ROOT))));
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

    private static Component resourceName(Holder<Aura> aura) {
        MutableComponent base = DefinitionText.name(aura, "aura");
        return aura.value().auraType()
                .filter(Elements::enabled)
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

    private static String auraNumber(double value) {
        return value == Double.POSITIVE_INFINITY ? "∞" : TooltipText.number(value);
    }
}
