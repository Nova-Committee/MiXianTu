package com.iafenvoy.mxt.data.action.builtin.bientity.meta;

import com.iafenvoy.mxt.data.Weighted;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record ChoiceAction(List<Weighted<BiEntityAction>> actions) implements BiEntityAction {
    public static final MapCodec<ChoiceAction> CODEC = Weighted.codec(BiEntityAction.CODEC).listOf().fieldOf("actions").xmap(ChoiceAction::new, ChoiceAction::actions);

    @Override
    public void execute(@NonNull BiEntityActionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        Weighted<BiEntityAction> entry = Weighted.select(this.actions, actor.getRandom());
        if (entry != null) entry.value().execute(actor, target, ctx);
    }

    @Override
    public @NonNull MapCodec<ChoiceAction> codec() {
        return CODEC;
    }
}
