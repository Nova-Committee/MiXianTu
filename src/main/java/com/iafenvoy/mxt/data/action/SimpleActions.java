package com.iafenvoy.mxt.data.action;

import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.data.context.action.BlockActionContext;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.data.context.action.ItemActionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/** Utility factories for registering simple context-based actions without a dedicated class. */
public final class SimpleActions {
    private SimpleActions() {
    }

    public static MapCodec<? extends EntityAction> createEntity(Consumer<EntityActionContext> action) {
        return new EntityAction() {
            private final MapCodec<? extends EntityAction> codec = MapCodec.unit(this);

            @Override
            public void execute(@NotNull EntityActionContext context) {
                action.accept(context);
            }

            @Override
            public @NotNull MapCodec<? extends EntityAction> codec() {
                return this.codec;
            }
        }.codec();
    }

    public static MapCodec<? extends BiEntityAction> createBiEntity(Consumer<BiEntityActionContext> action) {
        return new BiEntityAction() {
            private final MapCodec<? extends BiEntityAction> codec = MapCodec.unit(this);

            @Override
            public void execute(@NotNull BiEntityActionContext context) {
                action.accept(context);
            }

            @Override
            public @NotNull MapCodec<? extends BiEntityAction> codec() {
                return this.codec;
            }
        }.codec();
    }

    public static MapCodec<? extends BlockAction> createBlock(Consumer<BlockActionContext> action) {
        return new BlockAction() {
            private final MapCodec<? extends BlockAction> codec = MapCodec.unit(this);

            @Override
            public void execute(@NotNull BlockActionContext context) {
                action.accept(context);
            }

            @Override
            public @NotNull MapCodec<? extends BlockAction> codec() {
                return this.codec;
            }
        }.codec();
    }

    public static MapCodec<? extends ItemAction> createItem(Consumer<ItemActionContext> action) {
        return new ItemAction() {
            private final MapCodec<? extends ItemAction> codec = MapCodec.unit(this);

            @Override
            public void execute(@NotNull ItemActionContext context) {
                action.accept(context);
            }

            @Override
            public @NotNull MapCodec<? extends ItemAction> codec() {
                return this.codec;
            }
        }.codec();
    }
}
