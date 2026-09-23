package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.data.storage.builtin.*;
import com.iafenvoy.mxt.data.storage.runtime.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The built-in kinds: a data pack selects one by writing its id in an entry of a host's {@code components}
 * list, and the kind's class is then the slot its value lives in. The runtime's own kinds are registered here
 * too, so their values save and sync like any other, but no content declares any of them.
 */
@SuppressWarnings("unused")
public final class MxtDataStorages {
    public static final DeferredRegister<MapCodec<? extends DataStorage>> REGISTRY = DeferredRegister.create(MxtRegistries.DATA_STORAGE_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<EmptyDataStorage>> EMPTY = REGISTRY.register("empty", () -> EmptyDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<CooldownDataStorage>> COOLDOWN = REGISTRY.register("cooldown", () -> CooldownDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<ChargesDataStorage>> CHARGES = REGISTRY.register("charges", () -> ChargesDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<ToggleDataStorage>> TOGGLE = REGISTRY.register("toggle", () -> ToggleDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<TimerDataStorage>> TIMER = REGISTRY.register("timer", () -> TimerDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<ResourceDataStorage>> RESOURCE = REGISTRY.register("resource", () -> ResourceDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<TargetLockDataStorage>> TARGET_LOCK = REGISTRY.register("target_lock", () -> TargetLockDataStorage.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<CastDeadline>> CAST_DEADLINE = REGISTRY.register("cast_deadline", () -> CastDeadline.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<ChannelPulse>> CHANNEL_PULSE = REGISTRY.register("channel_pulse", () -> ChannelPulse.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<AuraPulse>> AURA_PULSE = REGISTRY.register("aura_pulse", () -> AuraPulse.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<EntryBegan>> ENTRY_BEGAN = REGISTRY.register("entry_began", () -> EntryBegan.CODEC);
    public static final DeferredHolder<MapCodec<? extends DataStorage>, MapCodec<IdleCountdown>> IDLE_COUNTDOWN = REGISTRY.register("idle_countdown", () -> IdleCountdown.CODEC);

    private MxtDataStorages() {
    }
}