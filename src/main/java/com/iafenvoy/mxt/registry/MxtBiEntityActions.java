package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.compat.kubejs.type.action.JsBiEntityAction;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.action.builtin.bientity.*;
import com.iafenvoy.mxt.data.action.builtin.bientity.meta.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.iafenvoy.mxt.data.action.SimpleActions.createBiEntity;

@SuppressWarnings("unused")
public final class MxtBiEntityActions {
    public static final DeferredRegister<MapCodec<? extends BiEntityAction>> REGISTRY = DeferredRegister.create(MxtRegistries.BI_ENTITY_ACTION_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<NoOpAction>> NO_OP = REGISTRY.register("no_op", () -> NoOpAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<JsBiEntityAction>> JS = REGISTRY.register("js", () -> JsBiEntityAction.CODEC);

    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<SequenceBiEntityAction>> SEQUENCE = REGISTRY.register("sequence", () -> SequenceBiEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<? extends BiEntityAction>> MOUNT = REGISTRY.register("mount", () -> createBiEntity(ctx -> ctx.actor().startRiding(ctx.target())));
    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<DamageTargetBiEntityAction>> DAMAGE_TARGET = REGISTRY.register("damage_target", () -> DamageTargetBiEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<HealTargetBiEntityAction>> HEAL_TARGET = REGISTRY.register("heal_target", () -> HealTargetBiEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<TransferResourceBiEntityAction>> TRANSFER_RESOURCE = REGISTRY.register("transfer_resource", () -> TransferResourceBiEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<AddVelocityAction>> ADD_VELOCITY = REGISTRY.register("add_velocity", () -> AddVelocityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<TeleportAction>> TELEPORT = REGISTRY.register("teleport", () -> TeleportAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<ChanceAction>> CHANCE = REGISTRY.register("chance", () -> ChanceAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<IfElseAction>> IF_ELSE = REGISTRY.register("if_else", () -> IfElseAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<ChoiceAction>> CHOICE = REGISTRY.register("choice", () -> ChoiceAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<ActorAction>> ACTOR_ACTION = REGISTRY.register("actor_action", () -> ActorAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<TargetAction>> TARGET_ACTION = REGISTRY.register("target_action", () -> TargetAction.CODEC);
}
