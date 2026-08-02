package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.*;
import com.iafenvoy.mxt.runtime.forging.ForgingWorldData;
import com.iafenvoy.mxt.runtime.formation.FormationWorldData;
import com.iafenvoy.mxt.runtime.world.AuraWorldData;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/**
 * Attachment registrations. Callers access these through entity/chunk data APIs, never through client supplied values.
 */
public final class MxtAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTRY = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MiXianTu.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SpiritData>> SPIRIT_DATA = entity("spirit_data", SpiritData::new, SpiritData.MAP_CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ResourceHolderData>> RESOURCE_HOLDER = entity("resource_holder", ResourceHolderData::new, ResourceHolderData.MAP_CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AbilityHolderData>> ABILITY_HOLDER = entity("ability_holder", AbilityHolderData::new, AbilityHolderData.MAP_CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CurseHolderData>> CURSE_HOLDER = entity("curse_holder", CurseHolderData::new, CurseHolderData.MAP_CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TribulationData>> TRIBULATION = entity("tribulation", TribulationData::new, TribulationData.MAP_CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ContractData>> CONTRACT = entity("contract", ContractData::new, ContractData.MAP_CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AuraChunkData>> AURA_CHUNK = REGISTRY.register("aura_chunk", () -> AttachmentType.builder(AuraChunkData::new).serialize(AuraChunkData.MAP_CODEC).sync(ByteBufCodecs.fromCodecWithRegistries(AuraChunkData.CODEC)).build());
    /**
     * Persistent artificial aura areas, owned by one ServerLevel.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AuraWorldData>> AURA_WORLD = REGISTRY.register("aura_world", () -> AttachmentType.builder(AuraWorldData::new).serialize(AuraWorldData.MAP_CODEC).build());
    /**
     * Attached to a ServerLevel by formation world adapters; never copied onto entities.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FormationWorldData>> FORMATION_WORLD = REGISTRY.register("formation_world", () -> AttachmentType.builder(FormationWorldData::new).serialize(FormationWorldData.MAP_CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ForgingWorldData>> FORGING_WORLD = REGISTRY.register("forging_world", () -> AttachmentType.builder(ForgingWorldData::new).serialize(ForgingWorldData.MAP_CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ForgingSessionData>> FORGING_SESSION = entityWithoutDeathCopy("forging_session", ForgingSessionData::new, ForgingSessionData.MAP_CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FlightData>> FLIGHT = entityWithoutDeathCopy("flight", FlightData::new, FlightData.MAP_CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SectData>> SECT = entity("sect", SectData::new, SectData.MAP_CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SectTerritoryData>> SECT_TERRITORY = REGISTRY.register("sect_territory", () -> AttachmentType.builder(SectTerritoryData::new).serialize(SectTerritoryData.MAP_CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RealmInstanceData>> REALM_INSTANCE = REGISTRY.register("realm_instance", () -> AttachmentType.builder(RealmInstanceData::new).serialize(RealmInstanceData.MAP_CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RealmTravelData>> REALM_TRAVEL = entity("realm_travel", RealmTravelData::new, RealmTravelData.MAP_CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SoulData>> SOUL = entity("soul", SoulData::new, SoulData.MAP_CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CreatureSpiritData>> CREATURE_SPIRIT = entity("creature_spirit", CreatureSpiritData::new, CreatureSpiritData.MAP_CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PillToxicityData>> PILL_TOXICITY = entity("pill_toxicity", PillToxicityData::new, PillToxicityData.MAP_CODEC);

    private MxtAttachments() {
    }

    private static <T> DeferredHolder<AttachmentType<?>, AttachmentType<T>> entity(String name, Supplier<T> factory, MapCodec<T> codec) {
        StreamCodec<RegistryFriendlyByteBuf, T> streamCodec = ByteBufCodecs.fromCodecWithRegistries(codec.codec());
        return REGISTRY.register(name, () -> AttachmentType.builder(factory).serialize(codec).sync(streamCodec).copyOnDeath().build());
    }

    /**
     * In-progress inventories must not duplicate when a player respawns.
     */
    private static <T> DeferredHolder<AttachmentType<?>, AttachmentType<T>> entityWithoutDeathCopy(String name, Supplier<T> factory, MapCodec<T> codec) {
        StreamCodec<RegistryFriendlyByteBuf, T> streamCodec = ByteBufCodecs.fromCodecWithRegistries(codec.codec());
        return REGISTRY.register(name, () -> AttachmentType.builder(factory).serialize(codec).sync(streamCodec).build());
    }
}
