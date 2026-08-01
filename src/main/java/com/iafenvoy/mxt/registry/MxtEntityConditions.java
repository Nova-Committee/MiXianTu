package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtEntityConditions {
    public static final DeferredRegister<MapCodec<? extends EntityCondition>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.ENTITY_CONDITION_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<AlwaysTrueEntityCondition>> ALWAYS_TRUE = REGISTRY.register("always_true", () -> AlwaysTrueEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<AndEntityCondition>> AND = REGISTRY.register("and", () -> AndEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<NotEntityCondition>> NOT = REGISTRY.register("not", () -> NotEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<OrEntityCondition>> OR = REGISTRY.register("or", () -> OrEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<SneakingEntityCondition>> SNEAKING = REGISTRY.register("sneaking", () -> SneakingEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<HasAbilityEntityCondition>> HAS_ABILITY = REGISTRY.register("has_ability", () -> HasAbilityEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<HasCurseEntityCondition>> HAS_CURSE = REGISTRY.register("has_curse", () -> HasCurseEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<RealmEntityCondition>> REALM = REGISTRY.register("realm", () -> RealmEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<ResourceCompareEntityCondition>> RESOURCE_COMPARE = REGISTRY.register("resource_compare", () -> ResourceCompareEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<EntityTypeTagCondition>> ENTITY_TAG = REGISTRY.register("entity_tag", () -> EntityTypeTagCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends EntityCondition>, MapCodec<FormationMemberEntityCondition>> FORMATION_MEMBER = REGISTRY.register("formation_member", () -> FormationMemberEntityCondition.CODEC);

    private MxtEntityConditions() {
    }
}
