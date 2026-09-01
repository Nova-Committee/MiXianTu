package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.formula.SimpleFunction;
import net.minecraft.util.Mth;
import net.objecthunter.exp4j.function.Function;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtFormulaFunctions {
    public static final DeferredRegister<Function> REGISTRY = DeferredRegister.create(MxtRegistries.FORMULA_FUNCTION, MiXianTu.MOD_ID);

    public static final DeferredHolder<Function, Function> ROUND = REGISTRY.register("round", () -> new SimpleFunction("round", 1, args -> Math.round(args[0])));
    public static final DeferredHolder<Function, Function> CLAMP = REGISTRY.register("clamp", () -> new SimpleFunction("clamp", 3, args -> Mth.clamp(args[0], args[1], args[2])));
    public static final DeferredHolder<Function, Function> MIN = REGISTRY.register("min", () -> new SimpleFunction("min", 2, args -> Math.min(args[0], args[1])));
    public static final DeferredHolder<Function, Function> MAX = REGISTRY.register("max", () -> new SimpleFunction("max", 2, args -> Math.max(args[0], args[1])));
}
