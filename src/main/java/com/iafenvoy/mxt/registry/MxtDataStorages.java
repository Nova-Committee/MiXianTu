package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.saturn.data.storage.DataStorage;
import com.iafenvoy.saturn.data.storage.runtime.AuraPulse;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in kinds: an ability type lists the ones its host keeps, and the kind's class is then the slot its
 * value lives in. The runtime's own kinds are registered here too, so their values save and sync like any other,
 * but no content declares any of them.
 */
@SuppressWarnings("unused")
public final class MxtDataStorages {
    public static final DeferredRegister<MapCodec<? extends DataStorage>> REGISTRY = DeferredRegister.create(SaturnRegistries.DATA_STORAGE_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<AuraPulse>> AURA_PULSE = REGISTRY.register("aura_pulse", () -> AuraPulse.CODEC);

    private MxtDataStorages() {
    }
}