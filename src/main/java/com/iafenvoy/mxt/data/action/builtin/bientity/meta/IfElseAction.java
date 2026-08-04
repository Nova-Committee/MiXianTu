package com.iafenvoy.mxt.data.action.builtin.bientity.meta;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

public record IfElseAction(BiEntityCondition condition, BiEntityAction ifAction,
                           Optional<BiEntityAction> elseAction) implements BiEntityAction {
    public static final MapCodec<IfElseAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiEntityCondition.CODEC.fieldOf("condition").forGetter(IfElseAction::condition),
            BiEntityAction.CODEC.fieldOf("if_action").forGetter(IfElseAction::ifAction),
            BiEntityAction.CODEC.optionalFieldOf("else_action").forGetter(IfElseAction::elseAction)
    ).apply(instance, IfElseAction::new));

    @Override
    public void execute(Entity actor, Entity target, FormulaContext context) {
        if (this.condition.test(actor, target, context)) this.ifAction.execute(actor, target, context);
        else this.elseAction.ifPresent(action -> action.execute(actor, target, context));
    }

    @Override
    public MapCodec<IfElseAction> codec() {
        return CODEC;
    }
}
