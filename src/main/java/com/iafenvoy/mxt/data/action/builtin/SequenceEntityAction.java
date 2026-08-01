package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.List;

public record SequenceEntityAction(List<EntityAction> actions) implements EntityAction {
    public static final MapCodec<SequenceEntityAction> CODEC = EntityAction.SINGLE_CODEC.listOf().fieldOf("actions").xmap(SequenceEntityAction::new, SequenceEntityAction::actions);

    public SequenceEntityAction {
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
    public MapCodec<SequenceEntityAction> codec() {
        return CODEC;
    }
}
