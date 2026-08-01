package com.iafenvoy.mxt.data.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/**
 * Compact generic state persisted for one declared ability component.
 */
public record AbilityComponentState(double value, long changedAt, Optional<String> targetUuid) {
    public static final Codec<AbilityComponentState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.DOUBLE.optionalFieldOf("value", 0.0D).forGetter(AbilityComponentState::value),
            Codec.LONG.optionalFieldOf("changed_at", 0L).forGetter(AbilityComponentState::changedAt),
            Codec.STRING.optionalFieldOf("target_uuid").forGetter(AbilityComponentState::targetUuid)
    ).apply(instance, AbilityComponentState::new));

    public AbilityComponentState {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Component state value must be finite");
    }

    public static AbilityComponentState initial(double value, long gameTime) {
        return new AbilityComponentState(value, gameTime, Optional.empty());
    }
}
