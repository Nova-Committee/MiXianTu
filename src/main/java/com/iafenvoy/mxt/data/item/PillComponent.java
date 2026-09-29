package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.Optional;

/**
 * The pill a single stack declares for itself — the path built-in pills use. The pill named here wins over whatever
 * binding claimed the stack, while use limit and cooldown stay with that binding. Every other field is an optional
 * effect override: naming one leaves the rest to the definition, or to {@link Pill#defaults()} when nothing claimed
 * the stack.
 */
public record PillComponent(Optional<Holder<Pill>> pill, Optional<EntityAction> onConsume,
                            Optional<NumberProvider> toxicityGain, Optional<NumberProvider> toxicityThreshold,
                            Optional<EntityAction> onOverdose, Optional<NumberProvider> toxicityAfterOverdose) {
    public static final Codec<PillComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Pill.CODEC.optionalFieldOf("pill").forGetter(PillComponent::pill),
            EntityAction.CODEC.optionalFieldOf("on_consume").forGetter(PillComponent::onConsume),
            NumberProvider.CODEC.optionalFieldOf("toxicity_gain").forGetter(PillComponent::toxicityGain),
            NumberProvider.CODEC.optionalFieldOf("toxicity_threshold").forGetter(PillComponent::toxicityThreshold),
            EntityAction.CODEC.optionalFieldOf("on_overdose").forGetter(PillComponent::onOverdose),
            NumberProvider.CODEC.optionalFieldOf("toxicity_after_overdose").forGetter(PillComponent::toxicityAfterOverdose)
    ).apply(i, PillComponent::new));

    public static PillComponent ofPill(Holder<Pill> pill) {
        return new PillComponent(Optional.of(pill), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    /**
     * Field by field. Limits, matchers and priority belong to the binding and are not part of this copy.
     */
    public Pill applyTo(Pill base) {
        if (this.onConsume.isEmpty() && this.toxicityGain.isEmpty() && this.toxicityThreshold.isEmpty()
                && this.onOverdose.isEmpty() && this.toxicityAfterOverdose.isEmpty()) return base;
        return new Pill(base.name(), base.description(), base.color(),
                this.onConsume.orElse(base.onConsume()), this.toxicityGain.orElse(base.toxicityGain()),
                this.toxicityThreshold.orElse(base.toxicityThreshold()), this.onOverdose.orElse(base.onOverdose()),
                this.toxicityAfterOverdose.orElse(base.toxicityAfterOverdose()), base.conditions());
    }
}
