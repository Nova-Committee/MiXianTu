package com.iafenvoy.mxt.data.action.builtin.item;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.context.action.ItemActionContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import org.jspecify.annotations.NonNull;

public record ConsumeItemAction(NumberProvider count) implements ItemAction {
    public static final MapCodec<ConsumeItemAction> CODEC = NumberProvider.CODEC.fieldOf("count").xmap(ConsumeItemAction::new, ConsumeItemAction::count);

    @Override
    public void execute(@NonNull ItemActionContext ctx) {
        double count = this.count.evaluate(ctx.formula());
        if (Double.isFinite(count) && count > 0.0D) ctx.stack().shrink(Math.max(1, (int) Math.round(count)));
    }

    @Override
    public @NonNull MapCodec<ConsumeItemAction> codec() {
        return CODEC;
    }
}
