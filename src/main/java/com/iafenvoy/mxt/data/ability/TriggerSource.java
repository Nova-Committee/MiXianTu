package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.trigger.Trigger;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * A type a signal can wake up: which signals it listens for, whether this occurrence gets through, and the condition
 * a hurt signal has to pass. It is a separate interface because listening is not part of being active, and the bridge
 * asks through it rather than through the class. The chance belongs here too: it is the same "does this occurrence
 * count" question.
 */
public interface TriggerSource {
    List<Trigger> triggers();

    // Only the hurt signal carries a damage source, so only the type that listens for it has a condition to answer.
    DamageCondition damageCondition();

    // Evaluated by the server immediately before dispatch, with the signal's own formula context.
    default boolean rolls(LivingEntity entity, FormulaContext context) {
        return true;
    }
}
