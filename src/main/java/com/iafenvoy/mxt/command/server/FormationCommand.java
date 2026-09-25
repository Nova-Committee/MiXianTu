package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.cost.context.CostContext;
import com.iafenvoy.mxt.data.cost.context.CostOrigin;
import com.iafenvoy.mxt.data.item.FormationPlateComponent;
import com.iafenvoy.mxt.item.FormationPlateItem;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.formation.FormationInstance;
import com.iafenvoy.mxt.runtime.formation.FormationService;
import com.iafenvoy.mxt.runtime.formation.FormationWorldTicker;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.TooltipText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /mxt formation} subtree: diagnostics, and binding a plate to a formation. A disabled server option
 * removes only the top-level {@code /formation} alias. {@code bind} is the one write, because nothing else could
 * produce a usable plate.
 */
public final class FormationCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("formation")
                .then(literal("list").executes(ctx -> listFormations(ctx.getSource())))
                .then(literal("info").executes(ctx -> formationCoverage(ctx.getSource())))
                .then(literal("upkeep").executes(ctx -> upkeepReport(ctx.getSource())))
                .then(literal("owners")
                        .then(argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> listOwners(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos")))
                                .then(literal("add")
                                        .requires(ServerCommandManager::mayChange)
                                        .then(argument("player", EntityArgument.player())
                                                .executes(ctx -> editOwner(ctx.getSource(),
                                                        BlockPosArgument.getBlockPos(ctx, "pos"),
                                                        EntityArgument.getPlayer(ctx, "player"), true))))
                                .then(literal("remove")
                                        .requires(ServerCommandManager::mayChange)
                                        .then(argument("player", EntityArgument.player())
                                                .executes(ctx -> editOwner(ctx.getSource(),
                                                        BlockPosArgument.getBlockPos(ctx, "pos"),
                                                        EntityArgument.getPlayer(ctx, "player"), false))))))
                .then(literal("bind")
                        .requires(ServerCommandManager::mayChange)
                        .then(argument("formation", ResourceArgument.resource(context, MxtResourceKeys.FORMATION))
                                .executes(ctx -> bind(ctx.getSource(),
                                        ResourceArgument.getResource(ctx, "formation", MxtResourceKeys.FORMATION)))));
    }

    // Rebinding is allowed and overwrites, and a definition the pack switched off is refused before the plate is
    // touched. Public so the server audit can drive the command body with a FakePlayer.
    public static int bind(CommandSourceStack source, Reference<Formation> definition) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (MxtDatapackRegistries.isDisabled(MxtResourceKeys.FORMATION, definition))
            throw new SimpleCommandExceptionType(Component.translatable("command.mxt.formation.bind.unknown",
                    HolderHelper.id(definition).toString())).create();
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

    // Every match is reported rather than a single winner: overlapping formations are what an operator wants to see.
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

    // A shared array is the point of the list: the operator reads who is on it before adding or removing one.
    private static int listOwners(CommandSourceStack source, BlockPos controller) {
        FormationInstance instance = instanceAt(source, controller);
        if (instance == null) return 0;
        String names = instance.owners().ids().isEmpty() ? "-"
                : instance.owners().ids().stream().map(UUID::toString).collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.translatable("command.mxt.formation.owners", names), false);
        return instance.owners().ids().size();
    }

    private static int editOwner(CommandSourceStack source, BlockPos controller, ServerPlayer player, boolean add) {
        FormationInstance instance = instanceAt(source, controller);
        if (instance == null) return 0;
        UUID id = player.getUUID();
        boolean changed = add ? instance.addOwner(id) : instance.removeOwner(id);
        String key = add ? (changed ? "command.mxt.formation.owners.added" : "command.mxt.formation.owners.already")
                : (changed ? "command.mxt.formation.owners.removed" : "command.mxt.formation.owners.not_owner");
        source.sendSuccess(() -> Component.translatable(key, player.getDisplayName(),
                instance.formation().toString()), true);
        return changed ? 1 : 0;
    }

    private static FormationInstance instanceAt(CommandSourceStack source, BlockPos controller) {
        FormationInstance instance = source.getLevel().getData(MxtAttachments.FORMATION_WORLD).get(controller).orElse(null);
        if (instance == null)
            source.sendFailure(Component.translatable("command.mxt.formation.owners.missing", formatPos(controller)));
        return instance;
    }

    private static String formatPos(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    // What the next period will ask of the payer once the formation's own ground and its stock are counted: the
    // same plan the ticker pays with, so the number shown is the number that will be charged.
    private static int upkeepReport(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        ServerLevel level = source.getLevel();
        BlockPos position = player.blockPosition();
        List<BlockPos> covering = level.getData(MxtAttachments.FORMATION_WORLD).formations().entrySet().stream()
                .filter(entry -> entry.getKey().distSqr(position) <= entry.getValue().radius() * entry.getValue().radius())
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (covering.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.formation.upkeep.empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.formation.upkeep", covering.size()), false);
        for (BlockPos controller : covering) {
            FormationInstance instance = level.getData(MxtAttachments.FORMATION_WORLD).get(controller).orElse(null);
            if (instance == null) continue;
            Formation definition = MxtDatapackRegistries.get(MxtResourceKeys.FORMATION, instance.formation()).orElse(null);
            if (definition == null) continue;
            CostContext context = CostContext.account(new ResourceHolderAttachment(), null,
                    FormulaContext.of(player), CostOrigin.FORMATION_MAINTENANCE);
            Map<Identifier, Double> owed = FormationService.MaintainRule.remaining(definition, context,
                    FormationWorldTicker.supply(level, controller, instance.radius()));
            source.sendSuccess(() -> Component.literal(line(controller, instance) + " owed=" + owed(owed)), false);
        }
        return covering.size();
    }

    // A dash rather than an empty string, so "nothing is owed" is visible in the same column as a bill.
    private static String owed(Map<Identifier, Double> remaining) {
        if (remaining.isEmpty()) return "-";
        return remaining.entrySet().stream()
                .map(entry -> TooltipText.number(entry.getValue()) + " " + entry.getKey())
                .collect(Collectors.joining(", "));
    }

    private static String line(BlockPos controller, FormationInstance formation) {
        return formation.formation()
                + " @ " + controller.getX() + " " + controller.getY() + " " + controller.getZ()
                + " r=" + formation.radius()
                + " owner=" + (formation.owners().ids().isEmpty() ? "-"
                        : formation.owners().ids().stream().map(UUID::toString).collect(Collectors.joining(",")))
                + " upkeep=" + formation.maintenanceCount()
                + (formation.stored().isEmpty() ? "" : " stored=" + formation.stored());
    }
}
