package com.iafenvoy.mxt.data.condition.builtin.damage;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.context.condition.DamageConditionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.damagesource.DamageType;
import org.jspecify.annotations.NonNull;

import java.util.Objects;

/**
 * Matches one concrete registered damage type.
 */
public record DamageTypeCondition(Holder<DamageType> damageType) implements DamageCondition {
    public static final MapCodec<DamageTypeCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            DamageType.CODEC.fieldOf("damage_type").forGetter(DamageTypeCondition::damageType)
    ).apply(i, DamageTypeCondition::new));

    @Override
    public boolean test(@NonNull DamageConditionContext ctx) {
        return Objects.equals(ctx.source().type(), this.damageType.value());
    }

    @Override
    public @NonNull MapCodec<DamageTypeCondition> codec() {
        return CODEC;
    }
}
