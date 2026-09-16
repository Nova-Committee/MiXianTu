package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.network.payload.HotbarConfigurationS2CPayload;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.ability.AbilityService;
import com.iafenvoy.mxt.runtime.ability.AbilityService.UseResult;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.network.PacketDistributor;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /ability} command; also reachable as {@code /mxt ability}. Casting keeps its operator
 * requirement in both positions: an alias changes where a node hangs, never what it may do.
 */
public final class AbilityCommand {
    private static final Identifier ABILITY_HOTBAR_MODE = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "ability");
    public static final LiteralArgumentBuilder<CommandSourceStack> ROOT = literal("ability")
            .executes(ctx -> openHotbarConfiguration(ctx.getSource()))
            .then(literal("cast").requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(argument("id", IdentifierArgument.id())
                            .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                    MxtDatapackRegistries.holders(ctx.getSource().getServer().registryAccess(), MxtResourceKeys.ABILITY)
                                            .map(HolderHelper::id).map(Identifier::toString).sorted().toList(), builder))
                            .executes(ctx -> castAbility(ctx.getSource(), IdentifierArgument.getId(ctx, "id")))));

    private static int openHotbarConfiguration(CommandSourceStack source) throws CommandSyntaxException {
        PacketDistributor.sendToPlayer(source.getPlayerOrException(), new HotbarConfigurationS2CPayload(ABILITY_HOTBAR_MODE));
        return 1;
    }

    private static int castAbility(CommandSourceStack source, Identifier id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        UseResult result = MxtDatapackRegistries.holder(MxtResourceKeys.ABILITY, id).map(ability -> AbilityService.use(ability, ability.value(), player,
                player.getData(MxtAttachments.ABILITY_HOLDER), player.getData(MxtAttachments.RESOURCE_HOLDER), player.level().getGameTime(), FormulaContext.of(player))).orElse(null);
        if (result == null || !result.committed()) {
            source.sendFailure(Component.translatable("command.mxt.ability.cast_failed", result == null ? "unknown_definition" : result.failure()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.ability.cast_success", DefinitionText.name(id, "ability")), true);
        return 1;
    }
}
