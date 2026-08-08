package com.iafenvoy.mxt.data.action.builtin.bientity.meta;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.WeightedActionEntry;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.List;

public record ChoiceAction(List<WeightedActionEntry<BiEntityAction>> actions) implements BiEntityAction {
    public static final MapCodec<ChoiceAction> CODEC = WeightedActionEntry.codec(BiEntityAction.CODEC).listOf().fieldOf("actions").xmap(ChoiceAction::new, ChoiceAction::actions);

    @Override
    public void execute(Entity actor, Entity target, FormulaContext context) {
        WeightedActionEntry<BiEntityAction> entry = WeightedActionEntry.select(this.actions, actor.getRandom());
        if (entry != null) entry.element().execute(actor, target, context);
    }

    @Override
    public MapCodec<ChoiceAction> codec() {
        return CODEC;
    }
}
