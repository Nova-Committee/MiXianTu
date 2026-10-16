package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.Optional;

/**
 * How one stack is read, where that differs from the technique: the three fields a pack may want to vary per item.
 * Which technique the stack teaches is not here - that is the {@code mxt:technique} component.
 */
public record TechniqueReadingComponent(Optional<NumberProvider> learnTime, Optional<ItemUseAnimation> holdAnimation,
                                        Optional<Holder<SoundEvent>> holdSound) {
    public static final Codec<TechniqueReadingComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("learn_time").forGetter(TechniqueReadingComponent::learnTime),
            ItemUseAnimation.CODEC.optionalFieldOf("hold_animation").forGetter(TechniqueReadingComponent::holdAnimation),
            SoundEvent.CODEC.optionalFieldOf("hold_sound").forGetter(TechniqueReadingComponent::holdSound)
    ).apply(i, TechniqueReadingComponent::new));

    public TechniqueReading applyTo(TechniqueReading base) {
        return new TechniqueReading(this.learnTime.orElse(base.learnTime()),
                this.holdAnimation.orElse(base.holdAnimation()), this.holdSound.orElse(base.holdSound()));
    }
}
