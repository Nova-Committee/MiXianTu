package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.render.MountRender;
import com.iafenvoy.mxt.data.ability.render.builtin.GeckoLibMountRender;
import com.iafenvoy.mxt.data.ability.render.builtin.ItemMountRender;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtMountRenders {
    public static final DeferredRegister<MapCodec<? extends MountRender>> REGISTRY = DeferredRegister.create(MxtRegistries.MOUNT_RENDER_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends MountRender>, MapCodec<ItemMountRender>> ITEM = REGISTRY.register("item", () -> ItemMountRender.CODEC);
    // Registered whether or not GeckoLib is installed: the definition is plain data, and the client decides what to
    // do when it has no renderer for it (see render/mount/MountRenderers).
    public static final DeferredHolder<MapCodec<? extends MountRender>, MapCodec<GeckoLibMountRender>> GECKOLIB = REGISTRY.register("geckolib", () -> GeckoLibMountRender.CODEC);
}
