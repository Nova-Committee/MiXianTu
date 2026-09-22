package com.iafenvoy.mxt.data.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.advancements.CriterionTriggerInstance;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger.SimpleInstance;
import net.minecraft.resources.Identifier;

/**
 * A trigger that hands its decision to one of vanilla's own criterion instances, reconstructing the arguments
 * vanilla's call site would have passed: it is repeatable and runtime-only, with no advancement lifecycle.
 *
 * @param <T> the vanilla instance type this trigger decodes
 */
public final class VanillaTrigger<T extends CriterionTriggerInstance> implements Trigger {
    private final Identifier signalType;
    private final Codec<T> vanilla;
    private final T instance;
    private final Matcher<T> matcher;

    private VanillaTrigger(Identifier signalType, Codec<T> vanilla, T instance, Matcher<T> matcher) {
        this.signalType = signalType;
        this.vanilla = vanilla;
        this.instance = instance;
        this.matcher = matcher;
    }

    // A dispatched trigger decodes its fields from the same object that names its type, hence a map codec.
    public static <T extends CriterionTriggerInstance> MapCodec<VanillaTrigger<T>> mapCodec(Identifier signalType, Codec<T> vanilla, Matcher<T> matcher) {
        return MapCodec.assumeMapUnsafe(vanilla.xmap(
                instance -> new VanillaTrigger<>(signalType, vanilla, instance, matcher),
                VanillaTrigger::instance));
    }

    @Override
    public MapCodec<? extends Trigger> codec() {
        return mapCodec(this.signalType, this.vanilla, this.matcher);
    }

    @Override
    public Identifier signalType() {
        return this.signalType;
    }

    @Override
    public boolean matches(TriggerSignal signal) {
        if (!this.signalType.equals(signal.type())) return false;
        // SimpleCriterionTrigger checks an instance's own player predicate before running the instance, so a
        // ported trigger does the same; the instance's own matches method never looks at that field.
        if (this.instance instanceof SimpleInstance simple
                && !VanillaTriggerSupport.playerPredicate(simple.player(), signal.context())) return false;
        return this.matcher.matches(this.instance, signal.context());
    }

    public T instance() {
        return this.instance;
    }

    /**
     * Rebuilds the payload one vanilla criterion would have received.
     */
    @FunctionalInterface
    public interface Matcher<T extends CriterionTriggerInstance> {
        boolean matches(T instance, TriggerContext context);
    }
}
