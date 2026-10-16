package com.iafenvoy.mxt.data.formation;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.runtime.formation.FormationActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * One functional module of a formation: what a formation does is its list of these, run in list order, and each one
 * is offered four beats. Every beat runs once for the array itself (a context without an entity) and, for
 * {@code tick} and {@code interval}, once per covered entity too - so a module that works on only one of the two
 * sides must return from the other. Only the modules implementing {@code interval} are throttled, by their own
 * interval in ticks.
 */
public interface FormationActionType {
    // A codec rather than a map codec, because a formation holds a list of these and only a codec can be listed.
    Codec<FormationActionType> CODEC = MxtRegistries.FORMATION_ACTION_TYPE.byNameCodec().dispatch("type", FormationActionType::codec, Function.identity());

    MapCodec<? extends FormationActionType> codec();

    // Raised, and for each entity that arrives.
    default void activate(FormationActionContext context) {
    }

    // Every upkeep period.
    default void tick(FormationActionContext context) {
    }

    // Every beat this module's own interval is due for; the gate is the framework's, so no module counts.
    default void interval(FormationActionContext context) {
    }

    // Taken down, and for each entity that leaves.
    default void inactivate(FormationActionContext context) {
    }

    // Ticks between two interval beats. Read only for a module that implements interval.
    default int intervalTicks() {
        return FormationIntervals.PERIOD;
    }
}
