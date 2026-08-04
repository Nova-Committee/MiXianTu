package com.iafenvoy.mxt.data.action.builtin.bientity.meta;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.List;

public record SequenceBiEntityAction(List<BiEntityAction> actions) implements BiEntityAction {
    public static final MapCodec<SequenceBiEntityAction> CODEC = SINGLE_CODEC.listOf().fieldOf("actions").xmap(SequenceBiEntityAction::new, SequenceBiEntityAction::actions);

    public SequenceBiEntityAction {
        actions = List.copyOf(actions);
    }

    @Override
    public void execute(Entity actor, Entity target, FormulaContext context) {
        this.actions.forEach(action -> action.execute(actor, target, context));
    }

    @Override
    public MapCodec<SequenceBiEntityAction> codec() {
        return CODEC;
    }
}
