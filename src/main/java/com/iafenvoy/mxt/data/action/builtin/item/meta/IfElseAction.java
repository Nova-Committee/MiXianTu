package com.iafenvoy.mxt.data.action.builtin.item.meta;

import com.iafenvoy.mxt.data.context.action.ItemActionContext;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public record IfElseAction(ItemCondition condition, ItemAction ifAction,
                           Optional<ItemAction> elseAction) implements ItemAction {
    public static final MapCodec<IfElseAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemCondition.CODEC.fieldOf("condition").forGetter(IfElseAction::condition),
            ItemAction.CODEC.fieldOf("if_action").forGetter(IfElseAction::ifAction),
            ItemAction.CODEC.optionalFieldOf("else_action").forGetter(IfElseAction::elseAction)
    ).apply(i, IfElseAction::new));

    @Override
    public void execute(ItemActionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        if (this.condition.test(holder, stack, ctx)) this.ifAction.execute(holder, stack, ctx);
        else this.elseAction.ifPresent(action -> action.execute(holder, stack, ctx));
    }

    @Override
    public MapCodec<IfElseAction> codec() {
        return CODEC;
    }
}
