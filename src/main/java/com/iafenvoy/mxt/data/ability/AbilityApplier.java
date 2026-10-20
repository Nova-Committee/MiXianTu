package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.profiling.LoggerDispatcher;
import com.iafenvoy.mxt.util.profiling.LoggingLevel;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.stream.Stream;

/**
 * A type whose own part is the reach: it picks the entities an activation lands on and runs one payload ability's
 * one-target half on each of them. The reach is asked before anything is paid, so a press that lands on nobody is
 * refused instead of paid for, and the payload itself pays nothing - the activation pays as the ability that owns it.
 */
public interface AbilityApplier extends AbilityEffect {
    LoggerDispatcher LOGGER = LoggerDispatcher.of(LogUtils.getLogger(), LoggingLevel.ERROR, LoggingLevel.ERROR);

    // The entities the payload would run on right now, already filtered by whatever the payload asks of a target.
    Stream<Entity> reach(Entity actor, FormulaContext context, @Nullable Vec3 origin);

    // The named ability run on every entity the reach hands over; only its one-target half is read.
    Holder<Ability> payload();

    @Override
    default void execute(Entity actor, FormulaContext context, @Nullable Vec3 origin) {
        try {
            this.reach(actor, context, origin).forEach(target -> AbilityEffect.runOn(this.payload().value().type(), actor, target, context, origin));
        } catch (RuntimeException exception) {
            LOGGER.log("Ability target selection failed", exception);
        }
    }
}
