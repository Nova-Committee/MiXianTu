package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.NoOpEntityAction;
import com.iafenvoy.mxt.data.item.ItemEffectDefinition;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Data-driven consumable pill effects and toxicity policy.
 *
 * <p>The item relationship deliberately lives in {@code mxt:item_binding},
 * so a pill definition can be shared by multiple items without knowing which
 * items use it.</p>
 */
public record PillDefinition(EntityAction onConsume,
                             NumberProvider toxicityGain, NumberProvider toxicityThreshold,
                             EntityAction onOverdose,
                             NumberProvider toxicityAfterOverdose) implements ItemEffectDefinition {
    public static final MapCodec<PillDefinition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            EntityAction.CODEC.optionalFieldOf("on_consume", NoOpEntityAction.INSTANCE).forGetter(PillDefinition::onConsume),
            NumberProvider.CODEC.optionalFieldOf("toxicity_gain", new Constant(0.0D)).forGetter(PillDefinition::toxicityGain),
            NumberProvider.CODEC.optionalFieldOf("toxicity_threshold", new Constant(Double.MAX_VALUE)).forGetter(PillDefinition::toxicityThreshold),
            EntityAction.CODEC.optionalFieldOf("on_overdose", NoOpEntityAction.INSTANCE).forGetter(PillDefinition::onOverdose),
            NumberProvider.CODEC.optionalFieldOf("toxicity_after_overdose", new Constant(0.0D)).forGetter(PillDefinition::toxicityAfterOverdose)
    ).apply(instance, PillDefinition::new));

    @Override
    public String type() {
        return "pill";
    }

    @Override
    public MapCodec<PillDefinition> codec() {
        return CODEC;
    }
}
