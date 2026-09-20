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
 * True when at least one of the actor's spirit-root elements overcomes a target root element. The relation
 * itself is the question here, not what it is worth: how much an edge multiplies damage is the damage
 * pipeline's business ({@code DamageCalculationService}).
 */
public enum ElementOvercomesBiEntityCondition implements BiEntityCondition {
    INSTANCE;
    public static final MapCodec<ElementOvercomesBiEntityCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        Set<Holder<Element>> actorElements = Elements.of(actor);
        Set<Holder<Element>> targetElements = Elements.of(target);
        return actorElements.stream().anyMatch(element -> targetElements.stream()
                .anyMatch(targetElement -> element.value().overcomes(targetElement)));
    }

    @Override
    public @NonNull MapCodec<ElementOvercomesBiEntityCondition> codec() {
        return CODEC;
    }
}
