package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.BossOverlayContext;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.SelfHudContext;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.TargetOverlayContext;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.SensedConcentrationContext;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtResourceBarContexts {
    public static final DeferredRegister<ResourceBarContext> REGISTRY =
            DeferredRegister.create(MxtRegistries.RESOURCE_BAR_CONTEXT, MiXianTu.MOD_ID);
    public static final DeferredHolder<ResourceBarContext, SelfHudContext> SELF_HUD =
            REGISTRY.register("self_hud", () -> SelfHudContext.INSTANCE);
    public static final DeferredHolder<ResourceBarContext, TargetOverlayContext> TARGET_OVERLAY =
            REGISTRY.register("target_overlay", () -> TargetOverlayContext.INSTANCE);
    public static final DeferredHolder<ResourceBarContext, BossOverlayContext> BOSS_OVERLAY =
            REGISTRY.register("boss_overlay", () -> BossOverlayContext.INSTANCE);
    public static final DeferredHolder<ResourceBarContext, SensedConcentrationContext> SENSED_CONCENTRATION =
            REGISTRY.register("sensed_concentration", () -> SensedConcentrationContext.INSTANCE);

    private MxtResourceBarContexts() {
    }
}
