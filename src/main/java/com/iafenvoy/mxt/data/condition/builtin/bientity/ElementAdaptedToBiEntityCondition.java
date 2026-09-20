package com.iafenvoy.mxt.data.condition.builtin.bientity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.Set;

/**
 * True when the actor is adapted to an element the target carries - the defensive mirror of
 * {@code mxt:element_overcomes}.
 *
 * <p>{@code adapted_to} is the half of an element's relations that damage reduction reads, and until this
 * condition existed a pack could ask "do I beat you" but not "do I resist you", which is the question a ward, a
 * shield or a reactive effect is actually written around. The relation is the question here, not what it is
 * worth: the multiplier is the damage pipeline's business.</p>
 */
public enum ElementAdaptedToBiEntityCondition implements BiEntityCondition {
    INSTANCE;
    public static final MapCodec<ElementAdaptedToBiEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        Set<Holder<Element>> actorElements = Elements.of(actor);
        Set<Holder<Element>> targetElements = Elements.of(target);
        return actorElements.stream().anyMatch(element -> targetElements.stream()
                .anyMatch(targetElement -> element.value().adapts(targetElement)));
    }

    @Override
    public @NonNull MapCodec<ElementAdaptedToBiEntityCondition> codec() {
        return CODEC;
    }
}
