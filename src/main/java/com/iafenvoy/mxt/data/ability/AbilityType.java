package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.data.storage.DataStorageCollector;
import com.iafenvoy.mxt.data.storage.runtime.ActiveState;
import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;

import java.util.function.Function;

/**
 * Code-owned ability behaviour selected by a datapack {@code type}. Concrete spells remain named datapack entries
 * whose parameters live in the type implementation that reads them; the root interface is only about being active,
 * and the tick loop is what drives it: it asks {@link #isActive} and hands the edges and the beats to the hooks
 * below. Anything a type can do beyond that (running actions, listening for signals) is a separate interface it
 * implements.
 */
public interface AbilityType {
    MapCodec<AbilityType> CODEC = MxtRegistries.ABILITY_TYPE.byNameCodec().dispatchMap("type", AbilityType::codec, Function.identity());

    MapCodec<? extends AbilityType> codec();

    // The state kinds this type keeps, one class each. Only what the type itself needs goes here; the default is the
    // one kind the tick loop coordinates with, and the action kinds are what an action host declares.
    default void createComponents(Ability ability, DataStorageCollector collector) {
        collector.add(ActiveState.NONE);
    }

    // What "this ability is in effect" means for this type. The tick loop compares it against the stored edge and
    // calls active / inactive on every change, so a type never tracks that itself.
    default boolean isActive(AbilityContext context) {
        return context.definition().condition().test(context.holder(), context.formula());
    }

    // The ability started being held, and stopped. A type that needs to prepare or undo something of its own does it
    // here; signal subscriptions are rebuilt by the bridge instead, because a reload must be able to redo them.
    default void grant(AbilityContext context) {
    }

    default void revoke(AbilityContext context) {
    }

    // The condition started holding: the rising edge, not every passing tick.
    default void active(AbilityContext context) {
    }

    // It stopped holding.
    default void inactive(AbilityContext context) {
    }

    // Every beat, on the cadence tickInterval asks for.
    default void tick(AbilityContext context) {
    }

    // Every beat while it is active. This is where a passive's work belongs.
    default void activeTick(AbilityContext context) {
    }

    // How many ticks apart this type wants its beat; 0 means it has no cadence right now, which the tick loop reads
    // as "not this tick".
    default int tickInterval(AbilityContext context) {
        return 1;
    }

    // A cadence a formula answers with something unusable is no cadence.
    static int cadence(NumberProvider interval, FormulaContext context) {
        double ticks = interval.evaluate(context);
        if (!Double.isFinite(ticks) || ticks < 1.0D) return 0;
        return (int) Math.min(Integer.MAX_VALUE, Math.round(ticks));
    }
}
