package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarVisibilities.Always;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarVisibilities.And;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarVisibilities.NonFull;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarVisibilities.Not;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarVisibilities.Or;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarVisibilities.Range;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarVisibilities.RecentlyChanged;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtResourceBarVisibilities {
    public static final DeferredRegister<MapCodec<? extends ResourceBarVisibility>> REGISTRY = DeferredRegister.create(MxtRegistries.RESOURCE_BAR_VISIBILITY_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends ResourceBarVisibility>, MapCodec<Always>> ALWAYS = REGISTRY.register("always", () -> Always.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarVisibility>, MapCodec<NonFull>> NON_FULL = REGISTRY.register("non_full", () -> NonFull.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarVisibility>, MapCodec<RecentlyChanged>> RECENTLY_CHANGED = REGISTRY.register("recently_changed", () -> RecentlyChanged.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarVisibility>, MapCodec<Range>> RESOURCE_RANGE = REGISTRY.register("resource_range", () -> Range.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarVisibility>, MapCodec<And>> AND = REGISTRY.register("and", () -> And.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarVisibility>, MapCodec<Or>> OR = REGISTRY.register("or", () -> Or.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarVisibility>, MapCodec<Not>> NOT = REGISTRY.register("not", () -> Not.CODEC);

    private MxtResourceBarVisibilities() {
    }
}
