package com.iafenvoy.mxt.data.action.builtin.bientity.meta;

import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record SequenceBiEntityAction(List<BiEntityAction> actions) implements BiEntityAction {
    public static final MapCodec<SequenceBiEntityAction> CODEC = SINGLE_CODEC.listOf().fieldOf("actions").xmap(SequenceBiEntityAction::new, SequenceBiEntityAction::actions);

    @Override
    public void execute(@NonNull BiEntityActionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        this.actions.forEach(action -> action.execute(actor, target, ctx));
    }

    @Override
    public @NonNull MapCodec<SequenceBiEntityAction> codec() {
        return CODEC;
    }
}
