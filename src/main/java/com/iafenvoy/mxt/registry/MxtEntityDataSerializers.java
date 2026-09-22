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
 * Entity data serializers this mod adds. They must go through {@link NeoForgeRegistries#ENTITY_DATA_SERIALIZERS}
 * (the vanilla registry refuses outside callers, which would let client and server disagree about the wire ids),
 * and the one instance built here is what an entity's accessor and the registry entry both use.
 */
public final class MxtEntityDataSerializers {
    public static final DeferredRegister<EntityDataSerializer<?>> REGISTRY = DeferredRegister.create(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, MiXianTu.MOD_ID);

    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<List<Integer>>> COLOR_LIST = REGISTRY.register("color_list", () -> EntityDataSerializer.forValueType(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(ColoredLightningBolt.MAX_PALETTE))));
}
