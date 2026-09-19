package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.data.trigger.TriggerContext;
import com.iafenvoy.mxt.data.trigger.TriggerSignal;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Consumer;

/**
 * The single entry every event bridge publishes through. It owns the one optimization the dispatcher relies
 * on: a signal nothing listens to is not built at all, which is what keeps per-tick signals affordable.
 */
public final class TriggerPublishing {
    private TriggerPublishing() {
    }

    public static void publish(Identifier signalType, LivingEntity actor, FormulaContext context) {
        publish(signalType, actor, context, ignored -> {
        });
    }

    public static void publish(Identifier signalType, LivingEntity actor, FormulaContext context, Consumer<TriggerContext> enrich) {
        // Nothing to react with, so nothing to build: the tick signal is published by every living entity on
        // every tick, and only the entities something listens to pay for a context.
        if (!TriggerDispatcher.hasListener(signalType)) return;
        TriggerContext triggerContext = new TriggerContext()
                .actor(actor)
                .level(actor.level())
                .formula(context);
        enrich.accept(triggerContext);
        TriggerDispatcher.publish(new TriggerSignal(signalType, triggerContext, null, actor.level().getGameTime()));
    }
}
