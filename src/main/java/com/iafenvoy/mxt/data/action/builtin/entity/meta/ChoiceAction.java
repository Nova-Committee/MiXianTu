package com.iafenvoy.mxt.data.action.builtin.entity.meta;

import com.iafenvoy.mxt.data.Weighted;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record ChoiceAction(List<Weighted<EntityAction>> actions) implements EntityAction {
    public static final MapCodec<ChoiceAction> CODEC = Weighted.codec(EntityAction.CODEC).listOf().fieldOf("actions").xmap(ChoiceAction::new, ChoiceAction::actions);

    @Override
    public void execute(@NonNull EntityActionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        Weighted<EntityAction> entry = Weighted.select(this.actions, entity.getRandom());
        if (entry != null) entry.value().execute(entity, ctx);
    }

    @Override
    public @NonNull MapCodec<ChoiceAction> codec() {
        return CODEC;
    }
}
