package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * The pill rules a single stack declares for itself. Every field is optional, so a component naming one number
 * leaves the rest to the declaration that claimed the stack; a declaration is not required either, in which case
 * the codec's own defaults answer.
 */
public record PillComponent(Optional<EntityAction> onConsume, Optional<NumberProvider> toxicityGain,
                            Optional<NumberProvider> toxicityThreshold, Optional<EntityAction> onOverdose,
                            Optional<NumberProvider> toxicityAfterOverdose) {
    public static final Codec<PillComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            EntityAction.CODEC.optionalFieldOf("on_consume").forGetter(PillComponent::onConsume),
            NumberProvider.CODEC.optionalFieldOf("toxicity_gain").forGetter(PillComponent::toxicityGain),
            NumberProvider.CODEC.optionalFieldOf("toxicity_threshold").forGetter(PillComponent::toxicityThreshold),
            EntityAction.CODEC.optionalFieldOf("on_overdose").forGetter(PillComponent::onOverdose),
            NumberProvider.CODEC.optionalFieldOf("toxicity_after_overdose").forGetter(PillComponent::toxicityAfterOverdose)
    ).apply(i, PillComponent::new));

    // Field by field, which is what makes "only this stack overdoses later" writable without repeating the rest.
    public PillBinding applyTo(PillBinding base) {
        return new PillBinding(base.entries(), this.onConsume.orElse(base.onConsume()),
                this.toxicityGain.orElse(base.toxicityGain()), this.toxicityThreshold.orElse(base.toxicityThreshold()),
                this.onOverdose.orElse(base.onOverdose()),
                this.toxicityAfterOverdose.orElse(base.toxicityAfterOverdose()),
                base.qualityChain(), base.conditions(), base.priority());
    }
}
