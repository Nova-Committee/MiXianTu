package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

public record ArtifactEnergyRangeItemCondition(NumberProvider min, NumberProvider max) implements ItemCondition {
    public static final MapCodec<ArtifactEnergyRangeItemCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("min").forGetter(ArtifactEnergyRangeItemCondition::min),
            NumberProvider.CODEC.fieldOf("max").forGetter(ArtifactEnergyRangeItemCondition::max)
    ).apply(i, ArtifactEnergyRangeItemCondition::new));

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        double min = this.min.evaluate(context);
        double max = this.max.evaluate(context);
        double energy = ArtifactService.state(stack).spiritEnergy();
        return Double.isFinite(min) && Double.isFinite(max) && min <= max && energy >= min && energy <= max;
    }

    @Override
    public @NonNull MapCodec<ArtifactEnergyRangeItemCondition> codec() {
        return CODEC;
    }
}
