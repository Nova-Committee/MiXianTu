package com.iafenvoy.mxt.data.progression;

import com.iafenvoy.mxt.data.resource.Resource;
import net.minecraft.core.Holder;

import java.util.Map;
import java.util.Optional;

/**
 * A definition that owns a progression: it names the level a holder starts on, what each level means to it and
 * which stored value measures mastery. A technique and a creature profile implement it today; anything else a
 * body can hold joins by implementing it and adding one source in the progression sources. The chain itself stays
 * shared and knows nothing about owners.
 */
public interface ProgressionOwner {
    Optional<Holder<Progression>> entryLevel();

    Map<Holder<Progression>, ProgressionConfig> levels();

    // Which value is compared against a level's mastery. Empty means the owner never advances on its own.
    Optional<Holder<Resource>> masteryResource();

    default Optional<ProgressionConfig> config(Holder<Progression> level) {
        return level == null ? Optional.empty() : Optional.ofNullable(this.levels().get(level));
    }
}
