package com.iafenvoy.mxt.data.action.builtin.bientity.meta;

import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

public record IfElseAction(BiEntityCondition condition, BiEntityAction ifAction,
                           BiEntityAction elseAction) implements BiEntityAction {
    public static final MapCodec<IfElseAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.CODEC.fieldOf("condition").forGetter(IfElseAction::condition),
            BiEntityAction.CODEC.fieldOf("if_action").forGetter(IfElseAction::ifAction),
            BiEntityAction.optionalCodec("else_action").forGetter(IfElseAction::elseAction)
    ).apply(i, IfElseAction::new));

    @Override
    public void execute(@NonNull BiEntityActionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        if (this.condition.test(actor, target, ctx)) this.ifAction.execute(actor, target, ctx);
        else this.elseAction.execute(actor, target, ctx);
    }

    @Override
    public @NonNull MapCodec<IfElseAction> codec() {
        return CODEC;
    }
}
