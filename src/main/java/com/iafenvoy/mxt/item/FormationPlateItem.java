package com.iafenvoy.mxt.item;

import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.item.FormationPlateComponent;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.runtime.formation.FormationWorldService;
import com.iafenvoy.mxt.runtime.formation.FormationWorldService.Result;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.NotNull;

/**
 * One portable controller for every datapack formation.
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
        if (plate.formation().isEmpty()) {
            ItemFeedback.send(player, Component.translatable("item.mxt.formation_plate.unbound"));
            return InteractionResult.FAIL;
        }
        Holder<Formation> formation = plate.formation().orElseThrow();
        Result result = FormationWorldService.activate(level, context.getClickedPos(), HolderHelper.id(formation),
                formation.value(), player.getData(MxtAttachments.RESOURCE_HOLDER), FormulaContexts.forEntity(player), player.getUUID());
        if (!result.active()) {
            ItemFeedback.send(player, Component.translatable("item.mxt.formation_plate.failed", result.failure().name()));
            return InteractionResult.FAIL;
        }
        ItemFeedback.send(player, Component.translatable("item.mxt.formation_plate.activated"));
        return InteractionResult.SUCCESS;
    }
}
