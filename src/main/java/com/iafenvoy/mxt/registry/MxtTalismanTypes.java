package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.talisman.TalismanType;
import com.iafenvoy.mxt.data.talisman.type.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtTalismanTypes {
    public static final DeferredRegister<MapCodec<? extends TalismanType>> REGISTRY = DeferredRegister.create(MxtRegistries.TALISMAN_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends TalismanType>, MapCodec<EmptyTalismanType>> EMPTY = REGISTRY.register("empty", () -> EmptyTalismanType.CODEC);
    public static final DeferredHolder<MapCodec<? extends TalismanType>, MapCodec<SelfTalismanType>> SELF = REGISTRY.register("self", () -> SelfTalismanType.CODEC);
    public static final DeferredHolder<MapCodec<? extends TalismanType>, MapCodec<AreaTalismanType>> AREA = REGISTRY.register("area", () -> AreaTalismanType.CODEC);
    public static final DeferredHolder<MapCodec<? extends TalismanType>, MapCodec<CrosshairTalismanType>> CROSSHAIR = REGISTRY.register("crosshair", () -> CrosshairTalismanType.CODEC);
    public static final DeferredHolder<MapCodec<? extends TalismanType>, MapCodec<ThrownTalismanType>> THROWN = REGISTRY.register("thrown", () -> ThrownTalismanType.CODEC);

    private MxtTalismanTypes() {
    }
}
