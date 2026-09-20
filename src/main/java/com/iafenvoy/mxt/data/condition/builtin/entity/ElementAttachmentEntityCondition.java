package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.aura.AuraRequirement;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.runtime.element.ElementReactionService;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.DataResult;
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
 *
 * <p>A {@code mxt:disabled} element answers no, whatever is left on the body: the buildup of an element a pack
 * took out of play is not a fact about that element any more than its relations or its aura are, and this is
 * the only reader of the accumulation, so the rule is stated here once. An empty table is refused at load
 * rather than read as "always true".</p>
 */
public record ElementAttachmentEntityCondition(Map<Holder<Element>, AuraRequirement> elements) implements EntityCondition {
    public static final MapCodec<ElementAttachmentEntityCondition> CODEC = CollectionCodecs.map(Element.CODEC, AuraRequirement.CODEC)
            .fieldOf("elements").xmap(ElementAttachmentEntityCondition::new, ElementAttachmentEntityCondition::elements)
            .validate(ElementAttachmentEntityCondition::validate);

    private static DataResult<ElementAttachmentEntityCondition> validate(ElementAttachmentEntityCondition condition) {
        return condition.elements().isEmpty()
                ? DataResult.error(() -> "mxt:element_attachment needs at least one element to read")
                : DataResult.success(condition);
    }

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return this.elements.entrySet().stream()
                .allMatch(entry -> Elements.enabled(entry.getKey())
                        && entry.getValue().test(ElementReactionService.amount(entity, entry.getKey()), context));
    }

    @Override
    public @NonNull MapCodec<ElementAttachmentEntityCondition> codec() {
        return CODEC;
    }
}
