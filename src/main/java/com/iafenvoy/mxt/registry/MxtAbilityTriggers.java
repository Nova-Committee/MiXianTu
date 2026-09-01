package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.AbilityTrigger;
import com.iafenvoy.mxt.data.ability.AbilityTrigger.Builtin;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@SuppressWarnings("unused")
public final class MxtAbilityTriggers {
    public static final DeferredRegister<MapCodec<? extends AbilityTrigger>> REGISTRY = DeferredRegister.create(MxtRegistries.ABILITY_TRIGGER_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends AbilityTrigger>, MapCodec<Builtin>> USE = register("use");
    public static final DeferredHolder<MapCodec<? extends AbilityTrigger>, MapCodec<Builtin>> TICK = register("tick");
    public static final DeferredHolder<MapCodec<? extends AbilityTrigger>, MapCodec<Builtin>> ATTACK = register("attack");
    public static final DeferredHolder<MapCodec<? extends AbilityTrigger>, MapCodec<Builtin>> HURT = register("hurt");
    public static final DeferredHolder<MapCodec<? extends AbilityTrigger>, MapCodec<Builtin>> KILL = register("kill");
    public static final DeferredHolder<MapCodec<? extends AbilityTrigger>, MapCodec<Builtin>> BLOCK_BREAK = register("block_break");
    public static final DeferredHolder<MapCodec<? extends AbilityTrigger>, MapCodec<Builtin>> BLOCK_USE = register("block_use");
    public static final DeferredHolder<MapCodec<? extends AbilityTrigger>, MapCodec<Builtin>> ITEM_USE = register("item_use");
    public static final DeferredHolder<MapCodec<? extends AbilityTrigger>, MapCodec<Builtin>> EQUIP = register("equip");
    public static final DeferredHolder<MapCodec<? extends AbilityTrigger>, MapCodec<Builtin>> DEATH = register("death");
    public static final DeferredHolder<MapCodec<? extends AbilityTrigger>, MapCodec<Builtin>> BREAKTHROUGH = register("breakthrough");

    private static DeferredHolder<MapCodec<? extends AbilityTrigger>, MapCodec<Builtin>> register(String event) {
        return REGISTRY.register(event, () -> MapCodec.unit(new Builtin(event)));
    }
}
