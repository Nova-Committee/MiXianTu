package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.cultivation.CultivationProfiles;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

/**
 * Matches entities that have entered the realm chain of the specified value.
 */
public record HasRealmEntityCondition(Holder<Resource> resource) implements EntityCondition {
    public static final MapCodec<HasRealmEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Resource.CODEC.fieldOf("resource").forGetter(HasRealmEntityCondition::resource)
    ).apply(i, HasRealmEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext context) {
        return CultivationProfiles.holder(context.entity().level().registryAccess(), this.resource)
                .map(cultivation -> context.entity().getData(MxtAttachments.CULTIVATION).realmStage(cultivation) != null)
                .orElse(false);
    }

    @Override
    public @NonNull MapCodec<HasRealmEntityCondition> codec() {
        return CODEC;
    }
}
