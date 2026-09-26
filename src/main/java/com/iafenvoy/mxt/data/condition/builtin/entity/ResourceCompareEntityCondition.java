package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

public record ResourceCompareEntityCondition(Holder<Resource> resource,
                                             NumberProvider min) implements EntityCondition {
    public static final MapCodec<ResourceCompareEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Resource.CODEC.fieldOf("resource").forGetter(ResourceCompareEntityCondition::resource), NumberProvider.CODEC.fieldOf("min").forGetter(ResourceCompareEntityCondition::min)
    ).apply(i, ResourceCompareEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return ctx.entity().getData(MxtAttachments.RESOURCE_HOLDER).get(this.resource) >= this.min.evaluate(ctx.formula());
    }

    @Override
    public @NonNull MapCodec<ResourceCompareEntityCondition> codec() {
        return CODEC;
    }
}
