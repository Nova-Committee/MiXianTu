package com.iafenvoy.mxt.data.action.builtin.entity.meta;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.WeightedActionEntry;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.List;

public record ChoiceAction(List<WeightedActionEntry<EntityAction>> actions) implements EntityAction {
    public static final MapCodec<ChoiceAction> CODEC = WeightedActionEntry.codec(EntityAction.CODEC).listOf().fieldOf("actions").xmap(ChoiceAction::new, ChoiceAction::actions);

    public ChoiceAction {
        actions = List.copyOf(actions);
    }

    @Override
    public void execute(Entity entity, FormulaContext context) {
        WeightedActionEntry<EntityAction> entry = WeightedActionEntry.select(this.actions, entity.getRandom());
        if (entry != null) entry.element().execute(entity, context);
    }

    @Override
    public MapCodec<ChoiceAction> codec() {
        return CODEC;
    }
}
