package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public record HasAbilityEntityCondition(Identifier ability) implements EntityCondition {
    public static final MapCodec<HasAbilityEntityCondition> CODEC = Identifier.CODEC.fieldOf("ability").xmap(HasAbilityEntityCondition::new, HasAbilityEntityCondition::ability);

    @Override
    public boolean test(Entity entity) {
        return entity.getData(MxtAttachments.ABILITY_HOLDER).has(this.ability);
    }

    @Override
    public MapCodec<HasAbilityEntityCondition> codec() {
        return CODEC;
    }
}
