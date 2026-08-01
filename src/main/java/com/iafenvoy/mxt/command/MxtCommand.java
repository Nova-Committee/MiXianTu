package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.attachment.AbilityHolderData;
import com.iafenvoy.mxt.attachment.CurseHolderData;
import com.iafenvoy.mxt.attachment.ResourceHolderData;
import com.iafenvoy.mxt.attachment.SectData;
import com.iafenvoy.mxt.attachment.SectTerritoryData;
import com.iafenvoy.mxt.attachment.SpiritData;
import com.iafenvoy.mxt.data.sect.SectDefinition;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.ability.AbilityService.UseResult;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService.BreakthroughResult;
import com.iafenvoy.mxt.runtime.sect.SectService;
import com.iafenvoy.mxt.runtime.sect.SectService.Result;
import com.iafenvoy.mxt.runtime.sect.SectTerritoryEventBridge;
import com.iafenvoy.mxt.runtime.world.SoulService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Minimal server-only diagnostic surface; it never accepts client attachment payloads.
 */
public final class MxtCommand {
    public static void registerCommand(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(literal("mxt")
                .then(literal("registries")
                        .then(literal("list").executes(ctx -> listRegistries(ctx.getSource())))
                        .then(literal("validate").executes(ctx -> validationStatus(ctx.getSource()))))
                .then(literal("attachment").then(literal("status").executes(ctx -> attachmentStatus(ctx.getSource()))))
                .then(literal("resource")
                        .then(argument("id", StringArgumentType.word()).executes(ctx -> queryResource(ctx.getSource(), StringArgumentType.getString(ctx, "id")))
                                .then(literal("set").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                                        .then(argument("value", DoubleArgumentType.doubleArg()).executes(ctx -> setResource(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "id"), DoubleArgumentType.getDouble(ctx, "value")))))))
                .then(literal("cultivate").then(literal("status").executes(ctx -> cultivateStatus(ctx.getSource()))))
                .then(literal("ability").then(literal("cast").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(argument("id", StringArgumentType.word()).executes(ctx -> castAbility(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))))
                .then(literal("breakthrough").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .then(argument("realm", StringArgumentType.word()).executes(ctx -> attemptBreakthrough(ctx.getSource(), StringArgumentType.getString(ctx, "realm")))))
                .then(literal("sect").then(literal("claim").executes(ctx -> claimTerritory(ctx.getSource(), false)))
                        .then(literal("release").executes(ctx -> claimTerritory(ctx.getSource(), true))))
                .then(literal("soul").then(literal("reclaim").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                        .executes(ctx -> reclaimSoul(ctx.getSource())))));
    }

    private static int listRegistries(CommandSourceStack source) {
        String text = MxtDatapackRegistries.registries().stream()
                .map(key -> key.identifier() + "=" + MxtDatapackRegistries.size(key))
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable("mxt.command.registries.list", text), false);
        return MxtDatapackRegistries.registries().size();
    }

    private static int validationStatus(CommandSourceStack source) {
        int registryCount = MxtDatapackRegistries.registries().size();
        int entryCount = MxtDatapackRegistries.registries().stream()
                .mapToInt(MxtDatapackRegistries::size).sum();
        source.sendSuccess(() -> Component.translatable("mxt.command.registries.validation_passed", registryCount, entryCount,
                "native datapack registries loaded"), false);
        return 1;
    }

    private static int attachmentStatus(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("mxt.command.requires_player"));
            return 0;
        }
        AbilityHolderData abilities = player.getData(MxtAttachments.ABILITY_HOLDER);
        CurseHolderData curses = player.getData(MxtAttachments.CURSE_HOLDER);
        ResourceHolderData resources = player.getData(MxtAttachments.RESOURCE_HOLDER);
        SpiritData spirit = player.getData(MxtAttachments.SPIRIT_DATA);
        source.sendSuccess(() -> Component.translatable("mxt.command.attachment.status", resources.values().size(), abilities.sources().size(),
                abilities.cooldowns().size(), curses.instances().size(), spirit.spiritRoots().size(), spirit.physiques().size()), false);
        return 1;
    }

    private static int queryResource(CommandSourceStack source, String rawId) {
        ServerPlayer player = source.getPlayer();
        Identifier id = parseId(source, rawId);
        if (player == null || id == null) return 0;
        double value = player.getData(MxtAttachments.RESOURCE_HOLDER).get(id);
        source.sendSuccess(() -> Component.translatable("mxt.command.resource.query", id, value), false);
        return 1;
    }

    private static int setResource(CommandSourceStack source, String rawId, double value) {
        ServerPlayer player = source.getPlayer();
        Identifier id = parseId(source, rawId);
        if (player == null || id == null) return 0;
        player.getData(MxtAttachments.RESOURCE_HOLDER).set(id, value);
        source.sendSuccess(() -> Component.translatable("mxt.command.resource.set", id, value), true);
        return 1;
    }

    private static int cultivateStatus(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("mxt.command.requires_player"));
            return 0;
        }
        SpiritData spirit = player.getData(MxtAttachments.SPIRIT_DATA);
        Component action = spirit.cultivateAction().<Component>map(id -> Component.literal(id.toString())).orElseGet(() -> Component.translatable("mxt.command.none"));
        source.sendSuccess(() -> Component.translatable("mxt.command.cultivate.status", action, spirit.cultivationProgress(), spirit.nextCultivateTick()), false);
        return 1;
    }

    private static int castAbility(CommandSourceStack source, String rawId) {
        ServerPlayer player = source.getPlayer();
        Identifier id = parseId(source, rawId);
        if (player == null || id == null) return 0;
        UseResult result = MxtDatapackRegistries.get(MxtDatapackRegistries.ABILITY, id).map(definition -> AbilityService.use(id, definition, player,
                player.getData(MxtAttachments.ABILITY_HOLDER), player.getData(MxtAttachments.RESOURCE_HOLDER), player.level().getGameTime(), FormulaContext.EMPTY)).orElse(null);
        if (result == null || !result.committed()) {
            source.sendFailure(Component.translatable("mxt.command.ability.cast_failed", result == null ? "unknown_definition" : result.failure()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("mxt.command.ability.cast_success", id), true);
        return 1;
    }

    private static int attemptBreakthrough(CommandSourceStack source, String rawId) {
        ServerPlayer player = source.getPlayer();
        Identifier id = parseId(source, rawId);
        if (player == null || id == null) return 0;
        BreakthroughResult result = MxtDatapackRegistries.get(MxtDatapackRegistries.REALM_STAGE, id).map(definition -> CultivationService.attempt(player,
                player.getData(MxtAttachments.SPIRIT_DATA), player.getData(MxtAttachments.RESOURCE_HOLDER), id, definition, FormulaContext.EMPTY, () -> true)).orElse(null);
        if (result == null || !result.advanced()) {
            source.sendFailure(Component.translatable("mxt.command.breakthrough.failed", result == null ? "unknown_definition" : result.failure()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("mxt.command.breakthrough.success", id), true);
        return 1;
    }

    private static int claimTerritory(CommandSourceStack source, boolean release) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        SectData membership = player.getData(MxtAttachments.SECT);
        Identifier sect = membership.sect().orElse(null);
        if (sect == null) {
            source.sendFailure(Component.translatable("mxt.command.sect.not_member"));
            return 0;
        }
        SectDefinition definition = MxtDatapackRegistries.get(MxtDatapackRegistries.SECT, sect).orElse(null);
        if (definition == null) {
            source.sendFailure(Component.translatable("mxt.command.sect.unknown", sect));
            return 0;
        }
        SectTerritoryData territory = player.level().getChunkAt(player.blockPosition()).getData(MxtAttachments.SECT_TERRITORY);
        Result result = release
                ? SectService.releaseTerritory(membership, sect, definition, territory, SectTerritoryEventBridge.CLAIM)
                : SectService.claimTerritory(membership, sect, definition, territory, SectTerritoryEventBridge.CLAIM);
        if (!result.changed()) {
            source.sendFailure(Component.translatable("mxt.command.sect.territory_failed", result.failure()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable(release ? "mxt.command.sect.territory_released" : "mxt.command.sect.territory_claimed"), true);
        return 1;
    }

    private static int reclaimSoul(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        if (!SoulService.reclaim(player)) {
            source.sendFailure(Component.translatable("mxt.command.soul.no_reclaimable"));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("mxt.command.soul.reclaimed"), true);
        return 1;
    }

    private static Identifier parseId(CommandSourceStack source, String rawId) {
        Identifier id = Identifier.tryParse(rawId);
        if (id == null) source.sendFailure(Component.translatable("mxt.command.invalid_id", rawId));
        return id;
    }
}
