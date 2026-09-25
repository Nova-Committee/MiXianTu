package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.Elements;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Set;

/**
 * True when any element the entity's spirit roots name is one of the listed ones. An element a pack disabled is
 * not part of the answer, exactly as everywhere else.
 */
public record HasElementEntityCondition(
        List<Either<Holder<Element>, TagKey<Element>>> elements) implements EntityCondition {
    public static final MapCodec<HasElementEntityCondition> CODEC = RecordCodecBuilder.<HasElementEntityCondition>mapCodec(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).fieldOf("elements").forGetter(HasElementEntityCondition::elements)
    ).apply(i, HasElementEntityCondition::new)).validate(HasElementEntityCondition::validate);

    // An empty list can never match, so it is a condition that silently never passes: refused at load.
    private static DataResult<HasElementEntityCondition> validate(HasElementEntityCondition condition) {
        return condition.elements().isEmpty()
                ? DataResult.error(() -> "mxt:has_element needs at least one element to ask about")
                : DataResult.success(condition);
    }

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        Set<Holder<Element>> held = Elements.of(ctx.entity());
        return held.stream().anyMatch(element -> RegistryCodecs.matches(this.elements, element));
    }

    @Override
    public @NonNull MapCodec<HasElementEntityCondition> codec() {
        return CODEC;
    }
}
