package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.CurrencyValue;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.RealmInstance;
import com.iafenvoy.mxt.data.Sect;
import com.iafenvoy.mxt.data.Title;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.data.artifact.ItemArchetype;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.BlockAura;
import com.iafenvoy.mxt.data.aura.ItemAura;
import com.iafenvoy.mxt.data.badge.Badge;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.iafenvoy.mxt.data.cultivation.CultivateAction;
import com.iafenvoy.mxt.data.cultivation.CultivationTechnique;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.Physique;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.item.ItemBinding;
import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.data.item.WeaponBinding;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.resource.Resource;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Native Minecraft datapack registries. Reloading and client synchronisation are
 * owned by the vanilla registry system; this class never stores a registry snapshot.
 */
@EventBusSubscriber
public final class MxtDatapackRegistries {
    private static final Identifier DISABLED_TAG = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "disabled");
    private static final List<ResourceKey<? extends Registry<?>>> KEYS = new LinkedList<>();

    @SubscribeEvent
    public static void newDatapackRegistries(NewRegistry event) {
        register(event, MxtResourceKeys.RESOURCE, Resource.DIRECT_CODEC);
        register(event, MxtResourceKeys.BADGE, Badge.DIRECT_CODEC);
        register(event, MxtResourceKeys.REALM_STAGE, RealmStage.DIRECT_CODEC);
        register(event, MxtResourceKeys.ELEMENT, Element.DIRECT_CODEC);
        register(event, MxtResourceKeys.SPIRIT_ROOT, SpiritRoot.DIRECT_CODEC);
        register(event, MxtResourceKeys.PHYSIQUE, Physique.DIRECT_CODEC);
        register(event, MxtResourceKeys.ABILITY, Ability.DIRECT_CODEC);
        register(event, MxtResourceKeys.CURSE, Curse.DIRECT_CODEC);
        register(event, MxtResourceKeys.FORGING_METHOD, ForgingMethod.DIRECT_CODEC);
        register(event, MxtResourceKeys.FORGING_BLUEPRINT, ForgingBlueprint.DIRECT_CODEC);
        register(event, MxtResourceKeys.CULTIVATION_TECHNIQUE, CultivationTechnique.DIRECT_CODEC);
        register(event, MxtResourceKeys.CULTIVATE_ACTION, CultivateAction.DIRECT_CODEC);
        register(event, MxtResourceKeys.ITEM_ARCHETYPE, ItemArchetype.DIRECT_CODEC);
        register(event, MxtResourceKeys.SPIRIT_HERB, SpiritHerb.CODEC);
        register(event, MxtResourceKeys.FORMATION, Formation.DIRECT_CODEC);
        register(event, MxtResourceKeys.TRIBULATION, Tribulation.DIRECT_CODEC);
        register(event, MxtResourceKeys.CREATURE_PROFILE, CreatureProfile.CODEC);
        register(event, MxtResourceKeys.CONTRACT_TYPE, ContractType.CODEC);
        register(event, MxtResourceKeys.TITLE, Title.DIRECT_CODEC);
        register(event, MxtResourceKeys.SECT, Sect.CODEC);
        register(event, MxtResourceKeys.REALM_INSTANCE, RealmInstance.CODEC);
        register(event, MxtResourceKeys.CURRENCY, CurrencyValue.CODEC);
        register(event, MxtResourceKeys.ITEM_BINDING, ItemBinding.CODEC);
        register(event, MxtResourceKeys.WEAPON_BINDING, WeaponBinding.CODEC);
        register(event, MxtResourceKeys.PILL_BINDING, PillBinding.CODEC);
        register(event, MxtResourceKeys.TECHNIQUE_BINDING, TechniqueBinding.CODEC);
        register(event, MxtResourceKeys.AURA_ZONE, AuraZone.DIRECT_CODEC);
        register(event, MxtResourceKeys.BLOCK_AURA, BlockAura.CODEC);
        register(event, MxtResourceKeys.ITEM_AURA, ItemAura.DIRECT_CODEC);
        register(event, MxtResourceKeys.ITEM_QUALITY, ItemQuality.DIRECT_CODEC);
    }

    private static <T> void register(NewRegistry event, ResourceKey<Registry<T>> key, Codec<T> codec) {
        event.dataPackRegistry(key, codec, codec);
        KEYS.add(key);
    }

    public static List<ResourceKey<? extends Registry<?>>> registries() {
        return KEYS;
    }

    public static <T> Optional<T> get(ResourceKey<? extends Registry<T>> key, Identifier id) {
        return isDisabled(key, id) ? Optional.empty() : registry(key).getOptional(id);
    }

    /**
     * Returns a directly held enabled datapack value without a second registry lookup.
     */
    public static <T> Optional<T> get(ResourceKey<? extends Registry<T>> key, Holder<T> holder) {
        TagKey<T> disabled = TagKey.create(key, DISABLED_TAG);
        return holder.is(disabled) ? Optional.empty() : Optional.of(holder.value());
    }

    /**
     * Resolves an enabled registry entry while retaining its stable holder reference.
     */
    public static <T> Optional<Reference<T>> holder(ResourceKey<? extends Registry<T>> key, Identifier id) {
        TagKey<T> disabled = TagKey.create(key, DISABLED_TAG);
        return registry(key).get(ResourceKey.create(key, id)).filter(holder -> !holder.is(disabled));
    }

    public static <T> Stream<Reference<T>> holders(ResourceKey<? extends Registry<T>> key) {
        TagKey<T> disabled = TagKey.create(key, DISABLED_TAG);
        return registry(key).listElements().filter(holder -> !holder.is(disabled));
    }

    /**
     * Reads enabled entries from either a server or the client-synchronised registry access.
     */
    public static <T> Stream<Reference<T>> holders(RegistryAccess access, ResourceKey<? extends Registry<T>> key) {
        TagKey<T> disabled = TagKey.create(key, DISABLED_TAG);
        return access.lookupOrThrow(key).listElements().filter(holder -> !holder.is(disabled));
    }

    /**
     * Reads enabled entries from a client-synchronised datapack registry lookup.
     */
    public static <T> Stream<Reference<T>> holders(Provider access, ResourceKey<? extends Registry<T>> key) {
        TagKey<T> disabled = TagKey.create(key, DISABLED_TAG);
        return access.lookupOrThrow(key).listElements().filter(holder -> !holder.is(disabled));
    }

    /**
     * Resolves an enabled entry from a client-synchronised datapack registry lookup.
     */
    public static <T> Optional<T> get(Provider access, ResourceKey<? extends Registry<T>> key, Identifier id) {
        TagKey<T> disabled = TagKey.create(key, DISABLED_TAG);
        return access.lookupOrThrow(key).get(ResourceKey.create(key, id))
                .filter(holder -> !holder.is(disabled))
                .map(Holder::value);
    }

    public static <T> Optional<T> get(Provider access, ResourceKey<? extends Registry<T>> key, Holder<T> holder) {
        return holder.is(TagKey.create(key, DISABLED_TAG)) ? Optional.empty() : Optional.of(holder.value());
    }

    public static <T> boolean isDisabled(ResourceKey<? extends Registry<T>> key, Identifier id) {
        TagKey<T> disabled = TagKey.create(key, DISABLED_TAG);
        return registry(key).get(ResourceKey.create(key, id)).map(holder -> holder.is(disabled)).orElse(false);
    }

    public static <T> boolean isDisabled(ResourceKey<? extends Registry<T>> key, Holder<T> holder) {
        return holder.is(TagKey.create(key, DISABLED_TAG));
    }

    /**
     * Checks a native datapack tag on one entry of a custom dynamic registry.
     */
    public static <T> boolean isTagged(ResourceKey<? extends Registry<T>> key, Identifier id, Identifier tagId) {
        TagKey<T> tag = TagKey.create(key, tagId);
        return registry(key).get(ResourceKey.create(key, id)).map(holder -> holder.is(tag)).orElse(false);
    }

    public static <T> boolean isTagged(ResourceKey<? extends Registry<T>> key, Holder<T> holder, Identifier tagId) {
        return holder.is(TagKey.create(key, tagId));
    }

    public static int size(ResourceKey<? extends Registry<?>> key) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("Native datapack registries are only available while a server is running");
        }
        return server.registryAccess().lookupOrThrow(key).size();
    }

    public static <T> Registry<T> registry(ResourceKey<? extends Registry<T>> key) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            throw new IllegalStateException("Native datapack registries are only available while a server is running");
        }
        return server.registryAccess().lookupOrThrow(key);
    }
}
