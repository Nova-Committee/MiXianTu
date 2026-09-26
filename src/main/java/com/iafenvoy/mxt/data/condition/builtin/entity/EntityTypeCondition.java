package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.NonNull;

public record EntityTypeCondition(EntityType<?> entityType) implements EntityCondition {
    public static final MapCodec<EntityTypeCondition> CODEC = BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity_type").xmap(EntityTypeCondition::new, EntityTypeCondition::entityType);

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return ctx.entity().getType() == this.entityType;
    }

    @Override
    public @NonNull MapCodec<EntityTypeCondition> codec() {
        return CODEC;
    }
}
