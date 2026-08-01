package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.iafenvoy.mxt.util.formula.NumberProvider.ContextVariable;
import com.iafenvoy.mxt.util.formula.NumberProvider.Expression;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Built-in numeric algorithms exposed to datapacks through {@code type}.
 */
public final class MxtNumberProviders {
    public static final DeferredRegister<MapCodec<? extends NumberProvider>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.NUMBER_PROVIDER_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends NumberProvider>, MapCodec<Constant>> CONSTANT = REGISTRY.register("constant", () -> Constant.MAP_CODEC);
    public static final DeferredHolder<MapCodec<? extends NumberProvider>, MapCodec<Expression>> EXPRESSION = REGISTRY.register("expression", () -> Expression.MAP_CODEC);
    public static final DeferredHolder<MapCodec<? extends NumberProvider>, MapCodec<ContextVariable>> CONTEXT_VARIABLE = REGISTRY.register("context_variable", () -> ContextVariable.MAP_CODEC);

    private MxtNumberProviders() {
    }
}
