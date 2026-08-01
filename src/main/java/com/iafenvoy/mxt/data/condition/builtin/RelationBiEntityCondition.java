package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

/**
 * Relationship predicate kept distinct from the team predicate so each builtin
 * registration owns a unique codec value, as required by Minecraft registries.
 */
public record RelationBiEntityCondition(boolean allied) implements BiEntityCondition {
    public static final MapCodec<RelationBiEntityCondition> CODEC = Codec.BOOL.optionalFieldOf("allied", true)
            .xmap(RelationBiEntityCondition::new, RelationBiEntityCondition::allied);

    @Override
    public boolean test(Entity actor, Entity target, FormulaContext context) {
        return actor.isAlliedTo(target) == this.allied;
    }

    @Override
    public MapCodec<RelationBiEntityCondition> codec() {
        return CODEC;
    }
}
