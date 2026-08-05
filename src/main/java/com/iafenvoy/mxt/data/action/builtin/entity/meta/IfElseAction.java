package com.iafenvoy.mxt.data.action.builtin.entity.meta;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public record IfElseAction(EntityCondition condition, EntityAction ifAction,
                           Optional<EntityAction> elseAction) implements EntityAction {
    public static final MapCodec<IfElseAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            EntityCondition.CODEC.fieldOf("condition").forGetter(IfElseAction::condition),
            EntityAction.CODEC.fieldOf("if_action").forGetter(IfElseAction::ifAction),
            EntityAction.CODEC.optionalFieldOf("else_action").forGetter(IfElseAction::elseAction)
    ).apply(instance, IfElseAction::new));

    @Override
    public void execute(Entity entity, FormulaContext context) {
        if (this.condition.test(entity, context)) this.ifAction.execute(entity, context);
        else this.elseAction.ifPresent(action -> action.execute(entity, context));
    }

    @Override
    public MapCodec<IfElseAction> codec() {
        return CODEC;
    }
}
