package com.iafenvoy.mxt.data.action.builtin.entity.meta;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record IfElseAction(EntityCondition condition, EntityAction ifAction,
                           EntityAction elseAction) implements EntityAction {
    public static final MapCodec<IfElseAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityCondition.CODEC.fieldOf("condition").forGetter(IfElseAction::condition),
            EntityAction.CODEC.fieldOf("if_action").forGetter(IfElseAction::ifAction),
            EntityAction.optionalCodec("else_action").forGetter(IfElseAction::elseAction)
    ).apply(i, IfElseAction::new));

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        if (this.condition.test(entity, ctx)) this.ifAction.execute(entity, ctx);
        else this.elseAction.execute(entity, ctx);
    }

    @Override
    public @NonNull MapCodec<IfElseAction> codec() {
        return CODEC;
    }
}
