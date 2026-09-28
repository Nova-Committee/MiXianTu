package com.iafenvoy.mxt.data.progression;

import net.minecraft.core.Holder;

import java.util.Map;
import java.util.Optional;

/**
 * A definition that owns a progression: it names the level a holder starts on and what each level means to it. A
 * technique implements this today; anything else a body can hold joins by implementing it and adding one source in
 * the progression sources. The chain itself stays shared and knows nothing about owners.
 */
public interface ProgressionOwner {
    Optional<Holder<Progression>> entryLevel();

    Map<Holder<Progression>, ProgressionConfig> levels();

    default Optional<ProgressionConfig> config(Holder<Progression> level) {
        return level == null ? Optional.empty() : Optional.ofNullable(this.levels().get(level));
    }
}
