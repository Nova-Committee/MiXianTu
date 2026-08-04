package com.iafenvoy.mxt.data.action.builtin.entity.meta;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.List;

public record SequenceAction(List<EntityAction> actions) implements EntityAction {
    public static final MapCodec<SequenceAction> CODEC = SINGLE_CODEC.listOf().fieldOf("actions").xmap(SequenceAction::new, SequenceAction::actions);

    public SequenceAction {
        actions = List.copyOf(actions);
    }

    @Override
    public void execute(Entity entity) {
        this.actions.forEach(action -> action.execute(entity));
    }

    @Override
    public void execute(Entity entity, FormulaContext context) {
        this.actions.forEach(action -> action.execute(entity, context));
    }

    @Override
    public MapCodec<SequenceAction> codec() {
        return CODEC;
    }
}
