package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.trigger.MxtTriggerSignals;
import com.iafenvoy.saturn.data.trigger.Trigger;
import com.iafenvoy.saturn.data.trigger.builtin.BuiltinTrigger;
import com.iafenvoy.saturn.registry.SaturnRegistries;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The trigger matchers only this mod names. The generic signals and the ported vanilla criteria live in Saturn now;
 * a pack that still writes {@code mxt:kill} reaches Saturn's entry through the registered alias.
 */
@SuppressWarnings("unused")
public final class MxtTriggers {
    public static final DeferredRegister<MapCodec<? extends Trigger>> REGISTRY = DeferredRegister.create(SaturnRegistries.TRIGGER_TYPE, MiXianTu.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<BuiltinTrigger>> BREAKTHROUGH = register(MxtTriggerSignals.BREAKTHROUGH);
    public static final DeferredHolder<MapCodec<? extends Trigger>, MapCodec<BuiltinTrigger>> PROGRESSION_LEVEL = register(MxtTriggerSignals.PROGRESSION_LEVEL);

    private static DeferredHolder<MapCodec<? extends Trigger>, MapCodec<BuiltinTrigger>> register(Identifier signal) {
        return REGISTRY.register(signal.getPath(), () -> MapCodec.unit(() -> new BuiltinTrigger(signal)));
    }
}
