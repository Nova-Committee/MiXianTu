package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.condition.AlwaysTrueCondition;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.bientity.*;
import com.iafenvoy.mxt.data.condition.builtin.bientity.meta.*;
import com.iafenvoy.mxt.compat.kubejs.type.condition.JsBiEntityCondition;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.iafenvoy.mxt.data.condition.SimpleConditions.createBiEntity;

@SuppressWarnings("unused")
public final class MxtBiEntityConditions {
    public static final DeferredRegister<MapCodec<? extends BiEntityCondition>> REGISTRY = DeferredRegister.create(MxtRegistries.BI_ENTITY_CONDITION_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<AlwaysTrueCondition>> ALWAYS_TRUE = REGISTRY.register("always_true", () -> AlwaysTrueCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<JsBiEntityCondition>> JS = REGISTRY.register("js", () -> JsBiEntityCondition.CODEC);

    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<AndBiEntityCondition>> AND = REGISTRY.register("and", () -> AndBiEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<DistanceBiEntityCondition>> DISTANCE = REGISTRY.register("distance", () -> DistanceBiEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<TeamBiEntityCondition>> TEAM = REGISTRY.register("team", () -> TeamBiEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<RelationBiEntityCondition>> RELATION = REGISTRY.register("relation", () -> RelationBiEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<ElementOvercomesBiEntityCondition>> ELEMENT_OVERCOMES = REGISTRY.register("element_overcomes", () -> ElementOvercomesBiEntityCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<CanSeeCondition>> CAN_SEE = REGISTRY.register("can_see", () -> CanSeeCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<ChanceCondition>> CHANCE = REGISTRY.register("chance", () -> ChanceCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<ConstantCondition>> CONSTANT = REGISTRY.register("constant", () -> ConstantCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<NotCondition>> NOT = REGISTRY.register("not", () -> NotCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<OrCondition>> OR = REGISTRY.register("or", () -> OrCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<ActorCondition>> ACTOR_CONDITION = REGISTRY.register("actor_condition", () -> ActorCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<TargetCondition>> TARGET_CONDITION = REGISTRY.register("target_condition", () -> TargetCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<BothCondition>> BOTH = REGISTRY.register("both", () -> BothCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<EitherCondition>> EITHER = REGISTRY.register("either", () -> EitherCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<RidingRecursiveCondition>> RIDING_RECURSIVE = REGISTRY.register("riding_recursive", () -> RidingRecursiveCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<? extends BiEntityCondition>> SAME_TEAM = REGISTRY.register("same_team", () -> createBiEntity(ctx -> ctx.actor().getTeam() != null && ctx.actor().isAlliedTo(ctx.target())));
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<RelativeRotationCondition>> RELATIVE_ROTATION = REGISTRY.register("relative_rotation", () -> RelativeRotationCondition.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityCondition>, MapCodec<UndirectedCondition>> UNDIRECTED = REGISTRY.register("undirected", () -> UndirectedCondition.CODEC);
}
