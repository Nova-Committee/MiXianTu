package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.curse.CurseType;
import com.iafenvoy.mxt.data.curse.CurseType.Empty;
import com.iafenvoy.mxt.data.curse.CurseType.Permanent;
import com.iafenvoy.mxt.data.curse.CurseType.Timed;
import com.iafenvoy.mxt.data.curse.CurseType.Triggered;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Built-in curse lifecycle algorithms selectable from datapacks.
 */
public final class MxtCurseTypes {
    public static final DeferredRegister<MapCodec<? extends CurseType>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.CURSE_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends CurseType>, MapCodec<Timed>> TIMED = REGISTRY.register("timed", () -> Timed.CODEC);
    public static final DeferredHolder<MapCodec<? extends CurseType>, MapCodec<Permanent>> PERMANENT = REGISTRY.register("permanent", () -> Permanent.CODEC);
    public static final DeferredHolder<MapCodec<? extends CurseType>, MapCodec<Triggered>> TRIGGERED = REGISTRY.register("triggered", () -> Triggered.CODEC);
    public static final DeferredHolder<MapCodec<? extends CurseType>, MapCodec<Empty>> EMPTY = REGISTRY.register("empty", () -> Empty.CODEC);

    private MxtCurseTypes() {
    }
}
