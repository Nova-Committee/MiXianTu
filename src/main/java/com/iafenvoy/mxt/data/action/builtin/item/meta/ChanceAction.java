package com.iafenvoy.mxt.data.action.builtin.item.meta;

import com.iafenvoy.mxt.data.context.action.ItemActionContext;

import com.iafenvoy.mxt.data.action.ItemAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public record ChanceAction(ItemAction action, float chance, Optional<ItemAction> failAction) implements ItemAction {
    public static final MapCodec<ChanceAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ItemAction.CODEC.fieldOf("action").forGetter(ChanceAction::action),
            Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(ChanceAction::chance),
            ItemAction.CODEC.optionalFieldOf("fail_action").forGetter(ChanceAction::failAction)
    ).apply(i, ChanceAction::new));

    @Override
    public void execute(ItemActionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        if (holder.getRandom().nextFloat() < this.chance) this.action.execute(holder, stack, ctx);
        else this.failAction.ifPresent(action -> action.execute(holder, stack, ctx));
    }

    @Override
    public MapCodec<ChanceAction> codec() {
        return CODEC;
    }
}
