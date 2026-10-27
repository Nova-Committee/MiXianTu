package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.action.builtin.block.ChangeAuraBlockAction;
import com.iafenvoy.saturn.data.action.BlockAction;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtBlockActions {
    public static final DeferredRegister<MapCodec<? extends BlockAction>> REGISTRY = DeferredRegister.create(SaturnRegistries.BLOCK_ACTION_TYPE, MiXianTu.MOD_ID);


    public static final DeferredHolder<MapCodec<? extends BlockAction>, MapCodec<ChangeAuraBlockAction>> CHANGE_AURA = REGISTRY.register("change_aura", () -> ChangeAuraBlockAction.CODEC);
}
