package com.iafenvoy.mxt.data.action.builtin.item;

import com.iafenvoy.mxt.data.context.action.ItemActionContext;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

/**
 * Merges a vanilla data-component patch into the acted item stack.
 */
public record MergeComponentsAction(DataComponentPatch components) implements ItemAction {
    public static final MapCodec<MergeComponentsAction> CODEC = DataComponentPatch.CODEC.fieldOf("components").xmap(MergeComponentsAction::new, MergeComponentsAction::components);

    @Override
    public void execute(ItemActionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        stack.applyComponents(this.components);
    }

    @Override
    public MapCodec<MergeComponentsAction> codec() {
        return CODEC;
    }
}
