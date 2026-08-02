package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.ability.AbilityDefinition;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;

public record HasAbilityEntityCondition(Holder<AbilityDefinition> ability) implements EntityCondition {
    public static final MapCodec<HasAbilityEntityCondition> CODEC = AbilityDefinition.HOLDER_CODEC.fieldOf("ability").xmap(HasAbilityEntityCondition::new, HasAbilityEntityCondition::ability);

    @Override
    public boolean test(Entity entity) {
        return entity.getData(MxtAttachments.ABILITY_HOLDER).has(HolderHelper.id(this.ability));
    }

    @Override
    public MapCodec<HasAbilityEntityCondition> codec() {
        return CODEC;
    }
}
