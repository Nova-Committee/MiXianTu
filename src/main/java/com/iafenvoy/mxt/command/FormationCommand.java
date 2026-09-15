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
 * The {@code /mxt formation} subtree: diagnostics, and binding a plate to a formation.
 *
 * <p>Also available as a top-level {@code /formation} when the server option is enabled, matching how the
 * other player-facing subtrees work. The {@code /mxt formation} entry is always complete, so a disabled
 * option removes the alias and never the command itself.</p>
 *
 * <p>The diagnostics matter because a running formation is otherwise invisible — nothing in the world says
 * which instance it is, how much upkeep it has paid, or who owns it — and an operator has no way to tell
 * an overlapping pair of formations from a single one.</p>
 *
 * <p>{@code bind} is the one write in here, and it exists because nothing else could produce a usable
 * plate. A plate carries its formation in a data component, and the only way to set one was the
 * {@code /give} component syntax, which asks a player to know the registry id, the component name and
 * the NBT shape before they can find out whether a formation works. Binding a held plate closes that
 * gap without inventing any content: which formation goes on which plate is still the content pack's
 * decision, and the command only performs the binding they would otherwise have to write as a recipe
 * or a loot table.</p>
 *
 * <p>Dismantling is deliberately <em>not</em> here. It belongs to the plate, which is the player-facing
 * way to control a formation and the only thing that goes through
 * {@link com.iafenvoy.mxt.runtime.formation.FormationRelations#canDismantle}.</p>
 */
public final class FormationCommand {
    /**
     * The same subtree as a top-level {@code /formation}, registered only when the server option allows
     * it.
     *
     * <p>Built by the same method as the {@code /mxt} copy rather than restated, so the two can never
     * drift into offering different arguments. Brigadier permits the two registrations because each
     * {@code build()} produces a separate node from the same builder.</p>
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
     * Offers the formations the held plate admits, rather than every formation in the registry.
     *
     * <p>{@code IdentifierArgument} has no suggestions of its own, so without this the argument completes
     * to nothing at all. What belongs here is the plate's own allow list: it is both the useful answer and
     * the bounded one, and a plate that admits nothing offers nothing rather than inviting a guess.</p>
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
     * Writes a formation into the plate held in the main hand.
     *
     * <p>Rebinding an already bound plate is allowed and overwrites: a plate is a tool, and making the
     * player launder it through a crafting table to change one field would be busywork. The formation id
     * is resolved before the stack is touched, so a typo leaves the plate exactly as it was.</p>
     *
     * <p>Public so the server audit can drive the command body with a {@code FakePlayer}; the command
     * node is the only caller in the mod.</p>
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
        // Before anything is written, and after the id itself resolved: a plate that does not admit this
        // formation must come out of a refused command exactly as it went in.
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

    /**
     * Every active formation in the source's level.
     */
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
     * The formations whose range covers the player.
     *
     * <p>Reports every match instead of resolving a single winner: overlapping formations are exactly
     * the case an operator is trying to see, and silently picking one would hide the other.</p>
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
                + " upkeep=" + formation.maintenanceCount();
    }
}
