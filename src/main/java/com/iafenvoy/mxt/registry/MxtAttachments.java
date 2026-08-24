package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.*;
import com.iafenvoy.mxt.runtime.forging.ForgingWorldComponent;
import com.iafenvoy.mxt.runtime.formation.FormationWorldComponent;
import com.iafenvoy.mxt.runtime.world.AuraWorldComponent;
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
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SpiritComponent>> SPIRIT_DATA = entity("spirit_data", SpiritComponent::new, SpiritComponent.CODEC);
    /**
     * One item temporarily removed from inventory while a named consumer processes it.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FloatHoldingItemComponent>> FLOAT_HOLDING_ITEM = REGISTRY.register("float_holding_item", () -> AttachmentType.builder(FloatHoldingItemComponent::new).serialize(FloatHoldingItemComponent.CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ResourceHolderComponent>> RESOURCE_HOLDER = entity("resource_holder", ResourceHolderComponent::new, ResourceHolderComponent.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SpiritBurstCooldownComponent>> SPIRIT_BURST_COOLDOWNS = entity("spirit_burst_cooldowns", SpiritBurstCooldownComponent::new, SpiritBurstCooldownComponent.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AbilityHolderComponent>> ABILITY_HOLDER = entity("ability_holder", AbilityHolderComponent::new, AbilityHolderComponent.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CurseHolderComponent>> CURSE_HOLDER = entity("curse_holder", CurseHolderComponent::new, CurseHolderComponent.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TribulationComponent>> TRIBULATION = entity("tribulation", TribulationComponent::new, TribulationComponent.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ContractComponent>> CONTRACT = entity("contract", ContractComponent::new, ContractComponent.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AuraChunkComponent>> AURA_CHUNK = REGISTRY.register("aura_chunk", () -> AttachmentType.builder(AuraChunkComponent::new).serialize(AuraChunkComponent.CODEC).build());
    /**
     * Persistent artificial aura areas, owned by one ServerLevel.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AuraWorldComponent>> AURA_WORLD = REGISTRY.register("aura_world", () -> AttachmentType.builder(AuraWorldComponent::new).serialize(AuraWorldComponent.MAP_CODEC).build());
    /**
     * Attached to a ServerLevel by formation world adapters; never copied onto entities.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FormationWorldComponent>> FORMATION_WORLD = REGISTRY.register("formation_world", () -> AttachmentType.builder(FormationWorldComponent::new).serialize(FormationWorldComponent.MAP_CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ForgingWorldComponent>> FORGING_WORLD = REGISTRY.register("forging_world", () -> AttachmentType.builder(ForgingWorldComponent::new).serialize(ForgingWorldComponent.MAP_CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ForgingSessionComponent>> FORGING_SESSION = entityWithoutDeathCopy("forging_session", ForgingSessionComponent::new, ForgingSessionComponent.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FlightComponent>> FLIGHT = entityWithoutDeathCopy("flight", FlightComponent::new, FlightComponent.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SectComponent>> SECT = entity("sect", SectComponent::new, SectComponent.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SectTerritoryComponent>> SECT_TERRITORY = REGISTRY.register("sect_territory", () -> AttachmentType.builder(SectTerritoryComponent::new).serialize(SectTerritoryComponent.CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RealmInstanceComponent>> REALM_INSTANCE = REGISTRY.register("realm_instance", () -> AttachmentType.builder(RealmInstanceComponent::new).serialize(RealmInstanceComponent.CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RealmTravelComponent>> REALM_TRAVEL = entity("realm_travel", RealmTravelComponent::new, RealmTravelComponent.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SoulComponent>> SOUL = entity("soul", SoulComponent::new, SoulComponent.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CreatureSpiritComponent>> CREATURE_SPIRIT = entity("creature_spirit", CreatureSpiritComponent::new, CreatureSpiritComponent.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PillToxicityComponent>> PILL_TOXICITY = entity("pill_toxicity", PillToxicityComponent::new, PillToxicityComponent.CODEC);

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
