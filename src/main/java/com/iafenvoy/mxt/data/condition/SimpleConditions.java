package com.iafenvoy.mxt.data.condition;

import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;
import com.iafenvoy.mxt.data.context.condition.DamageConditionContext;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/** Utility factories for registering simple context-based conditions without a dedicated class. */
public final class SimpleConditions {
    private SimpleConditions() {
    }

    public static MapCodec<? extends EntityCondition> createEntity(Predicate<EntityConditionContext> predicate) {
        return new EntityCondition() {
            private final MapCodec<? extends EntityCondition> codec = MapCodec.unit(this);

            @Override
            public boolean test(@NotNull EntityConditionContext context) {
                return predicate.test(context);
            }

            @Override
            public @NotNull MapCodec<? extends EntityCondition> codec() {
                return this.codec;
            }
        }.codec();
    }

    public static MapCodec<? extends BiEntityCondition> createBiEntity(Predicate<BiEntityConditionContext> predicate) {
        return new BiEntityCondition() {
            private final MapCodec<? extends BiEntityCondition> codec = MapCodec.unit(this);

            @Override
            public boolean test(@NotNull BiEntityConditionContext context) {
                return predicate.test(context);
            }

            @Override
            public @NotNull MapCodec<? extends BiEntityCondition> codec() {
                return this.codec;
            }
        }.codec();
    }

    public static MapCodec<? extends BlockCondition> createBlock(Predicate<BlockConditionContext> predicate) {
        return new BlockCondition() {
            private final MapCodec<? extends BlockCondition> codec = MapCodec.unit(this);

            @Override
            public boolean test(@NotNull BlockConditionContext context) {
                return predicate.test(context);
            }

            @Override
            public @NotNull MapCodec<? extends BlockCondition> codec() {
                return this.codec;
            }
        }.codec();
    }

    public static MapCodec<? extends ItemCondition> createItem(Predicate<ItemConditionContext> predicate) {
        return new ItemCondition() {
            private final MapCodec<? extends ItemCondition> codec = MapCodec.unit(this);

            @Override
            public boolean test(@NotNull ItemConditionContext context) {
                return predicate.test(context);
            }

            @Override
            public @NotNull MapCodec<? extends ItemCondition> codec() {
                return this.codec;
            }
        }.codec();
    }

    public static MapCodec<? extends DamageCondition> createDamage(Predicate<DamageConditionContext> predicate) {
        return new DamageCondition() {
            private final MapCodec<? extends DamageCondition> codec = MapCodec.unit(this);

            @Override
            public boolean test(@NotNull DamageConditionContext context) {
                return predicate.test(context);
            }

            @Override
            public @NotNull MapCodec<? extends DamageCondition> codec() {
                return this.codec;
            }
        }.codec();
    }
}
