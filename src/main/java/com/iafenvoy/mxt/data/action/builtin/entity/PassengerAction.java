package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Executes nested actions for matching direct or recursive passengers.
 */
public record PassengerAction(EntityAction action, BiEntityAction biEntityAction,
                              BiEntityCondition biEntityCondition,
                              boolean recursive) implements EntityAction {
    public static final MapCodec<PassengerAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityAction.optionalCodec("action").forGetter(PassengerAction::action),
            BiEntityAction.optionalCodec("bientity_action").forGetter(PassengerAction::biEntityAction),
            BiEntityCondition.optionalCodec("bientity_condition").forGetter(PassengerAction::biEntityCondition),
            Codec.BOOL.optionalFieldOf("recursive", false).forGetter(PassengerAction::recursive)
    ).apply(i, PassengerAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        for (Entity passenger : this.recursive ? entity.getIndirectPassengers() : entity.getPassengers()) {
            if (!this.biEntityCondition.test(entity, passenger, ctx))
                continue;
            this.action.execute(passenger, ctx);
            this.biEntityAction.execute(entity, passenger, ctx);
        }
    }

    @Override
    public @NonNull MapCodec<PassengerAction> codec() {
        return CODEC;
    }
}
