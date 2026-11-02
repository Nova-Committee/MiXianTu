package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.information.getter.MxtInformationGetter;
import com.iafenvoy.saturn.data.information.getter.EntityValueGetter;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtEntityValueGetters {
    public static final DeferredRegister<MapCodec<? extends EntityValueGetter<?>>> REGISTRY = DeferredRegister.create(SaturnRegistries.ENTITY_VALUE_GETTER, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends EntityValueGetter<?>>, MapCodec<MxtInformationGetter>> INFORMATION = REGISTRY.register("information", () -> MxtInformationGetter.CODEC);

    private MxtEntityValueGetters() {
    }
}
