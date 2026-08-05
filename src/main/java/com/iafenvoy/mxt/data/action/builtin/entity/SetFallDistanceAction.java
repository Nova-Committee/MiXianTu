package com.iafenvoy.mxt.data.action.builtin.entity;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

import net.minecraft.world.entity.Entity;

public record SetFallDistanceAction(float distance) implements EntityAction {
    public static final MapCodec<SetFallDistanceAction> CODEC = Codec.FLOAT.fieldOf("distance").xmap(SetFallDistanceAction::new, SetFallDistanceAction::distance);

    @Override
    public void execute(Entity entity, FormulaContext context) {
        entity.fallDistance = this.distance;
    }

    @Override
    public MapCodec<SetFallDistanceAction> codec() {
        return CODEC;
    }
}
