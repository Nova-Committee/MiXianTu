package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.curse.CurseType;
import com.iafenvoy.mxt.data.curse.builtin.EmptyCurseType;
import com.iafenvoy.mxt.data.curse.builtin.PermanentCurseType;
import com.iafenvoy.mxt.data.curse.builtin.TimedCurseType;
import com.iafenvoy.mxt.data.curse.builtin.TriggeredCurseType;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtCurseTypes {
    public static final DeferredRegister<MapCodec<? extends CurseType>> REGISTRY = DeferredRegister.create(MxtRegistries.CURSE_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends CurseType>, MapCodec<TimedCurseType>> TIMED = REGISTRY.register("timed", () -> TimedCurseType.CODEC);
    public static final DeferredHolder<MapCodec<? extends CurseType>, MapCodec<PermanentCurseType>> PERMANENT = REGISTRY.register("permanent", () -> PermanentCurseType.CODEC);
    public static final DeferredHolder<MapCodec<? extends CurseType>, MapCodec<TriggeredCurseType>> TRIGGERED = REGISTRY.register("triggered", () -> TriggeredCurseType.CODEC);
    public static final DeferredHolder<MapCodec<? extends CurseType>, MapCodec<EmptyCurseType>> EMPTY = REGISTRY.register("empty", () -> EmptyCurseType.CODEC);
}
