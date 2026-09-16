package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.formation.AttackFormationAction;
import com.iafenvoy.mxt.data.formation.BuffFormationAction;
import com.iafenvoy.mxt.data.formation.EmptyFormationAction;
import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.ProtectionFormationAction;
import com.iafenvoy.mxt.data.formation.RangeDisplayFormationAction;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in formation modules. A datapack selects one by writing its id in a module's {@code type}.
 */
@SuppressWarnings("unused")
public final class MxtFormationActionTypes {
    public static final DeferredRegister<MapCodec<? extends FormationActionType>> REGISTRY = DeferredRegister.create(MxtRegistries.FORMATION_ACTION_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends FormationActionType>, MapCodec<EmptyFormationAction>> NONE = REGISTRY.register("none", () -> EmptyFormationAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends FormationActionType>, MapCodec<AttackFormationAction>> ATTACK = REGISTRY.register("attack", () -> AttackFormationAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends FormationActionType>, MapCodec<BuffFormationAction>> BUFF = REGISTRY.register("buff", () -> BuffFormationAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends FormationActionType>, MapCodec<ProtectionFormationAction>> PROTECTION = REGISTRY.register("protection", () -> ProtectionFormationAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends FormationActionType>, MapCodec<RangeDisplayFormationAction>> RANGE_DISPLAY = REGISTRY.register("range_display", () -> RangeDisplayFormationAction.CODEC);

    private MxtFormationActionTypes() {
    }
}
