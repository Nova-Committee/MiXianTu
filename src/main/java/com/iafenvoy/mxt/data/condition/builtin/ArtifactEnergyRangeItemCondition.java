package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public record ArtifactEnergyRangeItemCondition(NumberProvider min, NumberProvider max) implements ItemCondition {
    public static final MapCodec<ArtifactEnergyRangeItemCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            NumberProvider.CODEC.fieldOf("min").forGetter(ArtifactEnergyRangeItemCondition::min),
            NumberProvider.CODEC.fieldOf("max").forGetter(ArtifactEnergyRangeItemCondition::max)
    ).apply(instance, ArtifactEnergyRangeItemCondition::new));

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        double min = this.min.evaluate(context);
        double max = this.max.evaluate(context);
        double energy = ArtifactService.state(stack).spiritEnergy();
        return Double.isFinite(min) && Double.isFinite(max) && min <= max && energy >= min && energy <= max;
    }

    @Override
    public MapCodec<ArtifactEnergyRangeItemCondition> codec() {
        return CODEC;
    }
}
