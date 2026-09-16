package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.item.FormationPlateComponent;
import com.iafenvoy.mxt.item.FormationPlateItem;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.formation.FormationInstance;
import com.iafenvoy.mxt.util.DefinitionText;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /mxt formation} subtree: diagnostics, and binding a plate to a formation. Also available as
 * a top-level {@code /formation} when the server option is enabled, and a disabled option removes only
 * the alias. {@code bind} is the one write, because nothing else could produce a usable plate.
 */
public final class FormationCommand {
    /**
     * The same subtree as a top-level {@code /formation}, registered only when the server option allows
     * it, and built by the same method as the {@code /mxt} copy so the two cannot drift.
     */
    public static final LiteralArgumentBuilder<CommandSourceStack> ROOT = literal("formation")
            .then(literal("list").executes(ctx -> listFormations(ctx.getSource())))
            .then(literal("info").executes(ctx -> formationCoverage(ctx.getSource())))
            .then(literal("bind")
                    .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(argument("formation", IdentifierArgument.id())
                            .suggests(FormationCommand::suggestAllowed)
                            .executes(ctx -> bind(ctx.getSource(), IdentifierArgument.getId(ctx, "formation")))));

    /**
     * Offers the plate's own allow list, because {@code IdentifierArgument} would otherwise complete to
     * nothing.
     */
    private static CompletableFuture<Suggestions> suggestAllowed(CommandContext<CommandSourceStack> context,
                                                                 SuggestionsBuilder builder) {
        ServerPlayer player = context.getSource().getPlayer();
        if (player == null) return builder.buildFuture();
        FormationPlateComponent plate = player.getMainHandItem()
                .getOrDefault(MxtDataComponents.FORMATION_PLATE, FormationPlateComponent.EMPTY);
        plate.admissible(formationRegistry()).stream()
                .map(holder -> holder.key().identifier())
                .filter(identifier -> identifier.toString().startsWith(builder.getRemainingLowerCase()))
                .forEach(identifier -> builder.suggest(identifier.toString()));
        return builder.buildFuture();
    }

    private static Registry<Formation> formationRegistry() {
        return MxtDatapackRegistries.registry(MxtResourceKeys.FORMATION);
    }

    /**
     * Writes a formation into the plate held in the main hand. Rebinding is allowed and overwrites, and a
     * typo leaves the plate as it was because the id is resolved first. Public so the server audit can
     * drive the command body with a {@code FakePlayer}.
     */
    public static int bind(CommandSourceStack source, Identifier formation) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Holder<Formation> definition = MxtDatapackRegistries
                .holder(MxtResourceKeys.FORMATION, formation)
                .orElseThrow(() -> new SimpleCommandExceptionType(
                        Component.translatable("command.mxt.formation.bind.unknown", formation.toString())).create());
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof FormationPlateItem)) {
            source.sendFailure(Component.translatable("command.mxt.formation.bind.missing"));
            return 0;
        }
        FormationPlateComponent plate = stack.getOrDefault(MxtDataComponents.FORMATION_PLATE, FormationPlateComponent.EMPTY);
        if (!plate.admits(definition)) {
            source.sendFailure(Component.translatable("command.mxt.formation.bind.denied",
                    DefinitionText.name(definition, "formation")));
            return 0;
        }
        stack.set(MxtDataComponents.FORMATION_PLATE, new FormationPlateComponent(plate.allowed(), Optional.of(definition)));
        source.sendSuccess(() -> Component.translatable("command.mxt.formation.bind.done",
                DefinitionText.name(definition, "formation")), false);
        return 1;
    }

    private static int listFormations(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Map<BlockPos, FormationInstance> formations = level.getData(MxtAttachments.FORMATION_WORLD).formations();
        String dimension = level.dimension().identifier().toString();
        if (formations.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.formation.list.empty", dimension), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.formation.list", dimension, formations.size()), false);
        formations.forEach((controller, snapshot) ->
                source.sendSuccess(() -> Component.literal(line(controller, snapshot)), false));
        return formations.size();
    }

    /**
     * Every match is reported rather than a single winner, because overlapping formations are exactly
     * what an operator is trying to see.
     */
    private static int formationCoverage(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        BlockPos position = player.blockPosition();
        List<String> covering = player.level().getData(MxtAttachments.FORMATION_WORLD).formations().entrySet().stream()
                .filter(entry -> entry.getKey().distSqr(position) <= entry.getValue().radius() * entry.getValue().radius())
                .map(entry -> line(entry.getKey(), entry.getValue()) + " d=" + Math.sqrt(entry.getKey().distSqr(position)))
                .sorted()
                .toList();
        if (covering.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.formation.info.empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.formation.info", covering.size()), false);
        covering.forEach(entry -> source.sendSuccess(() -> Component.literal(entry), false));
        return covering.size();
    }

    private static String line(BlockPos controller, FormationInstance formation) {
        return formation.formation()
                + " @ " + controller.getX() + " " + controller.getY() + " " + controller.getZ()
                + " r=" + formation.radius()
                + " owner=" + formation.owner().map(UUID::toString).orElse("-")
                + " upkeep=" + formation.maintenanceCount()
                + (formation.stored().isEmpty() ? "" : " stored=" + formation.stored());
    }
}
