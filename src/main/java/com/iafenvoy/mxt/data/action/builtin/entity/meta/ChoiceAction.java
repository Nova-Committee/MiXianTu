package com.iafenvoy.mxt.data.action.builtin.entity.meta;

import com.iafenvoy.mxt.data.context.action.EntityActionContext;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.WeightedActionEntry;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.List;

public record ChoiceAction(List<WeightedActionEntry<EntityAction>> actions) implements EntityAction {
    public static final MapCodec<ChoiceAction> CODEC = WeightedActionEntry.codec(EntityAction.CODEC).listOf().fieldOf("actions").xmap(ChoiceAction::new, ChoiceAction::actions);

    @Override
    public void execute(EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        WeightedActionEntry<EntityAction> entry = WeightedActionEntry.select(this.actions, entity.getRandom());
        if (entry != null) entry.element().execute(entity, ctx);
    }

    @Override
    public MapCodec<ChoiceAction> codec() {
        return CODEC;
    }
}
