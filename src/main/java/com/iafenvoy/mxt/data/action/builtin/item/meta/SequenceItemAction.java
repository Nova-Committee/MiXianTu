package com.iafenvoy.mxt.data.action.builtin.item.meta;

import com.iafenvoy.mxt.data.context.action.ItemActionContext;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record SequenceItemAction(List<ItemAction> actions) implements ItemAction {
    public static final MapCodec<SequenceItemAction> CODEC = SINGLE_CODEC.listOf().fieldOf("actions").xmap(SequenceItemAction::new, SequenceItemAction::actions);

    @Override
    public void execute(@NonNull ItemActionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        this.actions.forEach(action -> action.execute(holder, stack, ctx));
    }

    @Override
    public @NonNull MapCodec<SequenceItemAction> codec() {
        return CODEC;
    }
}
