package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * Matches an entity against a vanilla or datapack entity-type tag.
 */
public record EntityTypeTagCondition(TagKey<EntityType<?>> tag) implements EntityCondition {
    public static final MapCodec<EntityTypeTagCondition> CODEC = TagKey.hashedCodec(Registries.ENTITY_TYPE).fieldOf("tag").xmap(EntityTypeTagCondition::new, EntityTypeTagCondition::tag);

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        return entity.getType().builtInRegistryHolder().is(this.tag);
    }

    @Override
    public MapCodec<EntityTypeTagCondition> codec() {
        return CODEC;
    }
}
