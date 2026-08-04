package com.iafenvoy.mxt.data.condition.builtin.damage;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.AlwaysTrueEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.Optional;

/**
 * Matches projectile damage, optionally filtering the projectile entity and its condition.
 */
public record ProjectileDamageCondition(Optional<Holder<EntityType<?>>> projectile,
                                        EntityCondition projectileCondition) implements DamageCondition {
    public static final MapCodec<ProjectileDamageCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RegistryFixedCodec.create(Registries.ENTITY_TYPE).optionalFieldOf("projectile").forGetter(ProjectileDamageCondition::projectile),
            EntityCondition.CODEC.optionalFieldOf("projectile_condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(ProjectileDamageCondition::projectileCondition)
    ).apply(instance, ProjectileDamageCondition::new));

    @Override
    public boolean test(DamageSource source, float amount, FormulaContext context) {
        Entity entity = source.getDirectEntity();
        return source.is(DamageTypeTags.IS_PROJECTILE)
                && entity != null
                && this.projectile.map(holder -> holder.value() == entity.getType()).orElse(true)
                && this.projectileCondition.test(entity, context);
    }

    @Override
    public MapCodec<ProjectileDamageCondition> codec() {
        return CODEC;
    }
}
