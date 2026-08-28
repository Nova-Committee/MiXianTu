package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

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
    public static final MapCodec<PassengerAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityAction.CODEC.optionalFieldOf("action").forGetter(PassengerAction::action),
            BiEntityAction.CODEC.optionalFieldOf("bientity_action").forGetter(PassengerAction::biEntityAction),
            BiEntityCondition.CODEC.optionalFieldOf("bientity_condition").forGetter(PassengerAction::biEntityCondition),
            Codec.BOOL.optionalFieldOf("recursive", false).forGetter(PassengerAction::recursive)
    ).apply(i, PassengerAction::new));

    @Override
    public void execute(EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        for (Entity passenger : this.recursive ? entity.getIndirectPassengers() : entity.getPassengers()) {
            if (this.biEntityCondition.isPresent() && !this.biEntityCondition.get().test(entity, passenger, ctx))
                continue;
            this.action.ifPresent(action -> action.execute(passenger, ctx));
            this.biEntityAction.ifPresent(action -> action.execute(entity, passenger, ctx));
        }
    }

    @Override
    public MapCodec<PassengerAction> codec() {
        return CODEC;
    }
}
