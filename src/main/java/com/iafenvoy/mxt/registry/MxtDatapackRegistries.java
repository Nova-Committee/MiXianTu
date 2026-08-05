package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.alchemy.AlchemyRecipe;
import com.iafenvoy.mxt.data.alchemy.SpiritHerb;
import com.iafenvoy.mxt.data.artifact.ItemArchetype;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.iafenvoy.mxt.data.cultivation.*;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.data.CurrencyValue;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.Formation;
import com.iafenvoy.mxt.data.MaterialGrade;
import com.iafenvoy.mxt.data.resource.ResourceBar;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.Sect;
import com.iafenvoy.mxt.data.Title;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.RealmInstance;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.BlockAura;
import com.iafenvoy.mxt.data.item.ItemBinding;
import com.iafenvoy.mxt.data.item.PillBinding;
import com.iafenvoy.mxt.data.item.WeaponBinding;
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
    public static final ResourceKey<Registry<Resource>> RESOURCE = MxtRegistryKeys.RESOURCE;
    public static final ResourceKey<Registry<ResourceBar>> RESOURCE_BAR = MxtRegistryKeys.RESOURCE_BAR;
    public static final ResourceKey<Registry<RealmStage>> REALM_STAGE = MxtRegistryKeys.REALM_STAGE;
    public static final ResourceKey<Registry<Element>> ELEMENT = MxtRegistryKeys.ELEMENT;
    public static final ResourceKey<Registry<SpiritRoot>> SPIRIT_ROOT = MxtRegistryKeys.SPIRIT_ROOT;
    public static final ResourceKey<Registry<Physique>> PHYSIQUE = MxtRegistryKeys.PHYSIQUE;
    public static final ResourceKey<Registry<Ability>> ABILITY = MxtRegistryKeys.ABILITY;
    public static final ResourceKey<Registry<Curse>> CURSE = MxtRegistryKeys.CURSE;
    public static final ResourceKey<Registry<ForgingMethod>> FORGING_METHOD = MxtRegistryKeys.FORGING_METHOD;
    public static final ResourceKey<Registry<ForgingBlueprint>> FORGING_BLUEPRINT = MxtRegistryKeys.FORGING_BLUEPRINT;
    public static final ResourceKey<Registry<CultivationTechnique>> CULTIVATION_TECHNIQUE = MxtRegistryKeys.CULTIVATION_TECHNIQUE;
    public static final ResourceKey<Registry<CultivateAction>> CULTIVATE_ACTION = MxtRegistryKeys.CULTIVATE_ACTION;
    public static final ResourceKey<Registry<ItemArchetype>> ITEM_ARCHETYPE = MxtRegistryKeys.ITEM_ARCHETYPE;
    public static final ResourceKey<Registry<SpiritHerb>> SPIRIT_HERB = MxtRegistryKeys.SPIRIT_HERB;
    public static final ResourceKey<Registry<AlchemyRecipe>> ALCHEMY_RECIPE = MxtRegistryKeys.ALCHEMY_RECIPE;
    public static final ResourceKey<Registry<Formation>> FORMATION = MxtRegistryKeys.FORMATION;
    public static final ResourceKey<Registry<Tribulation>> TRIBULATION = MxtRegistryKeys.TRIBULATION;
    public static final ResourceKey<Registry<CreatureProfile>> CREATURE_PROFILE = MxtRegistryKeys.CREATURE_PROFILE;
    public static final ResourceKey<Registry<ContractType>> CONTRACT_TYPE = MxtRegistryKeys.CONTRACT_TYPE;
    public static final ResourceKey<Registry<Title>> TITLE = MxtRegistryKeys.TITLE;
    public static final ResourceKey<Registry<MaterialGrade>> MATERIAL_GRADE = MxtRegistryKeys.MATERIAL_GRADE;
    public static final ResourceKey<Registry<Sect>> SECT = MxtRegistryKeys.SECT;
    public static final ResourceKey<Registry<RealmInstance>> REALM_INSTANCE = MxtRegistryKeys.REALM_INSTANCE;
    public static final ResourceKey<Registry<CurrencyValue>> CURRENCY = MxtRegistryKeys.CURRENCY;
    public static final ResourceKey<Registry<ItemBinding>> ITEM_BINDING = MxtRegistryKeys.ITEM_BINDING;
    public static final ResourceKey<Registry<WeaponBinding>> WEAPON_BINDING = MxtRegistryKeys.WEAPON_BINDING;
    public static final ResourceKey<Registry<PillBinding>> PILL_BINDING = MxtRegistryKeys.PILL_BINDING;
    public static final ResourceKey<Registry<AuraZone>> AURA_ZONE = MxtRegistryKeys.AURA_ZONE;
    public static final ResourceKey<Registry<BlockAura>> BLOCK_AURA = MxtRegistryKeys.BLOCK_AURA;
    private static final List<ResourceKey<? extends Registry<?>>> KEYS = List.of(
            MxtRegistryKeys.RESOURCE, MxtRegistryKeys.RESOURCE_BAR, MxtRegistryKeys.REALM_STAGE,
            MxtRegistryKeys.ELEMENT, MxtRegistryKeys.SPIRIT_ROOT, MxtRegistryKeys.PHYSIQUE,
            MxtRegistryKeys.ABILITY, MxtRegistryKeys.CURSE, MxtRegistryKeys.FORGING_METHOD,
            MxtRegistryKeys.FORGING_BLUEPRINT, MxtRegistryKeys.CULTIVATION_TECHNIQUE,
            MxtRegistryKeys.CULTIVATE_ACTION, MxtRegistryKeys.ITEM_ARCHETYPE, MxtRegistryKeys.SPIRIT_HERB,
            MxtRegistryKeys.ALCHEMY_RECIPE, MxtRegistryKeys.FORMATION,
            MxtRegistryKeys.TRIBULATION, MxtRegistryKeys.CREATURE_PROFILE, MxtRegistryKeys.CONTRACT_TYPE,
            MxtRegistryKeys.TITLE, MxtRegistryKeys.MATERIAL_GRADE, MxtRegistryKeys.SECT,
            MxtRegistryKeys.REALM_INSTANCE, MxtRegistryKeys.CURRENCY,
            MxtRegistryKeys.ITEM_BINDING, MxtRegistryKeys.WEAPON_BINDING, MxtRegistryKeys.PILL_BINDING,
            MxtRegistryKeys.AURA_ZONE,
            MxtRegistryKeys.BLOCK_AURA
    );

    @SubscribeEvent
    public static void newDatapackRegistries(NewRegistry event) {
        register(event, MxtRegistryKeys.RESOURCE, Resource.DIRECT_CODEC);
        register(event, MxtRegistryKeys.RESOURCE_BAR, ResourceBar.DIRECT_CODEC);
        register(event, MxtRegistryKeys.REALM_STAGE, RealmStage.DIRECT_CODEC);
        register(event, MxtRegistryKeys.ELEMENT, Element.DIRECT_CODEC);
        register(event, MxtRegistryKeys.SPIRIT_ROOT, SpiritRoot.DIRECT_CODEC);
        register(event, MxtRegistryKeys.PHYSIQUE, Physique.CODEC);
        register(event, MxtRegistryKeys.ABILITY, Ability.DIRECT_CODEC);
        register(event, MxtRegistryKeys.CURSE, Curse.DIRECT_CODEC);
        register(event, MxtRegistryKeys.FORGING_METHOD, ForgingMethod.DIRECT_CODEC);
        register(event, MxtRegistryKeys.FORGING_BLUEPRINT, ForgingBlueprint.DIRECT_CODEC);
        register(event, MxtRegistryKeys.CULTIVATION_TECHNIQUE, CultivationTechnique.CODEC);
        register(event, MxtRegistryKeys.CULTIVATE_ACTION, CultivateAction.CODEC);
        register(event, MxtRegistryKeys.ITEM_ARCHETYPE, ItemArchetype.CODEC);
        register(event, MxtRegistryKeys.SPIRIT_HERB, SpiritHerb.CODEC);
        register(event, MxtRegistryKeys.ALCHEMY_RECIPE, AlchemyRecipe.CODEC);
        register(event, MxtRegistryKeys.FORMATION, Formation.DIRECT_CODEC);
        register(event, MxtRegistryKeys.TRIBULATION, Tribulation.DIRECT_CODEC);
        register(event, MxtRegistryKeys.CREATURE_PROFILE, CreatureProfile.CODEC);
        register(event, MxtRegistryKeys.CONTRACT_TYPE, ContractType.CODEC);
        register(event, MxtRegistryKeys.TITLE, Title.CODEC);
        register(event, MxtRegistryKeys.MATERIAL_GRADE, MaterialGrade.DIRECT_CODEC);
        register(event, MxtRegistryKeys.SECT, Sect.CODEC);
        register(event, MxtRegistryKeys.REALM_INSTANCE, RealmInstance.CODEC);
        register(event, MxtRegistryKeys.CURRENCY, CurrencyValue.CODEC);
        register(event, MxtRegistryKeys.ITEM_BINDING, ItemBinding.CODEC);
        register(event, MxtRegistryKeys.WEAPON_BINDING, WeaponBinding.CODEC);
        register(event, MxtRegistryKeys.PILL_BINDING, PillBinding.CODEC);
        register(event, MxtRegistryKeys.AURA_ZONE, AuraZone.DIRECT_CODEC);
        register(event, MxtRegistryKeys.BLOCK_AURA, BlockAura.CODEC);
    }

    public static List<ResourceKey<? extends Registry<?>>> registries() {
        return KEYS;
    }

    public static <T> Optional<T> get(ResourceKey<? extends Registry<T>> key, Identifier id) {
        return isDisabled(key, id) ? Optional.empty() : registry(key).getOptional(id);
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

    public static <T> boolean isDisabled(ResourceKey<? extends Registry<T>> key, Identifier id) {
        TagKey<T> disabled = TagKey.create(key, DISABLED_TAG);
        return registry(key).get(ResourceKey.create(key, id)).map(holder -> holder.is(disabled)).orElse(false);
    }

    /**
     * Checks a native datapack tag on one entry of a custom dynamic registry.
     */
    public static <T> boolean isTagged(ResourceKey<? extends Registry<T>> key, Identifier id, Identifier tagId) {
        TagKey<T> tag = TagKey.create(key, tagId);
        return registry(key).get(ResourceKey.create(key, id)).map(holder -> holder.is(tag)).orElse(false);
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

    private static <T> void register(NewRegistry event, ResourceKey<Registry<T>> key, Codec<T> codec) {
        event.dataPackRegistry(key, codec, codec);
    }
}
