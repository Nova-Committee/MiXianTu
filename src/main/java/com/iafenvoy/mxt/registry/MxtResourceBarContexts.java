package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.*;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtResourceBarContexts {
    public static final DeferredRegister<ResourceBarContext> REGISTRY = DeferredRegister.create(MxtRegistries.RESOURCE_BAR_CONTEXT, MiXianTu.MOD_ID);

    public static final DeferredHolder<ResourceBarContext, SelfHudContext> SELF_HUD = REGISTRY.register("self_hud", () -> SelfHudContext.INSTANCE);
    public static final DeferredHolder<ResourceBarContext, TargetOverlayContext> TARGET_OVERLAY = REGISTRY.register("target_overlay", () -> TargetOverlayContext.INSTANCE);
    public static final DeferredHolder<ResourceBarContext, BossOverlayContext> BOSS_OVERLAY = REGISTRY.register("boss_overlay", () -> BossOverlayContext.INSTANCE);
    public static final DeferredHolder<ResourceBarContext, EnvironmentConcentrationContext> ENVIRONMENT_CONCENTRATION = REGISTRY.register("environment_concentration", () -> EnvironmentConcentrationContext.INSTANCE);
    public static final DeferredHolder<ResourceBarContext, ActualConcentrationContext> ACTUAL_CONCENTRATION = REGISTRY.register("actual_concentration", () -> ActualConcentrationContext.INSTANCE);
}
