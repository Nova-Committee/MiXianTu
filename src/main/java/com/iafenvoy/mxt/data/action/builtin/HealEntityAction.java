package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

public record HealEntityAction(NumberProvider amount) implements EntityAction {
    public static final MapCodec<HealEntityAction> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(NumberProvider.CODEC.fieldOf("amount").forGetter(HealEntityAction::amount)).apply(instance, HealEntityAction::new));

    @Override
    public void execute(Entity entity) {
        if (entity instanceof LivingEntity living) {
            double value = this.amount.evaluate(FormulaContext.EMPTY);
            if (Double.isFinite(value) && value > 0.0D) living.heal((float) value);
        }
    }

    @Override
    public void execute(Entity entity, FormulaContext context) {
        if (entity instanceof LivingEntity living) {
            double value = this.amount.evaluate(context);
            if (Double.isFinite(value) && value > 0.0D) living.heal((float) value);
        }
    }

    @Override
    public MapCodec<HealEntityAction> codec() {
        return CODEC;
    }
}
