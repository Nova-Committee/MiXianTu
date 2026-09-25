package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.*;
import com.iafenvoy.mxt.runtime.creature.BoundBeastsAttachment;
import com.iafenvoy.mxt.runtime.formation.FormationWorldAttachment;
import com.iafenvoy.mxt.runtime.world.AuraWorldAttachment;
import com.iafenvoy.mxt.runtime.world.SecretRealmWorldAttachment;
import com.iafenvoy.mxt.util.ShouldSyncAttachment;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.tick.EntityTickEvent.Post;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

@EventBusSubscriber
public final class MxtAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTRY = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MiXianTu.MOD_ID);
    private static final List<Supplier<? extends AttachmentType<?>>> SYNCED_ENTITY_ATTACHMENTS = new ArrayList<>();

    // Entity
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CultivationAttachment>> CULTIVATION = entity("cultivation", CultivationAttachment::new, CultivationAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SpiritIdentityAttachment>> SPIRIT_IDENTITY = entity("spirit_identity", SpiritIdentityAttachment::new, SpiritIdentityAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SpiritStatsAttachment>> SPIRIT_STATS = entity("spirit_stats", SpiritStatsAttachment::new, SpiritStatsAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FloatHoldingItemAttachment>> FLOAT_HOLDING_ITEM = entityWithoutDeathCopy("float_holding_item", FloatHoldingItemAttachment::new, FloatHoldingItemAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ResourceHolderAttachment>> RESOURCE_HOLDER = entity("resource_holder", ResourceHolderAttachment::new, ResourceHolderAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ElementAttachment>> ELEMENT_ATTACHMENT = entity("element_attachment", ElementAttachment::new, ElementAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SpiritBurstCooldownAttachment>> SPIRIT_BURST_COOLDOWNS = entity("spirit_burst_cooldowns", SpiritBurstCooldownAttachment::new, SpiritBurstCooldownAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AbilityAttachment>> ABILITY_HOLDER = entity("ability_holder", AbilityAttachment::new, AbilityAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<WheelLayoutAttachment>> WHEEL_LAYOUT = entity("wheel_layout", WheelLayoutAttachment::new, WheelLayoutAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CurseHolderAttachment>> CURSE_HOLDER = entity("curse_holder", CurseHolderAttachment::new, CurseHolderAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TribulationAttachment>> TRIBULATION = entity("tribulation", TribulationAttachment::new, TribulationAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<ContractAttachment>> CONTRACT = entity("contract", ContractAttachment::new, ContractAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SecretRealmTravelAttachment>> SECRET_REALM_TRAVEL = entity("secret_realm_travel", SecretRealmTravelAttachment::new, SecretRealmTravelAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SoulAttachment>> SOUL = entity("soul", SoulAttachment::new, SoulAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<CreatureSpiritAttachment>> CREATURE_SPIRIT = entity("creature_spirit", CreatureSpiritAttachment::new, CreatureSpiritAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PillToxicityAttachment>> PILL_TOXICITY = entity("pill_toxicity", PillToxicityAttachment::new, PillToxicityAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FlightAttachment>> FLIGHT = entityWithoutDeathCopy("flight", FlightAttachment::new, FlightAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FriendAttachment>> FRIEND = entityServerOnly("friend", FriendAttachment::new, FriendAttachment.CODEC);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TriggerCooldownAttachment>> TRIGGER_COOLDOWNS = entityServerOnly("trigger_cooldowns", TriggerCooldownAttachment::new, TriggerCooldownAttachment.CODEC);
    // Level
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AuraWorldAttachment>> AURA_WORLD = REGISTRY.register("aura_world", () -> AttachmentType.builder(AuraWorldAttachment::new).serialize(AuraWorldAttachment.MAP_CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<FormationWorldAttachment>> FORMATION_WORLD = REGISTRY.register("formation_world", () -> AttachmentType.builder(FormationWorldAttachment::new).serialize(FormationWorldAttachment.MAP_CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<AuraChunkAttachment>> AURA_CHUNK = REGISTRY.register("aura_chunk", () -> AttachmentType.builder(AuraChunkAttachment::new).serialize(AuraChunkAttachment.CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SecretRealmWorldAttachment>> SECRET_REALM_WORLD = REGISTRY.register("secret_realm_world", () -> AttachmentType.builder(SecretRealmWorldAttachment::new).serialize(SecretRealmWorldAttachment.MAP_CODEC).build());
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<BoundBeastsAttachment>> BOUND_BEASTS = REGISTRY.register("bound_beasts", () -> AttachmentType.builder(BoundBeastsAttachment::new).serialize(BoundBeastsAttachment.MAP_CODEC).build());

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

    // No copyOnDeath: an in-progress inventory must not duplicate when a player respawns.
    private static <T extends ShouldSyncAttachment> DeferredHolder<AttachmentType<?>, AttachmentType<T>> entityWithoutDeathCopy(String name, Supplier<T> factory, MapCodec<T> codec) {
        StreamCodec<RegistryFriendlyByteBuf, T> streamCodec = ByteBufCodecs.fromCodecWithRegistries(codec.codec());
        DeferredHolder<AttachmentType<?>, AttachmentType<T>> holder = REGISTRY.register(name, () -> AttachmentType.builder(factory).serialize(codec).sync(streamCodec).build());
        SYNCED_ENTITY_ATTACHMENTS.add(holder);
        return holder;
    }

    // A server-owned attachment: saved and copied on death but never sent to the client, which is also how an
    // attachment opts out of ShouldSyncAttachment. A synced copy of FriendAttachment would be stale from the
    // next login onwards, because its session half is emptied at login.
    private static <T> DeferredHolder<AttachmentType<?>, AttachmentType<T>> entityServerOnly(String name, Supplier<T> factory, MapCodec<T> codec) {
        return REGISTRY.register(name, () -> AttachmentType.builder(factory).serialize(codec).copyOnDeath().build());
    }
}
