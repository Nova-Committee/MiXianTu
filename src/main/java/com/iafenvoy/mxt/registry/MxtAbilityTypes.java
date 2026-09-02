package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.type.*;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtAbilityTypes {
    public static final DeferredRegister<MapCodec<? extends AbilityType>> REGISTRY = DeferredRegister.create(MxtRegistries.ABILITY_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<ActiveAbilityType>> ACTIVE = REGISTRY.register("active", () -> ActiveAbilityType.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<TriggeredAbilityType>> TRIGGERED = REGISTRY.register("triggered", () -> TriggeredAbilityType.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<ModifierAbilityType>> MODIFIER = REGISTRY.register("modifier", () -> ModifierAbilityType.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<AuraAbilityType>> AURA = REGISTRY.register("aura", () -> AuraAbilityType.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<ChannelledAbilityType>> CHANNELLED = REGISTRY.register("channelled", () -> ChannelledAbilityType.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<CompositeAbilityType>> COMPOSITE = REGISTRY.register("composite", () -> CompositeAbilityType.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<WordAbilityType>> WORD = REGISTRY.register("word", () -> WordAbilityType.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<EmptyAbilityType>> EMPTY = REGISTRY.register("empty", () -> EmptyAbilityType.CODEC);
}
