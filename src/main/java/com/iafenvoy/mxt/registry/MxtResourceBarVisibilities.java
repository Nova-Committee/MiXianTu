package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.iafenvoy.mxt.data.resourcebar.builtin.visibility.AlwaysVisibility;
import com.iafenvoy.mxt.data.resourcebar.builtin.visibility.AndVisibility;
import com.iafenvoy.mxt.data.resourcebar.builtin.visibility.NonFullVisibility;
import com.iafenvoy.mxt.data.resourcebar.builtin.visibility.NonZeroVisibility;
import com.iafenvoy.mxt.data.resourcebar.builtin.visibility.NotVisibility;
import com.iafenvoy.mxt.data.resourcebar.builtin.visibility.OrVisibility;
import com.iafenvoy.mxt.data.resourcebar.builtin.visibility.RangeVisibility;
import com.iafenvoy.mxt.data.resourcebar.builtin.visibility.RecentlyChangedVisibility;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MxtResourceBarVisibilities {
    public static final DeferredRegister<MapCodec<? extends ResourceBarVisibility>> REGISTRY = DeferredRegister.create(MxtRegistries.RESOURCE_BAR_VISIBILITY_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends ResourceBarVisibility>, MapCodec<AlwaysVisibility>> ALWAYS = REGISTRY.register("always", () -> AlwaysVisibility.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarVisibility>, MapCodec<NonFullVisibility>> NON_FULL = REGISTRY.register("non_full", () -> NonFullVisibility.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarVisibility>, MapCodec<NonZeroVisibility>> NON_ZERO = REGISTRY.register("non_zero", () -> NonZeroVisibility.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarVisibility>, MapCodec<RecentlyChangedVisibility>> RECENTLY_CHANGED = REGISTRY.register("recently_changed", () -> RecentlyChangedVisibility.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarVisibility>, MapCodec<RangeVisibility>> RESOURCE_RANGE = REGISTRY.register("resource_range", () -> RangeVisibility.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarVisibility>, MapCodec<AndVisibility>> AND = REGISTRY.register("and", () -> AndVisibility.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarVisibility>, MapCodec<OrVisibility>> OR = REGISTRY.register("or", () -> OrVisibility.CODEC);
    public static final DeferredHolder<MapCodec<? extends ResourceBarVisibility>, MapCodec<NotVisibility>> NOT = REGISTRY.register("not", () -> NotVisibility.CODEC);

    private MxtResourceBarVisibilities() {
    }
}
