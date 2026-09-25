package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * Matches an artifact holding one named aura within a range. The aura is named rather than implied because an
 * artifact may hold several charges.
 */
public record ArtifactEnergyRangeItemCondition(Holder<Aura> aura, NumberProvider min,
                                               NumberProvider max) implements ItemCondition {
    public static final MapCodec<ArtifactEnergyRangeItemCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Aura.CODEC.fieldOf("aura").forGetter(ArtifactEnergyRangeItemCondition::aura),
            NumberProvider.CODEC.fieldOf("min").forGetter(ArtifactEnergyRangeItemCondition::min),
            NumberProvider.CODEC.fieldOf("max").forGetter(ArtifactEnergyRangeItemCondition::max)
    ).apply(i, ArtifactEnergyRangeItemCondition::new));

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        double min = this.min.evaluate(context);
        double max = this.max.evaluate(context);
        double energy = ArtifactService.stored(stack, this.aura);
        return Double.isFinite(min) && Double.isFinite(max) && min <= max && energy >= min && energy <= max;
    }

    @Override
    public @NonNull MapCodec<ArtifactEnergyRangeItemCondition> codec() {
        return CODEC;
    }
}
