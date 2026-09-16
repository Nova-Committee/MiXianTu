package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.config.MxtServerConfig;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.item.FormationPlateComponent;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.formation.FormationCenters;
import com.iafenvoy.mxt.runtime.formation.FormationCenters.Match;
import com.iafenvoy.mxt.runtime.formation.FormationInstance;
import com.iafenvoy.mxt.runtime.formation.FormationRelations;
import com.iafenvoy.mxt.runtime.formation.FormationWorldService;
import com.iafenvoy.mxt.runtime.formation.FormationWorldService.Failure;
import com.iafenvoy.mxt.runtime.formation.FormationWorldService.Result;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One portable controller for every datapack formation. It is a two-way switch: clicking the centre of a
 * standing structure activates it, and clicking a controller that is already running dismantles it, checked
 * first and by position alone so a plate whose own definition no longer matches can still stop what stands
 * there. An unbound plate instead identifies the formation in front of it and raises the nearest match.
 */
public final class FormationPlateItem extends Item {
    public FormationPlateItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level))
            return InteractionResult.SUCCESS;
        FormationPlateComponent plate = context.getItemInHand().getOrDefault(MxtDataComponents.FORMATION_PLATE, FormationPlateComponent.EMPTY);
        BlockPos clicked = context.getClickedPos();
        // The plate is the off switch as well as the on switch, and this lookup is by position alone, so a
        // plate whose structure no longer matches can still dismantle what is standing there. Checked before
        // anything else so nothing is spent discovering the occupancy.
        if (level.getData(MxtAttachments.FORMATION_WORLD).get(clicked).isPresent()) return dismantle(level, clicked, player);
        Optional<Holder<Formation>> selected = plate.selected();
        if (selected.isPresent()) return activate(level, clicked, player, selected.get());
        // Two different situations: "pick a formation" against "this plate was bound to one it is not
        // allowed to run". Plates that admit nothing are a real configuration, not a broken item.
        if (plate.formation().isPresent()) {
            ItemFeedback.send(player, Component.translatable("item.mxt.formation_plate.not_allowed"));
            return InteractionResult.FAIL;
        }
        if (!MxtServerConfig.formationPlateAutoDetect()) {
            ItemFeedback.send(player, Component.translatable("item.mxt.formation_plate.unbound"));
            return InteractionResult.FAIL;
        }
        // Sorted, so two definitions describing the same structure resolve to the same one every time.
        List<Holder<Formation>> admissible = admissibleCandidates(plate);
        if (admissible.isEmpty()) {
            ItemFeedback.send(player, Component.translatable("item.mxt.formation_plate.not_allowed"));
            return InteractionResult.FAIL;
        }
        Optional<Match> match = FormationCenters.resolveAny(level, clicked, admissible);
        if (match.isEmpty()) {
            ItemFeedback.send(player, Component.translatable("item.mxt.formation_plate.no_structure"));
            return InteractionResult.FAIL;
        }
        // The matched centre is the formation the identification found, so it is raised as it stands rather
        // than searched for a second time.
        return activateAt(level, match.get().center(), player, match.get().formation());
    }

    /**
     * Every formation a plate may try, in the order it tries them. Public because the ordering decides what
     * the plate raises, and the audit asserts it.
     */
    public static List<Holder<Formation>> admissibleCandidates(FormationPlateComponent plate) {
        return plate.admissible(MxtDatapackRegistries.registry(MxtResourceKeys.FORMATION)).stream()
                .sorted(Comparator.comparing(HolderHelper::id))
                .map(formation -> (Holder<Formation>) formation)
                .toList();
    }

    /**
     * Raises a bound formation, resolving the centre the player meant from the clicked block.
     */
    private static InteractionResult activate(ServerLevel level, BlockPos clicked, ServerPlayer player, Holder<Formation> formation) {
        // Nothing is stored at the clicked position, so the centre the player meant is the clicked block or one
        // of the twenty-six around it.
        Optional<BlockPos> center = FormationCenters.resolve(level, clicked, formation.value());
        return activateAt(level, center.orElse(clicked), player, formation);
    }

    /**
     * Raises a formation whose centre has already been decided. Occupancy is checked here as well as at the
     * click, because a stored neighbour is the formation the player is clicking to stop.
     */
    private static InteractionResult activateAt(ServerLevel level, BlockPos center, ServerPlayer player, Holder<Formation> formation) {
        if (level.getData(MxtAttachments.FORMATION_WORLD).get(center).isPresent()) return dismantle(level, center, player);
        Result result = FormationWorldService.activate(level, center, HolderHelper.id(formation),
                formation.value(), player.getData(MxtAttachments.RESOURCE_HOLDER), FormulaContexts.forEntity(player), player.getUUID());
        if (!result.active()) {
            ItemFeedback.send(player, Component.translatable("item.mxt.formation_plate.failed", failure(result.failure())));
            return InteractionResult.FAIL;
        }
        ItemFeedback.send(player, Component.translatable("item.mxt.formation_plate.activated",
                DefinitionText.name(formation, "formation")));
        return InteractionResult.SUCCESS;
    }

    /**
     * Turns a failure into a translatable message naming the failure, and the resource where one could not be
     * paid.
     */
    private static Component failure(Failure failure) {
        if (failure == null) return Component.translatable("item.mxt.formation_plate.failed.unknown");
        return Component.translatable("item.mxt.formation_plate.failed." + failure.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Takes down the formation occupying the controller: the only player-facing way to stop one.
     */
    private static InteractionResult dismantle(ServerLevel level, BlockPos controller, ServerPlayer player) {
        FormationInstance instance = level.getData(MxtAttachments.FORMATION_WORLD).get(controller).orElse(null);
        if (instance == null) return InteractionResult.FAIL;
        if (!FormationRelations.canDismantle(instance, player)) {
            ItemFeedback.send(player, Component.translatable("item.mxt.formation_plate.denied"));
            return InteractionResult.FAIL;
        }
        if (!FormationWorldService.deactivate(level, controller)) return InteractionResult.FAIL;
        ItemFeedback.send(player, Component.translatable("item.mxt.formation_plate.deactivated"));
        return InteractionResult.SUCCESS;
    }
}
