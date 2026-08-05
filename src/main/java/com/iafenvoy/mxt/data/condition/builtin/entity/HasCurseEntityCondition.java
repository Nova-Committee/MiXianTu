package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;

public record HasCurseEntityCondition(Holder<Curse> curse) implements EntityCondition {
    public static final MapCodec<HasCurseEntityCondition> CODEC = Curse.CODEC.fieldOf("curse").xmap(HasCurseEntityCondition::new, HasCurseEntityCondition::curse);

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        return entity.getData(MxtAttachments.CURSE_HOLDER).instances().containsKey(HolderHelper.id(this.curse));
    }

    @Override
    public MapCodec<HasCurseEntityCondition> codec() {
        return CODEC;
    }
}
