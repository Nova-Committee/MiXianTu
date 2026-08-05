package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Binomial;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.iafenvoy.mxt.util.formula.NumberProvider.ContextVariable;
import com.iafenvoy.mxt.util.formula.NumberProvider.Expression;
import com.iafenvoy.mxt.util.formula.NumberProvider.Sum;
import com.iafenvoy.mxt.util.formula.NumberProvider.Uniform;
import com.iafenvoy.mxt.util.formula.NumberProvider.WeightedList;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Built-in numeric algorithms selected by an inline {@code type} field.
 * Java Edition 26.3 adds a vanilla writable registry named {@code number_provider};
 * this remains an intrinsic type registry until the project migrates to that version.
 */
public final class MxtNumberProviders {
    public static final DeferredRegister<MapCodec<? extends NumberProvider>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.NUMBER_PROVIDER_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends NumberProvider>, MapCodec<Constant>> CONSTANT = REGISTRY.register("constant", () -> Constant.MAP_CODEC);
    public static final DeferredHolder<MapCodec<? extends NumberProvider>, MapCodec<Expression>> EXPRESSION = REGISTRY.register("expression", () -> Expression.MAP_CODEC);
    public static final DeferredHolder<MapCodec<? extends NumberProvider>, MapCodec<ContextVariable>> CONTEXT_VARIABLE = REGISTRY.register("context_variable", () -> ContextVariable.MAP_CODEC);
    public static final DeferredHolder<MapCodec<? extends NumberProvider>, MapCodec<Sum>> SUM = REGISTRY.register("sum", () -> Sum.MAP_CODEC);
    public static final DeferredHolder<MapCodec<? extends NumberProvider>, MapCodec<Uniform>> UNIFORM = REGISTRY.register("uniform", () -> Uniform.MAP_CODEC);
    public static final DeferredHolder<MapCodec<? extends NumberProvider>, MapCodec<Binomial>> BINOMIAL = REGISTRY.register("binomial", () -> Binomial.MAP_CODEC);
    public static final DeferredHolder<MapCodec<? extends NumberProvider>, MapCodec<WeightedList>> WEIGHTED_LIST = REGISTRY.register("weighted_list", () -> WeightedList.MAP_CODEC);

    private MxtNumberProviders() {
    }
}
