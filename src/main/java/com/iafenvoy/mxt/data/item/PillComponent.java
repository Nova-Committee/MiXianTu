package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.Optional;

/**
 * The pill rules a single stack declares for itself. {@code binding} names the definition whose holder owns use
 * limits and cooldown. Every other field is an optional effect override: naming one number leaves the rest to
 * that definition, or to {@link PillBinding#defaults()} when nothing claimed the stack.
 */
public record PillComponent(Optional<Holder<PillBinding>> binding, Optional<EntityAction> onConsume,
                            Optional<NumberProvider> toxicityGain, Optional<NumberProvider> toxicityThreshold,
                            Optional<EntityAction> onOverdose, Optional<NumberProvider> toxicityAfterOverdose) {
    public static final Codec<PillComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            PillBinding.CODEC.optionalFieldOf("binding").forGetter(PillComponent::binding),
            EntityAction.CODEC.optionalFieldOf("on_consume").forGetter(PillComponent::onConsume),
            NumberProvider.CODEC.optionalFieldOf("toxicity_gain").forGetter(PillComponent::toxicityGain),
            NumberProvider.CODEC.optionalFieldOf("toxicity_threshold").forGetter(PillComponent::toxicityThreshold),
            EntityAction.CODEC.optionalFieldOf("on_overdose").forGetter(PillComponent::onOverdose),
            NumberProvider.CODEC.optionalFieldOf("toxicity_after_overdose").forGetter(PillComponent::toxicityAfterOverdose)
    ).apply(i, PillComponent::new));

    public static PillComponent ofBinding(Holder<PillBinding> binding) {
        return new PillComponent(Optional.of(binding), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty());
    }

    /**
     * Field by field. Limits, conditions, matchers and priority stay on {@code base}; an overlay cannot retarget
     * the holder that {@code uses} and {@code cooldownUntil} key off.
     */
    public PillBinding applyTo(PillBinding base) {
        if (this.onConsume.isEmpty() && this.toxicityGain.isEmpty() && this.toxicityThreshold.isEmpty()
                && this.onOverdose.isEmpty() && this.toxicityAfterOverdose.isEmpty()) return base;
        return new PillBinding(base.name(), base.description(), base.entries(),
                this.onConsume.orElse(base.onConsume()), this.toxicityGain.orElse(base.toxicityGain()),
                this.toxicityThreshold.orElse(base.toxicityThreshold()), this.onOverdose.orElse(base.onOverdose()),
                this.toxicityAfterOverdose.orElse(base.toxicityAfterOverdose()), base.conditions(), base.maxUses(),
                base.cooldown(), base.priority());
    }
}
