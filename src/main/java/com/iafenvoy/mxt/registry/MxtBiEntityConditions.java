package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.condition.builtin.bientity.ElementAdaptedToBiEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.bientity.ElementOvercomesBiEntityCondition;
import com.iafenvoy.mxt.runtime.friend.FriendService;
import com.iafenvoy.saturn.data.condition.BiEntityCondition;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.iafenvoy.saturn.data.condition.SimpleConditions.createBiEntity;

@SuppressWarnings("unused")
public final class MxtBiEntityConditions {
    public static final DeferredRegister<MapCodec<? extends BiEntityCondition>> REGISTRY = DeferredRegister.create(SaturnRegistries.BI_ENTITY_CONDITION_TYPE, MiXianTu.MOD_ID);


    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<? extends BiEntityCondition>> FRIEND = REGISTRY.register("friend", () -> createBiEntity(ctx -> FriendService.isFriend(ctx.actor(), ctx.target())));
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<ElementOvercomesBiEntityCondition>> ELEMENT_OVERCOMES = REGISTRY.register("element_overcomes", () -> ElementOvercomesBiEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<ElementAdaptedToBiEntityCondition>> ELEMENT_ADAPTED_TO = REGISTRY.register("element_adapted_to", () -> ElementAdaptedToBiEntityCondition.CODEC);
}
