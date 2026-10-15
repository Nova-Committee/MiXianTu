package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.talisman.Talisman;
import net.minecraft.core.Holder;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.List;
import java.util.Optional;

/**
 * Entity data serializers this mod adds. They must go through {@link NeoForgeRegistries#ENTITY_DATA_SERIALIZERS}
 * (the vanilla registry refuses outside callers, which would let client and server disagree about the wire ids),
 * and the one instance built here is what an entity's accessor and the registry entry both use.
 */
public final class MxtEntityDataSerializers {
    public static final DeferredRegister<EntityDataSerializer<?>> REGISTRY = DeferredRegister.create(NeoForgeRegistries.ENTITY_DATA_SERIALIZERS, MiXianTu.MOD_ID);

    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<List<Integer>>> COLOR_LIST = REGISTRY.register("color_list", () -> EntityDataSerializer.forValueType(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list())));
    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<Optional<Holder<Aura>>>> AURA = REGISTRY.register("aura", () -> EntityDataSerializer.forValueType(ByteBufCodecs.optional(ByteBufCodecs.holderRegistry(MxtResourceKeys.AURA))));
    public static final DeferredHolder<EntityDataSerializer<?>, EntityDataSerializer<Optional<Holder<Talisman>>>> TALISMAN = REGISTRY.register("talisman", () -> EntityDataSerializer.forValueType(ByteBufCodecs.optional(ByteBufCodecs.holderRegistry(MxtResourceKeys.TALISMAN))));
}
