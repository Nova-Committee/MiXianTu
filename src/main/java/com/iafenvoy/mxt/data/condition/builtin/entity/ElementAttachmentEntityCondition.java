package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.aura.AuraRequirement;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.runtime.element.ElementReactionService;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Reads how much of an element has built up on an entity. An element disabled by {@code mxt:disabled} answers no
 * however much has built up, and an empty table is refused at load rather than read as "always true".
 */
public record ElementAttachmentEntityCondition(
        Map<Holder<Element>, AuraRequirement> elements) implements EntityCondition {
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
        return this.elements.entrySet().stream().allMatch(entry -> Elements.enabled(entry.getKey())
                && entry.getValue().test(ElementReactionService.amount(ctx.entity(), entry.getKey()), ctx.formula()));
    }

    @Override
    public @NonNull MapCodec<ElementAttachmentEntityCondition> codec() {
        return CODEC;
    }
}
