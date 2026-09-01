package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.ItemCost;
import com.iafenvoy.mxt.data.cost.ResourceCost;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtCosts {
    public static final DeferredRegister<MapCodec<? extends Cost>> REGISTRY = DeferredRegister.create(MxtRegistries.COST_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends Cost>, MapCodec<ResourceCost>> RESOURCE = REGISTRY.register("resource", () -> ResourceCost.CODEC);
    public static final DeferredHolder<MapCodec<? extends Cost>, MapCodec<ItemCost>> ITEM = REGISTRY.register("item", () -> ItemCost.CODEC);
}
