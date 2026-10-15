package com.iafenvoy.mxt.data.condition.builtin.damage;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.DamageConditionContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NonNull;

import java.util.Optional;

/**
 * Matches projectile damage, optionally filtering the projectile entity and its condition.
 */
public record ProjectileDamageCondition(Optional<EntityType<?>> projectile,
                                        EntityCondition projectileCondition) implements DamageCondition {
    public static final MapCodec<ProjectileDamageCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().optionalFieldOf("projectile").forGetter(ProjectileDamageCondition::projectile),
            EntityCondition.optionalCodec("projectile_condition").forGetter(ProjectileDamageCondition::projectileCondition)
    ).apply(i, ProjectileDamageCondition::new));

    @Override
    public boolean test(@NonNull DamageConditionContext ctx) {
        DamageSource source = ctx.source();
        Entity entity = source.getDirectEntity();
        return source.is(DamageTypeTags.IS_PROJECTILE)
                && entity != null
                && this.projectile.map(entity.getType()::equals).orElse(true)
                && this.projectileCondition.test(entity, ctx);
    }

    @Override
    public @NonNull MapCodec<ProjectileDamageCondition> codec() {
        return CODEC;
    }
}
