package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.builtin.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in formation modules. A datapack selects one by writing its id in a module's {@code type}, and the
 * generic three are the beats a formation used to declare with top-level fields.
 */
@SuppressWarnings("unused")
public final class MxtFormationActionTypes {
    public static final DeferredRegister<MapCodec<? extends FormationActionType>> REGISTRY = DeferredRegister.create(MxtRegistries.FORMATION_ACTION_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends FormationActionType>, MapCodec<EmptyFormationAction>> EMPTY = REGISTRY.register("empty", () -> EmptyFormationAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends FormationActionType>, MapCodec<AttackFormationAction>> ATTACK = REGISTRY.register("attack", () -> AttackFormationAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends FormationActionType>, MapCodec<BuffFormationAction>> BUFF = REGISTRY.register("buff", () -> BuffFormationAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends FormationActionType>, MapCodec<AuraFormationAction>> AURA = REGISTRY.register("aura", () -> AuraFormationAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends FormationActionType>, MapCodec<ProtectionFormationAction>> PROTECTION = REGISTRY.register("protection", () -> ProtectionFormationAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends FormationActionType>, MapCodec<ParticleFormationAction>> PARTICLE = REGISTRY.register("particle", () -> ParticleFormationAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends FormationActionType>, MapCodec<SendMessageFormationAction>> SEND_MESSAGE = REGISTRY.register("send_message", () -> SendMessageFormationAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends FormationActionType>, MapCodec<ActivateFormationAction>> ACTIVATE = REGISTRY.register("activate", () -> ActivateFormationAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends FormationActionType>, MapCodec<TickFormationAction>> TICK = REGISTRY.register("tick", () -> TickFormationAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends FormationActionType>, MapCodec<InactivateFormationAction>> INACTIVATE = REGISTRY.register("inactivate", () -> InactivateFormationAction.CODEC);

    private MxtFormationActionTypes() {
    }
}
