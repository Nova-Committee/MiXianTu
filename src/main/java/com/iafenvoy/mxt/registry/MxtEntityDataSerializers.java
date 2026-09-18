package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.lightning.ColoredLightningBolt;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;

/**
 * Entity data serializers this mod adds on top of the vanilla set.
 *
 * <p>A modded serializer has to go through {@link NeoForgeRegistries#ENTITY_DATA_SERIALIZERS}: the vanilla
 * registry refuses outside callers, because client and server would otherwise be free to disagree about the
 * wire ids. The instance is built here and the deferred entry registers that very instance, so the accessor an
 * entity defines and the registry entry are the same object rather than two equal ones.</p>
 */
public final class MxtEntityDataSerializers {
    public static final DeferredRegister<EntityDataSerializer<?>> REGISTRY = DeferredRegister.create(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, MiXianTu.MOD_ID);

    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<List<Integer>>> COLOR_LIST = REGISTRY.register("color_list", () -> EntityDataSerializer.forValueType(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(ColoredLightningBolt.MAX_PALETTE))));
}
