package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.item.FormationPlateComponent;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.formation.FormationCenters;
import com.iafenvoy.mxt.runtime.formation.FormationInstance;
import com.iafenvoy.mxt.runtime.formation.FormationRelations;
import com.iafenvoy.mxt.runtime.formation.FormationWorldAttachment;
import com.iafenvoy.mxt.runtime.formation.FormationWorldService;
import com.iafenvoy.mxt.runtime.formation.FormationWorldService.Failure;
import com.iafenvoy.mxt.runtime.formation.FormationWorldService.Result;
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

import java.util.Locale;
import java.util.Optional;

/**
 * One portable controller for every datapack formation.
 *
 * <p>The plate is a two-way switch. Clicking the centre of a standing structure activates it; clicking
 * a controller that is already running dismantles it. Activation resolves the centre through
 * {@link FormationCenters}, so a click one block off still finds the structure the player meant instead
 * of reporting that it does not match. Dismantling is checked first, and by position alone: it is the
 * only player-facing way to stop a formation, so it must not depend on the plate's own definition
 * matching whatever is standing there.</p>
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
        Optional<Holder<Formation>> selected = plate.selected();
        if (selected.isEmpty()) {
            // Two different situations, and telling them apart is the difference between "pick a formation"
            // and "this plate was bound to one it is not allowed to run". Plates that admit nothing are a
            // real configuration, not a broken item.
            ItemFeedback.send(player, Component.translatable(plate.formation().isPresent()
                    ? "item.mxt.formation_plate.not_allowed"
                    : "item.mxt.formation_plate.unbound"));
            return InteractionResult.FAIL;
        }
        Holder<Formation> formation = selected.get();
        BlockPos clicked = context.getClickedPos();
        FormationWorldAttachment world = level.getData(MxtAttachments.FORMATION_WORLD);
        // A stored controller is a running formation, and the plate is the off switch as well as the on
        // switch: this lookup is by position alone, so a plate whose structure no longer matches can still
        // dismantle what is standing there. Checked before anything else so nothing is spent discovering
        // the occupancy.
        if (world.get(clicked).isPresent()) return dismantle(level, clicked, player);
        // Nothing is stored at the clicked position, so the player is activating, and the centre they
        // meant is the clicked block or one of the twenty-six around it. A neighbour that is already
        // stored is the formation they are standing in and clicking to stop.
        Optional<BlockPos> center = FormationCenters.resolve(level, clicked, formation.value());
        if (center.isPresent() && world.get(center.get()).isPresent())
            return dismantle(level, center.get(), player);
        Result result = FormationWorldService.activate(level, center.orElse(clicked), HolderHelper.id(formation),
                formation.value(), player.getData(MxtAttachments.RESOURCE_HOLDER), FormulaContexts.forEntity(player), player.getUUID());
        if (!result.active()) {
            ItemFeedback.send(player, Component.translatable("item.mxt.formation_plate.failed", failure(result.failure())));
            return InteractionResult.FAIL;
        }
        ItemFeedback.send(player, Component.translatable("item.mxt.formation_plate.activated"));
        return InteractionResult.SUCCESS;
    }

    /**
     * Turns a failure into something a player can act on.
     *
     * <p>The enum constant used to be printed verbatim, so a Chinese client read
     * {@code 阵法无法激活：INVALID_STRUCTURE}, and the two failures that matter at the click —
     * the structure not being there, and the cost not being payable — were indistinguishable from a
     * crash message. The resource case also names the resource that could not be paid.</p>
     */
    private static Component failure(Failure failure) {
        if (failure == null) return Component.translatable("item.mxt.formation_plate.failed.unknown");
        return Component.translatable("item.mxt.formation_plate.failed." + failure.name().toLowerCase(Locale.ROOT));
    }

    /**
     * Takes down the formation occupying the controller.
     *
     * <p>Before this existed the plate simply refused the click, which left a running formation with
     * no way to be stopped short of breaking its structure or starving its upkeep.</p>
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
