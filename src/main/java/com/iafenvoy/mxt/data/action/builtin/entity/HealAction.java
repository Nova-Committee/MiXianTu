package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public record HealAction(NumberProvider amount) implements EntityAction {
    public static final MapCodec<HealAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(NumberProvider.CODEC.fieldOf("amount").forGetter(HealAction::amount)).apply(i, HealAction::new));

    @Override
    public void execute(Entity entity, FormulaContext context) {
        if (entity instanceof LivingEntity living) {
            double value = this.amount.evaluate(context);
            if (Double.isFinite(value) && value > 0.0D) living.heal((float) value);
        }
    }

    @Override
    public MapCodec<HealAction> codec() {
        return CODEC;
    }
}
