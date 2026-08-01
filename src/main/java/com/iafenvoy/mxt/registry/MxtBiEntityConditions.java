package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtBiEntityConditions {
    public static final DeferredRegister<MapCodec<? extends BiEntityCondition>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.BI_ENTITY_CONDITION_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<AlwaysTrueBiEntityCondition>> ALWAYS_TRUE = REGISTRY.register("always_true", () -> AlwaysTrueBiEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<AndBiEntityCondition>> AND = REGISTRY.register("and", () -> AndBiEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<DistanceBiEntityCondition>> DISTANCE = REGISTRY.register("distance", () -> DistanceBiEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<TeamBiEntityCondition>> TEAM = REGISTRY.register("team", () -> TeamBiEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<RelationBiEntityCondition>> RELATION = REGISTRY.register("relation", () -> RelationBiEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<ElementOvercomesBiEntityCondition>> ELEMENT_OVERCOMES = REGISTRY.register("element_overcomes", () -> ElementOvercomesBiEntityCondition.CODEC);

    private MxtBiEntityConditions() {
    }
}
