package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.AbilityType.Active;
import com.iafenvoy.mxt.data.ability.AbilityType.Aura;
import com.iafenvoy.mxt.data.ability.AbilityType.Channelled;
import com.iafenvoy.mxt.data.ability.AbilityType.Composite;
import com.iafenvoy.mxt.data.ability.AbilityType.Empty;
import com.iafenvoy.mxt.data.ability.AbilityType.Modifier;
import com.iafenvoy.mxt.data.ability.AbilityType.Triggered;
import com.iafenvoy.mxt.data.ability.AbilityType.Word;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Built-in ability lifecycle algorithms.
 */
public final class MxtAbilityTypes {
    public static final DeferredRegister<MapCodec<? extends AbilityType>> REGISTRY = DeferredRegister.create(MxtTypeRegistries.ABILITY_TYPE, MiXianTu.MOD_ID);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<Active>> ACTIVE = REGISTRY.register("active", () -> Active.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<Triggered>> TRIGGERED = REGISTRY.register("triggered", () -> Triggered.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<Modifier>> MODIFIER = REGISTRY.register("modifier", () -> Modifier.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<Aura>> AURA = REGISTRY.register("aura", () -> Aura.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<Channelled>> CHANNELLED = REGISTRY.register("channelled", () -> Channelled.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<Composite>> COMPOSITE = REGISTRY.register("composite", () -> Composite.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<Word>> WORD = REGISTRY.register("word", () -> Word.CODEC);
    public static final DeferredHolder<MapCodec<? extends AbilityType>, MapCodec<Empty>> EMPTY = REGISTRY.register("empty", () -> Empty.CODEC);

    private MxtAbilityTypes() {
    }
}
