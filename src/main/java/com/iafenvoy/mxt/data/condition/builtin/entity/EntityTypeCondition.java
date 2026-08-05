package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

public record EntityTypeCondition(EntityType<?> entityType) implements EntityCondition {
    public static final MapCodec<EntityTypeCondition> CODEC = BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("entity_type").xmap(EntityTypeCondition::new, EntityTypeCondition::entityType);

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        return entity.getType() == this.entityType;
    }

    @Override
    public MapCodec<EntityTypeCondition> codec() {
        return CODEC;
    }
}
