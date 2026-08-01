package com.iafenvoy.mxt.data.action.builtin;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

public enum NoOpEntityAction implements EntityAction {
    INSTANCE;
    public static final MapCodec<NoOpEntityAction> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public void execute(Entity entity) {
    }

    @Override
    public MapCodec<NoOpEntityAction> codec() {
        return CODEC;
    }
}
