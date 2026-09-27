package com.iafenvoy.mxt.data.action.builtin.item.meta;

import com.iafenvoy.mxt.data.Weighted;
import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.context.action.ItemActionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record ChoiceAction(List<Weighted<ItemAction>> actions) implements ItemAction {
    public static final MapCodec<ChoiceAction> CODEC = Weighted.codec(ItemAction.CODEC).listOf().fieldOf("actions").xmap(ChoiceAction::new, ChoiceAction::actions);

    @Override
    public void execute(@NonNull ItemActionContext ctx) {
        Entity holder = ctx.holder();
        Weighted<ItemAction> entry = Weighted.select(this.actions, holder.getRandom());
        if (entry != null) entry.value().execute(holder, ctx.stack(), ctx);
    }

    @Override
    public @NonNull MapCodec<ChoiceAction> codec() {
        return CODEC;
    }
}
