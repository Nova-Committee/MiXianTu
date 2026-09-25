package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.ability.ToggleContext;
import com.iafenvoy.mxt.runtime.ability.AbilityActivationService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;

import java.util.Optional;

/**
 * A skill a player presses for. It is also the first {@link Togglable}: the press runs the whole cast pipeline,
 * which pays for itself, so the shared press gate is skipped rather than applied twice.
 * <p>
 * Which cell it sits in is the player's own wheel layout, never a property of the skill, so the removed
 * {@code slot} field is refused by name instead of being silently dropped.
 */
public record ActiveAbilityType() implements AbilityType, Togglable {
    public static final ActiveAbilityType INSTANCE = new ActiveAbilityType();
    public static final MapCodec<ActiveAbilityType> CODEC = Codec.STRING.optionalFieldOf("slot")
            .flatXmap(ActiveAbilityType::refuseSlot, instance -> DataResult.success(Optional.empty()));

    private static DataResult<ActiveAbilityType> refuseSlot(Optional<String> slot) {
        return slot.isPresent()
                ? DataResult.error(() -> "mxt:active cannot declare slot: which wheel cell a skill occupies is "
                + "the player's own twelve-cell layout")
                : DataResult.success(INSTANCE);
    }

    @Override
    public MapCodec<ActiveAbilityType> codec() {
        return CODEC;
    }

    // A cast pays inside its own transaction, which also owns its cooldown and its charges.
    @Override
    public boolean gated(ToggleContext context) {
        return false;
    }

    @Override
    public Result activate(ToggleContext context) {
        return AbilityActivationService.cast(context);
    }
}
