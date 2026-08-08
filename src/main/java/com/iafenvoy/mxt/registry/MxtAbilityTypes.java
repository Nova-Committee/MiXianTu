package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.type.ActiveAbilityType;
import com.iafenvoy.mxt.data.ability.type.AuraAbilityType;
import com.iafenvoy.mxt.data.ability.type.ChannelledAbilityType;
import com.iafenvoy.mxt.data.ability.type.CompositeAbilityType;
import com.iafenvoy.mxt.data.ability.type.EmptyAbilityType;
import com.iafenvoy.mxt.data.ability.type.ModifierAbilityType;
import com.iafenvoy.mxt.data.ability.type.TriggeredAbilityType;
import com.iafenvoy.mxt.data.ability.type.WordAbilityType;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Built-in ability lifecycle algorithms.
 */
public final class MxtAbilityTypes {
    public static final DeferredRegister<MapCodec<? extends AbilityType>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.ABILITY_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<ActiveAbilityType>> ACTIVE = REGISTRY.register("active", () -> ActiveAbilityType.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<TriggeredAbilityType>> TRIGGERED = REGISTRY.register("triggered", () -> TriggeredAbilityType.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<ModifierAbilityType>> MODIFIER = REGISTRY.register("modifier", () -> ModifierAbilityType.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<AuraAbilityType>> AURA = REGISTRY.register("aura", () -> AuraAbilityType.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<ChannelledAbilityType>> CHANNELLED = REGISTRY.register("channelled", () -> ChannelledAbilityType.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<CompositeAbilityType>> COMPOSITE = REGISTRY.register("composite", () -> CompositeAbilityType.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<WordAbilityType>> WORD = REGISTRY.register("word", () -> WordAbilityType.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<EmptyAbilityType>> EMPTY = REGISTRY.register("empty", () -> EmptyAbilityType.CODEC);

    private MxtAbilityTypes() {
    }
}
