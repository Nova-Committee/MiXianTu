package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.screen.profiling.ProfilerOpenScreenData;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.iafenvoy.saturn.screen.opener.OpenScreenData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtOpenScreenData {
    public static final DeferredRegister<StreamCodec<RegistryFriendlyByteBuf, ? extends OpenScreenData>> REGISTRY = DeferredRegister.create(SaturnRegistries.OPEN_SCREEN_DATA, MiXianTu.MOD_ID);

    public static final DeferredHolder<StreamCodec<RegistryFriendlyByteBuf, ? extends OpenScreenData>, StreamCodec<RegistryFriendlyByteBuf, ProfilerOpenScreenData>> PROFILER = REGISTRY.register("profiler", () -> ProfilerOpenScreenData.STREAM_CODEC);
}
