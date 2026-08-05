package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;

public record HasAbilityEntityCondition(Holder<Ability> ability) implements EntityCondition {
    public static final MapCodec<HasAbilityEntityCondition> CODEC = Ability.CODEC.fieldOf("ability").xmap(HasAbilityEntityCondition::new, HasAbilityEntityCondition::ability);

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        return entity.getData(MxtAttachments.ABILITY_HOLDER).has(HolderHelper.id(this.ability));
    }

    @Override
    public MapCodec<HasAbilityEntityCondition> codec() {
        return CODEC;
    }
}
