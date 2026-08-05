package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * Executes nested actions for a matching vehicle, optionally through the full riding chain.
 */
public record RidingAction(Optional<EntityAction> action, Optional<BiEntityAction> biEntityAction,
                           Optional<BiEntityCondition> biEntityCondition, boolean recursive) implements EntityAction {
    public static final MapCodec<RidingAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            EntityAction.CODEC.optionalFieldOf("action").forGetter(RidingAction::action),
            BiEntityAction.CODEC.optionalFieldOf("bientity_action").forGetter(RidingAction::biEntityAction),
            BiEntityCondition.CODEC.optionalFieldOf("bientity_condition").forGetter(RidingAction::biEntityCondition),
            Codec.BOOL.optionalFieldOf("recursive", false).forGetter(RidingAction::recursive)
    ).apply(instance, RidingAction::new));

    @Override
    public void execute(Entity entity, FormulaContext context) {
        Entity vehicle = entity.getVehicle();
        while (vehicle != null) {
            boolean matches = this.biEntityCondition.isEmpty() || this.biEntityCondition.get().test(entity, vehicle, context);
            if (matches) {
                if (this.action.isPresent()) this.action.get().execute(vehicle, context);
                if (this.biEntityAction.isPresent()) this.biEntityAction.get().execute(entity, vehicle, context);
            }
            if (!this.recursive) return;
            vehicle = vehicle.getVehicle();
        }
    }

    @Override
    public MapCodec<RidingAction> codec() {
        return CODEC;
    }
}
