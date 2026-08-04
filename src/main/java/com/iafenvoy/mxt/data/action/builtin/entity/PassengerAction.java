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
 * Executes nested actions for matching direct or recursive passengers.
 */
public record PassengerAction(Optional<EntityAction> action, Optional<BiEntityAction> biEntityAction,
                              Optional<BiEntityCondition> biEntityCondition,
                              boolean recursive) implements EntityAction {
    public static final MapCodec<PassengerAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            EntityAction.CODEC.optionalFieldOf("action").forGetter(PassengerAction::action),
            BiEntityAction.CODEC.optionalFieldOf("bientity_action").forGetter(PassengerAction::biEntityAction),
            BiEntityCondition.CODEC.optionalFieldOf("bientity_condition").forGetter(PassengerAction::biEntityCondition),
            Codec.BOOL.optionalFieldOf("recursive", false).forGetter(PassengerAction::recursive)
    ).apply(instance, PassengerAction::new));

    @Override
    public void execute(Entity entity, FormulaContext context) {
        for (Entity passenger : this.recursive ? entity.getIndirectPassengers() : entity.getPassengers()) {
            if (this.biEntityCondition.isPresent() && !this.biEntityCondition.get().test(entity, passenger, context))
                continue;
            this.action.ifPresent(action -> action.execute(passenger, context));
            this.biEntityAction.ifPresent(action -> action.execute(entity, passenger, context));
        }
    }

    @Override
    public void execute(Entity entity) {
        this.execute(entity, FormulaContext.EMPTY);
    }

    @Override
    public MapCodec<PassengerAction> codec() {
        return CODEC;
    }
}
