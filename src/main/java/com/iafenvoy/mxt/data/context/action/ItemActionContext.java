package com.iafenvoy.mxt.data.context.action;

import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public class ItemActionContext extends Context {
    private final Entity holder;
    private final ItemStack stack;
    private final FormulaContext formula;

    public ItemActionContext(Entity holder, ItemStack stack, FormulaContext formula) {
        this.holder = holder;
        this.stack = stack;
        this.formula = formula;
    }

    public Entity holder() {
        return this.holder;
    }

    public ItemStack stack() {
        return this.stack;
    }

    @Override
    public FormulaContext formula() {
        return this.formula;
    }
}
