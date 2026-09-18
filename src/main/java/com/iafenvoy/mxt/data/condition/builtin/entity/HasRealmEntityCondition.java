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
 * Matches entities that have entered the realm chain of the specified aura.
 * <p>
 * The chain is keyed by the aura in the state attachment, so this names an aura rather than the value it is
 * counted in: a plain counter has no realm chain to have entered.
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
