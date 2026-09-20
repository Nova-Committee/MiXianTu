package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.aura.AuraRequirement;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.runtime.element.ElementReactionService;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Tests how much of an element has built up on an entity, with the same lower/upper bound shape aura ranges
 * use. Every entry has to pass.
 *
 * <p>It is the read side of the accumulation system: a pack can make an effect depend on how much fire a body
 * is carrying without any reaction firing, which is what a "the more it builds the worse it gets" design
 * needs.</p>
 */
public record ElementAttachmentEntityCondition(Map<Holder<Element>, AuraRequirement> elements) implements EntityCondition {
    public static final MapCodec<ElementAttachmentEntityCondition> CODEC = CollectionCodecs.map(Element.CODEC, AuraRequirement.CODEC)
            .fieldOf("elements").xmap(ElementAttachmentEntityCondition::new, ElementAttachmentEntityCondition::elements);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return this.elements.entrySet().stream()
                .allMatch(entry -> entry.getValue().test(ElementReactionService.amount(entity, entry.getKey()), context));
    }

    @Override
    public @NonNull MapCodec<ElementAttachmentEntityCondition> codec() {
        return CODEC;
    }
}
