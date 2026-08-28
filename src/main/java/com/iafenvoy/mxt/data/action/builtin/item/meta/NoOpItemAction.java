package com.iafenvoy.mxt.data.action.builtin.item.meta;

import com.iafenvoy.mxt.data.context.action.ItemActionContext;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public enum NoOpItemAction implements ItemAction {
    INSTANCE;
    public static final MapCodec<NoOpItemAction> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public void execute(ItemActionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
    }

    @Override
    public MapCodec<NoOpItemAction> codec() {
        return CODEC;
    }
}
