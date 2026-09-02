package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.trigger.Trigger;
import com.iafenvoy.mxt.data.trigger.Trigger.Builtin;
import com.iafenvoy.mxt.data.trigger.TriggerSignals;
import com.mojang.serialization.MapCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Built-in trigger matchers. Third-party modules can register additional
 * codecs into the same extensible registry.
 */
@SuppressWarnings("unused")
public final class MxtTriggers {
    public static final DeferredRegister<MapCodec<? extends Trigger>> REGISTRY =
            DeferredRegister.create(MxtRegistries.TRIGGER_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> TICK = register("tick");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> ATTACK = register("attack");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> HURT = register("hurt");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> KILL = register("kill");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> BLOCK_BREAK = register("block_break");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> BLOCK_USE = register("block_use");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> ITEM_USE = register("item_use");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> EQUIP = register("equip");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> DEATH = register("death");
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> BREAKTHROUGH = register("breakthrough");

    private static DeferredHolder<MapCodec<? extends Trigger>, MapCodec<Builtin>> register(String signal) {
        return REGISTRY.register(signal, () -> MapCodec.unit(new Builtin(
                TriggerSignals.id(signal))));
    }
}
