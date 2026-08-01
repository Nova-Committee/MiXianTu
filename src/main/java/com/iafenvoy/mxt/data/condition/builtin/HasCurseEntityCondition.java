package com.iafenvoy.mxt.data.condition.builtin;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

public record HasCurseEntityCondition(Identifier curse) implements EntityCondition {
    public static final MapCodec<HasCurseEntityCondition> CODEC = Identifier.CODEC.fieldOf("curse").xmap(HasCurseEntityCondition::new, HasCurseEntityCondition::curse);

    @Override
    public boolean test(Entity entity) {
        return entity.getData(MxtAttachments.CURSE_HOLDER).instances().containsKey(this.curse);
    }

    @Override
    public MapCodec<HasCurseEntityCondition> codec() {
        return CODEC;
    }
}
