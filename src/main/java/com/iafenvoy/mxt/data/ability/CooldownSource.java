package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.util.formula.NumberProvider;

/**
 * A type whose activation is paid for, and which therefore carries a cooldown length. It is a separate interface
 * because a type that never pays never reads one, and the payment paths ask through it rather than through the class.
 */
public interface CooldownSource {
    NumberProvider cooldown();
}
