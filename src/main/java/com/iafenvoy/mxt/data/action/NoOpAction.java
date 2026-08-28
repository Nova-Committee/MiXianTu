package com.iafenvoy.mxt.data.action;

import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.data.context.action.BlockActionContext;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.context.action.ItemActionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

public enum NoOpAction implements BiEntityAction, BlockAction, EntityAction, ItemAction {
    INSTANCE;
    public static final MapCodec<NoOpAction> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public @NotNull MapCodec<NoOpAction> codec() {
        return CODEC;
    }

    @Override
    public void execute(@NotNull BiEntityActionContext context) {
    }

    @Override
    public void execute(@NotNull BlockActionContext context) {
    }

    @Override
    public void execute(@NotNull EntityActionContext context) {
    }

    @Override
    public void execute(@NotNull ItemActionContext context) {
    }
}
