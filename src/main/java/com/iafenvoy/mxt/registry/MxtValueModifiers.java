package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.formula.ValueModifier;
import com.iafenvoy.mxt.util.formula.ValueModifier.Add;
import com.iafenvoy.mxt.util.formula.ValueModifier.Clamp;
import com.iafenvoy.mxt.util.formula.ValueModifier.MultiplyBase;
import com.iafenvoy.mxt.util.formula.ValueModifier.MultiplyTotal;
import com.iafenvoy.mxt.util.formula.ValueModifier.Set;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtValueModifiers {
    public static final DeferredRegister<MapCodec<? extends ValueModifier>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.VALUE_MODIFIER_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends ValueModifier>, MapCodec<Add>> ADD = REGISTRY.register("add", () -> Add.CODEC);
    public static final DeferredHolder<MapCodec<? extends ValueModifier>, MapCodec<MultiplyBase>> MULTIPLY_BASE = REGISTRY.register("multiply_base", () -> MultiplyBase.CODEC);
    public static final DeferredHolder<MapCodec<? extends ValueModifier>, MapCodec<MultiplyTotal>> MULTIPLY_TOTAL = REGISTRY.register("multiply_total", () -> MultiplyTotal.CODEC);
    public static final DeferredHolder<MapCodec<? extends ValueModifier>, MapCodec<Clamp>> CLAMP = REGISTRY.register("clamp", () -> Clamp.CODEC);
    public static final DeferredHolder<MapCodec<? extends ValueModifier>, MapCodec<Set>> SET = REGISTRY.register("set", () -> Set.CODEC);

    private MxtValueModifiers() {
    }
}
