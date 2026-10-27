package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.ActualConcentrationContext;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.EnvironmentConcentrationContext;
import com.iafenvoy.saturn.data.resourcebar.ResourceBarContext;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtResourceBarContexts {
    public static final DeferredRegister<ResourceBarContext> REGISTRY = DeferredRegister.create(SaturnRegistries.RESOURCE_BAR_CONTEXT, MiXianTu.MOD_ID);

    public static final DeferredHolder<ResourceBarContext, EnvironmentConcentrationContext> ENVIRONMENT_CONCENTRATION = REGISTRY.register("environment_concentration", () -> EnvironmentConcentrationContext.INSTANCE);
    public static final DeferredHolder<ResourceBarContext, ActualConcentrationContext> ACTUAL_CONCENTRATION = REGISTRY.register("actual_concentration", () -> ActualConcentrationContext.INSTANCE);
}
