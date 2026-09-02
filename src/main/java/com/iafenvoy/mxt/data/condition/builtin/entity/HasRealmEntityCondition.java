package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

/**
 * Matches entities that have entered a realm chain for the specified resource.
 */
public record HasRealmEntityCondition(Holder<Resource> resource) implements EntityCondition {
    public static final MapCodec<HasRealmEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Resource.CODEC.fieldOf("resource").forGetter(HasRealmEntityCondition::resource)
    ).apply(i, HasRealmEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext context) {
        return context.entity().getData(MxtAttachments.CULTIVATION).realmStage(this.resource) != null;
    }

    @Override
    public @NonNull MapCodec<HasRealmEntityCondition> codec() {
        return CODEC;
    }
}
