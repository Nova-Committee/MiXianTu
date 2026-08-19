package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import net.objecthunter.exp4j.function.Function;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Built-in exp4j functions. Other code may register additional functions in this intrinsic registry.
 */
public final class MxtFormulaFunctions {
    public static final DeferredRegister<Function> REGISTRY = DeferredRegister.create(MxtRegistries.FORMULA_FUNCTION, MiXianTu.MOD_ID);
    public static final DeferredHolder<Function, Function> ROUND = REGISTRY.register("round", () -> new Function("round", 1) {
        @Override
        public double apply(double... arguments) {
            return Math.round(arguments[0]);
        }
    });
    public static final DeferredHolder<Function, Function> CLAMP = REGISTRY.register("clamp", () -> new Function("clamp", 3) {
        @Override
        public double apply(double... arguments) {
            return Math.max(arguments[1], Math.min(arguments[0], arguments[2]));
        }
    });
    public static final DeferredHolder<Function, Function> MIN = REGISTRY.register("min", () -> new Function("min", 2) {
        @Override
        public double apply(double... arguments) {
            return Math.min(arguments[0], arguments[1]);
        }
    });
    public static final DeferredHolder<Function, Function> MAX = REGISTRY.register("max", () -> new Function("max", 2) {
        @Override
        public double apply(double... arguments) {
            return Math.max(arguments[0], arguments[1]);
        }
    });

    private MxtFormulaFunctions() {
    }
}
