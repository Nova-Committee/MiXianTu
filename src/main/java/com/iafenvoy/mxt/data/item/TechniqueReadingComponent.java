package com.iafenvoy.mxt.data.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemUseAnimation;

import java.util.Optional;

/**
 * How one stack is read, where that differs from the declaration: the three fields a pack may want to vary per
 * item. Which technique the stack teaches is not here - that is the {@code mxt:technique} component.
 */
public record TechniqueReadingComponent(Optional<Integer> learnTime, Optional<ItemUseAnimation> holdAnimation,
                                        Optional<Holder<SoundEvent>> holdSound) {
    public static final Codec<TechniqueReadingComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 72_000).optionalFieldOf("learn_time").forGetter(TechniqueReadingComponent::learnTime),
            ItemUseAnimation.CODEC.optionalFieldOf("hold_animation").forGetter(TechniqueReadingComponent::holdAnimation),
            SoundEvent.CODEC.optionalFieldOf("hold_sound").forGetter(TechniqueReadingComponent::holdSound)
    ).apply(i, TechniqueReadingComponent::new));

    public TechniqueBinding applyTo(TechniqueBinding base) {
        return new TechniqueBinding(base.technique(), base.entries(), base.priority(), base.carrierItem(),
                base.qualityChain(), base.conditions(), this.learnTime.orElse(base.learnTime()),
                this.holdAnimation.orElse(base.holdAnimation()), this.holdSound.orElse(base.holdSound()));
    }
}
