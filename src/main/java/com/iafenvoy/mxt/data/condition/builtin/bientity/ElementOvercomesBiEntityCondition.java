package com.iafenvoy.mxt.data.condition.builtin.bientity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

import java.util.Set;

/**
 * True when one of the actor's spirit-root elements overcomes a target root element. Only the relation is asked
 * here; how much an edge multiplies damage is the damage pipeline's business.
 */
public enum ElementOvercomesBiEntityCondition implements BiEntityCondition {
    INSTANCE;
    public static final MapCodec<ElementOvercomesBiEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        Set<Holder<Element>> actorElements = Elements.of(ctx.actor());
        Set<Holder<Element>> targetElements = Elements.of(ctx.target());
        return actorElements.stream().anyMatch(element -> targetElements.stream()
                .anyMatch(targetElement -> element.value().overcomes(targetElement)));
    }

    @Override
    public @NonNull MapCodec<ElementOvercomesBiEntityCondition> codec() {
        return CODEC;
    }
}
