package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.badge.Badge;
import com.iafenvoy.mxt.data.badge.builtin.CraftingRecipeBadge;
import com.iafenvoy.mxt.data.badge.builtin.EmptyBadge;
import com.iafenvoy.mxt.data.badge.builtin.KeybindBadge;
import com.iafenvoy.mxt.data.badge.builtin.SpriteBadge;
import com.iafenvoy.mxt.data.badge.builtin.TooltipBadge;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtBadges {
    public static final DeferredRegister<MapCodec<? extends Badge>> REGISTRY = DeferredRegister.create(MxtRegistries.BADGE_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends Badge>, MapCodec<EmptyBadge>> EMPTY = REGISTRY.register("empty", () -> EmptyBadge.CODEC);
    public static final DeferredHolder<MapCodec<? extends Badge>, MapCodec<CraftingRecipeBadge>> CRAFTING_RECIPE = REGISTRY.register("crafting_recipe", () -> CraftingRecipeBadge.CODEC);
    public static final DeferredHolder<MapCodec<? extends Badge>, MapCodec<KeybindBadge>> KEYBIND = REGISTRY.register("keybind", () -> KeybindBadge.CODEC);
    public static final DeferredHolder<MapCodec<? extends Badge>, MapCodec<SpriteBadge>> SPRITE = REGISTRY.register("sprite", () -> SpriteBadge.CODEC);
    public static final DeferredHolder<MapCodec<? extends Badge>, MapCodec<TooltipBadge>> TOOLTIP = REGISTRY.register("tooltip", () -> TooltipBadge.CODEC);
}
