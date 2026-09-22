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
 * True when the actor is adapted to an element the target carries - the mirror of {@code mxt:element_overcomes}.
 * Only the relation is asked here; the multiplier is the damage pipeline's business.
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
