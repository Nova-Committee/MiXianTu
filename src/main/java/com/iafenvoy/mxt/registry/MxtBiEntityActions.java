package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.builtin.bientity.*;
import com.iafenvoy.mxt.data.action.builtin.bientity.meta.*;
import com.iafenvoy.mxt.integration.kubejs.type.action.JsBiEntityAction;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtBiEntityActions {
    public static final DeferredRegister<MapCodec<? extends BiEntityAction>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.BI_ENTITY_ACTION_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<BiEntityNoOpAction>> NO_OP = REGISTRY.register("no_op", () -> BiEntityNoOpAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<JsBiEntityAction>> JS = REGISTRY.register("js", () -> JsBiEntityAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiEntityAction>, MapCodec<SequenceBiEntityAction>> SEQUENCE = REGISTRY.register("sequence", () -> SequenceBiEntityAction.CODEC);
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

    private MxtBiEntityActions() {
    }
}
