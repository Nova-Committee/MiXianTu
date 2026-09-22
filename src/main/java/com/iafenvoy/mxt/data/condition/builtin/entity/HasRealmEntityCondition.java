package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

/**
 * True when the entity has entered the realm chain of the named aura: the chain is keyed by the aura in the state
 * attachment, so a plain counter has no realm chain to have entered.
 */
public record HasRealmEntityCondition(Holder<Aura> aura) implements EntityCondition {
    public static final MapCodec<HasRealmEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Aura.CODEC.fieldOf("aura").forGetter(HasRealmEntityCondition::aura)
    ).apply(i, HasRealmEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext context) {
        return context.entity().getData(MxtAttachments.CULTIVATION).realmStage(this.aura) != null;
    }

    @Override
    public @NonNull MapCodec<HasRealmEntityCondition> codec() {
        return CODEC;
    }
}
