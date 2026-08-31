package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.*;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.iafenvoy.mxt.runtime.forging.ForgingWorldAttachment;
import com.iafenvoy.mxt.runtime.formation.FormationWorldAttachment;
import com.iafenvoy.mxt.runtime.world.AuraWorldAttachment;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Attachment registrations and the common dirty/synchronisation dispatcher.
 */
@EventBusSubscriber
public final class MxtAttachments {
    private static final List<Supplier<? extends AttachmentType<?>>> SYNCED_ENTITY_ATTACHMENTS = new ArrayList<>();
    public static final DeferredRegister<AttachmentType<?>> REGISTRY = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MiXianTu.MOD_ID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CultivationAttachment>> CULTIVATION = entity("cultivation", CultivationAttachment::new, CultivationAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SpiritIdentityAttachment>> SPIRIT_IDENTITY = entity("spirit_identity", SpiritIdentityAttachment::new, SpiritIdentityAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SpiritStatsAttachment>> SPIRIT_STATS = entity("spirit_stats", SpiritStatsAttachment::new, SpiritStatsAttachment.CODEC);
    /**
     * One item temporarily removed from inventory while a named consumer processes it.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FloatHoldingItemAttachment>> FLOAT_HOLDING_ITEM = entityWithoutDeathCopy("float_holding_item", FloatHoldingItemAttachment::new, FloatHoldingItemAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ResourceHolderAttachment>> RESOURCE_HOLDER = entity("resource_holder", ResourceHolderAttachment::new, ResourceHolderAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SpiritBurstCooldownAttachment>> SPIRIT_BURST_COOLDOWNS = entity("spirit_burst_cooldowns", SpiritBurstCooldownAttachment::new, SpiritBurstCooldownAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AbilityAttachment>> ABILITY_HOLDER = entity("ability_holder", AbilityAttachment::new, AbilityAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<HotbarLayoutAttachment>> HOTBAR_LAYOUT = entity("hotbar_layout", HotbarLayoutAttachment::new, HotbarLayoutAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CurseHolderAttachment>> CURSE_HOLDER = entity("curse_holder", CurseHolderAttachment::new, CurseHolderAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TribulationAttachment>> TRIBULATION = entity("tribulation", TribulationAttachment::new, TribulationAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ContractAttachment>> CONTRACT = entity("contract", ContractAttachment::new, ContractAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AuraChunkAttachment>> AURA_CHUNK = REGISTRY.register("aura_chunk", () -> AttachmentType.builder(AuraChunkAttachment::new).serialize(AuraChunkAttachment.CODEC).build());
    /**
     * Persistent artificial aura areas, owned by one ServerLevel.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AuraWorldAttachment>> AURA_WORLD = REGISTRY.register("aura_world", () -> AttachmentType.builder(AuraWorldAttachment::new).serialize(AuraWorldAttachment.MAP_CODEC).build());
    /**
     * Attached to a ServerLevel by formation world adapters; never copied onto entities.
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FormationWorldAttachment>> FORMATION_WORLD = REGISTRY.register("formation_world", () -> AttachmentType.builder(FormationWorldAttachment::new).serialize(FormationWorldAttachment.MAP_CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ForgingWorldAttachment>> FORGING_WORLD = REGISTRY.register("forging_world", () -> AttachmentType.builder(ForgingWorldAttachment::new).serialize(ForgingWorldAttachment.MAP_CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ForgingSessionAttachment>> FORGING_SESSION = entityWithoutDeathCopy("forging_session", ForgingSessionAttachment::new, ForgingSessionAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FlightAttachment>> FLIGHT = entityWithoutDeathCopy("flight", FlightAttachment::new, FlightAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SectAttachment>> SECT = entity("sect", SectAttachment::new, SectAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SectTerritoryAttachment>> SECT_TERRITORY = REGISTRY.register("sect_territory", () -> AttachmentType.builder(SectTerritoryAttachment::new).serialize(SectTerritoryAttachment.CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RealmInstanceAttachment>> REALM_INSTANCE = REGISTRY.register("realm_instance", () -> AttachmentType.builder(RealmInstanceAttachment::new).serialize(RealmInstanceAttachment.CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RealmTravelAttachment>> REALM_TRAVEL = entity("realm_travel", RealmTravelAttachment::new, RealmTravelAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SoulAttachment>> SOUL = entity("soul", SoulAttachment::new, SoulAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CreatureSpiritAttachment>> CREATURE_SPIRIT = entity("creature_spirit", CreatureSpiritAttachment::new, CreatureSpiritAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PillToxicityAttachment>> PILL_TOXICITY = entity("pill_toxicity", PillToxicityAttachment::new, PillToxicityAttachment.CODEC);

    @SubscribeEvent
    public static void flushDirtyAttachments(Post event) {
        Entity entity = event.getEntity();
        if (entity.level().isClientSide()) return;
        for (Supplier<? extends AttachmentType<?>> type : SYNCED_ENTITY_ATTACHMENTS) {
            AttachmentType<?> rawType = type.get();
            if (entity.getExistingData(rawType).orElse(null) instanceof ShouldSyncAttachment attachment && attachment.checkDirty())
                entity.syncData(rawType);
        }
    }

    private static <T extends ShouldSyncAttachment> DeferredHolder<AttachmentType<?>, AttachmentType<T>> entity(String name, Supplier<T> factory, MapCodec<T> codec) {
        StreamCodec<RegistryFriendlyByteBuf, T> streamCodec = ByteBufCodecs.fromCodecWithRegistries(codec.codec());
        DeferredHolder<AttachmentType<?>, AttachmentType<T>> holder = REGISTRY.register(name, () -> AttachmentType.builder(factory).serialize(codec).sync(streamCodec).copyOnDeath().build());
        SYNCED_ENTITY_ATTACHMENTS.add(holder);
        return holder;
    }

    /**
     * In-progress inventories must not duplicate when a player respawns.
     */
    private static <T extends ShouldSyncAttachment> DeferredHolder<AttachmentType<?>, AttachmentType<T>> entityWithoutDeathCopy(String name, Supplier<T> factory, MapCodec<T> codec) {
        StreamCodec<RegistryFriendlyByteBuf, T> streamCodec = ByteBufCodecs.fromCodecWithRegistries(codec.codec());
        DeferredHolder<AttachmentType<?>, AttachmentType<T>> holder = REGISTRY.register(name, () -> AttachmentType.builder(factory).serialize(codec).sync(streamCodec).build());
        SYNCED_ENTITY_ATTACHMENTS.add(holder);
        return holder;
    }
}
