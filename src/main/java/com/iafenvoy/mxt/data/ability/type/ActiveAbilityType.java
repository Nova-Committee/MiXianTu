package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.ability.ToggleContext;
import com.iafenvoy.mxt.runtime.ability.AbilityActivationService;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

/**
 * A skill a player presses for. It is also the first {@link Togglable}: the press runs the whole cast pipeline,
 * which pays for itself, so the shared press gate is skipped rather than applied twice.
 */
public record ActiveAbilityType(String slot) implements AbilityType, Togglable {
    public static final MapCodec<ActiveAbilityType> CODEC = Codec.STRING.optionalFieldOf("slot", "primary").xmap(ActiveAbilityType::new, ActiveAbilityType::slot);

    public ActiveAbilityType {
        if (slot.isBlank()) throw new IllegalArgumentException("Ability slot cannot be blank");
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
