package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.action.builtin.item.AddAbilityAction;
import com.iafenvoy.mxt.data.action.builtin.item.ChargeArtifactItemAction;
import com.iafenvoy.saturn.data.action.ItemAction;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtItemActions {
    public static final DeferredRegister<MapCodec<? extends ItemAction>> REGISTRY = DeferredRegister.create(SaturnRegistries.ITEM_ACTION_TYPE, MiXianTu.MOD_ID);


    public static final DeferredHolder<MapCodec<? extends ItemAction>, MapCodec<ChargeArtifactItemAction>> CHARGE_ARTIFACT = REGISTRY.register("charge_artifact", () -> ChargeArtifactItemAction.CODEC);
    public static final DeferredHolder<MapCodec<? extends ItemAction>, MapCodec<AddAbilityAction>> ADD_ABILITY = REGISTRY.register("add_ability", () -> AddAbilityAction.CODEC);
}
