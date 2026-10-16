package com.iafenvoy.mxt.runtime.formation;

import com.iafenvoy.mxt.data.formation.Formation;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.FormationIntervals;

/**
 * Runs a formation's modules at each beat, in list order. The array's own beats and the per-entity ones differ only
 * in the context they carry, so nothing here switches on a module type. The module beat is finer than the upkeep
 * period, which is why {@code tick} carries its own gate.
 */
public final class FormationActionRunner {
    private FormationActionRunner() {
    }

    public static void activate(Formation definition, FormationActionContext context) {
        for (FormationActionType module : definition.actions()) module.activate(context);
    }

    // tick for every module on an upkeep period and interval for the ones whose own tick count is due. The gate reads
    // the world clock rather than a counter kept per module, so the instances of one definition - and everything
    // inside them - fire together.
    public static void periodic(Formation definition, FormationActionContext context, long gameTime) {
        boolean period = FormationIntervals.periodDue(gameTime);
        for (FormationActionType module : definition.actions()) {
            if (period) module.tick(context);
            if (FormationIntervals.due(module.intervalTicks(), gameTime))
                module.interval(context);
        }
    }

    public static void inactivate(Formation definition, FormationActionContext context) {
        for (FormationActionType module : definition.actions()) module.inactivate(context);
    }
}
