package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.ability.AbilityDefinition;
import com.iafenvoy.mxt.data.alchemy.AlchemyRecipeDefinition;
import com.iafenvoy.mxt.data.alchemy.SpiritHerbDefinition;
import com.iafenvoy.mxt.data.artifact.ItemArchetypeDefinition;
import com.iafenvoy.mxt.data.creature.ContractTypeDefinition;
import com.iafenvoy.mxt.data.creature.CreatureProfileDefinition;
import com.iafenvoy.mxt.data.cultivation.*;
import com.iafenvoy.mxt.data.curse.CurseDefinition;
import com.iafenvoy.mxt.data.economy.CurrencyValueDefinition;
import com.iafenvoy.mxt.data.forging.ForgingBlueprintDefinition;
import com.iafenvoy.mxt.data.forging.ForgingMethodDefinition;
import com.iafenvoy.mxt.data.formation.FormationDefinition;
import com.iafenvoy.mxt.data.material.MaterialGradeDefinition;
import com.iafenvoy.mxt.data.resource.ResourceBarDefinition;
import com.iafenvoy.mxt.data.resource.ResourceDefinition;
import com.iafenvoy.mxt.data.sect.SectDefinition;
import com.iafenvoy.mxt.data.title.TitleDefinition;
import com.iafenvoy.mxt.data.tribulation.TribulationDefinition;
import com.iafenvoy.mxt.data.world.RealmInstanceDefinition;
import com.iafenvoy.mxt.data.world.AuraZoneDefinition;
import com.iafenvoy.mxt.data.world.BlockAuraDefinition;
import com.iafenvoy.mxt.data.item.ItemBindingDefinition;
import com.iafenvoy.mxt.data.item.ItemDefinition;
import com.iafenvoy.mxt.data.item.ItemEffectDefinition;
import com.iafenvoy.mxt.data.item.ItemDefinitionReference;
import com.iafenvoy.mxt.data.item.ItemDefinitionRegistry;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Native Minecraft datapack registries. Reloading and client synchronisation are
 * owned by the vanilla registry system; this class never stores a registry snapshot.
 */
public final class MxtDatapackRegistries {
    private static final Identifier DISABLED_TAG = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "disabled");
    public static final ResourceKey<Registry<ResourceDefinition>> RESOURCE = MxtRegistryKeys.RESOURCE;
    public static final ResourceKey<Registry<ResourceBarDefinition>> RESOURCE_BAR = MxtRegistryKeys.RESOURCE_BAR;
    public static final ResourceKey<Registry<RealmStageDefinition>> REALM_STAGE = MxtRegistryKeys.REALM_STAGE;
    public static final ResourceKey<Registry<ElementDefinition>> ELEMENT = MxtRegistryKeys.ELEMENT;
    public static final ResourceKey<Registry<SpiritRootDefinition>> SPIRIT_ROOT = MxtRegistryKeys.SPIRIT_ROOT;
    public static final ResourceKey<Registry<PhysiqueDefinition>> PHYSIQUE = MxtRegistryKeys.PHYSIQUE;
    public static final ResourceKey<Registry<AbilityDefinition>> ABILITY = MxtRegistryKeys.ABILITY;
    public static final ResourceKey<Registry<CurseDefinition>> CURSE = MxtRegistryKeys.CURSE;
    public static final ResourceKey<Registry<ForgingMethodDefinition>> FORGING_METHOD = MxtRegistryKeys.FORGING_METHOD;
    public static final ResourceKey<Registry<ForgingBlueprintDefinition>> FORGING_BLUEPRINT = MxtRegistryKeys.FORGING_BLUEPRINT;
    public static final ResourceKey<Registry<CultivationTechniqueDefinition>> CULTIVATION_TECHNIQUE = MxtRegistryKeys.CULTIVATION_TECHNIQUE;
    public static final ResourceKey<Registry<CultivateActionDefinition>> CULTIVATE_ACTION = MxtRegistryKeys.CULTIVATE_ACTION;
    public static final ResourceKey<Registry<ItemArchetypeDefinition>> ITEM_ARCHETYPE = MxtRegistryKeys.ITEM_ARCHETYPE;
    public static final ResourceKey<Registry<SpiritHerbDefinition>> SPIRIT_HERB = MxtRegistryKeys.SPIRIT_HERB;
    public static final ResourceKey<Registry<AlchemyRecipeDefinition>> ALCHEMY_RECIPE = MxtRegistryKeys.ALCHEMY_RECIPE;
    public static final ResourceKey<Registry<FormationDefinition>> FORMATION = MxtRegistryKeys.FORMATION;
    public static final ResourceKey<Registry<TribulationDefinition>> TRIBULATION = MxtRegistryKeys.TRIBULATION;
    public static final ResourceKey<Registry<CreatureProfileDefinition>> CREATURE_PROFILE = MxtRegistryKeys.CREATURE_PROFILE;
    public static final ResourceKey<Registry<ContractTypeDefinition>> CONTRACT_TYPE = MxtRegistryKeys.CONTRACT_TYPE;
    public static final ResourceKey<Registry<TitleDefinition>> TITLE = MxtRegistryKeys.TITLE;
    public static final ResourceKey<Registry<MaterialGradeDefinition>> MATERIAL_GRADE = MxtRegistryKeys.MATERIAL_GRADE;
    public static final ResourceKey<Registry<SectDefinition>> SECT = MxtRegistryKeys.SECT;
    public static final ResourceKey<Registry<RealmInstanceDefinition>> REALM_INSTANCE = MxtRegistryKeys.REALM_INSTANCE;
    public static final ResourceKey<Registry<CurrencyValueDefinition>> CURRENCY = MxtRegistryKeys.CURRENCY;
    public static final ResourceKey<Registry<ItemDefinition>> ITEM = MxtRegistryKeys.ITEM;
    public static final ResourceKey<Registry<ItemDefinition>> PILL = MxtRegistryKeys.PILL;
    public static final ResourceKey<Registry<ItemDefinition>> WEAPON = MxtRegistryKeys.WEAPON;
    public static final ResourceKey<Registry<ItemEffectDefinition>> ITEM_EFFECT = MxtRegistryKeys.ITEM_EFFECT;
    public static final ResourceKey<Registry<ItemBindingDefinition>> ITEM_BINDING = MxtRegistryKeys.ITEM_BINDING;
    public static final ResourceKey<Registry<AuraZoneDefinition>> AURA_ZONE = MxtRegistryKeys.AURA_ZONE;
    public static final ResourceKey<Registry<BlockAuraDefinition>> BLOCK_AURA = MxtRegistryKeys.BLOCK_AURA;
    private static final List<ResourceKey<? extends Registry<?>>> KEYS = List.of(
            MxtRegistryKeys.RESOURCE, MxtRegistryKeys.RESOURCE_BAR, MxtRegistryKeys.REALM_STAGE,
            MxtRegistryKeys.ELEMENT, MxtRegistryKeys.SPIRIT_ROOT, MxtRegistryKeys.PHYSIQUE,
            MxtRegistryKeys.ABILITY, MxtRegistryKeys.CURSE, MxtRegistryKeys.FORGING_METHOD,
            MxtRegistryKeys.FORGING_BLUEPRINT, MxtRegistryKeys.CULTIVATION_TECHNIQUE,
            MxtRegistryKeys.CULTIVATE_ACTION, MxtRegistryKeys.ITEM_ARCHETYPE, MxtRegistryKeys.SPIRIT_HERB,
            MxtRegistryKeys.ALCHEMY_RECIPE, MxtRegistryKeys.FORMATION,
            MxtRegistryKeys.TRIBULATION, MxtRegistryKeys.CREATURE_PROFILE, MxtRegistryKeys.CONTRACT_TYPE,
            MxtRegistryKeys.TITLE, MxtRegistryKeys.MATERIAL_GRADE, MxtRegistryKeys.SECT,
            MxtRegistryKeys.REALM_INSTANCE, MxtRegistryKeys.CURRENCY, MxtRegistryKeys.ITEM,
            MxtRegistryKeys.PILL, MxtRegistryKeys.WEAPON,
            MxtRegistryKeys.ITEM_EFFECT,
            MxtRegistryKeys.ITEM_BINDING,
            MxtRegistryKeys.AURA_ZONE,
            MxtRegistryKeys.BLOCK_AURA
    );

    private MxtDatapackRegistries() {
    }

    public static void newDatapackRegistries(NewRegistry event) {
        register(event, MxtRegistryKeys.RESOURCE, ResourceDefinition.CODEC);
        register(event, MxtRegistryKeys.RESOURCE_BAR, ResourceBarDefinition.CODEC);
        register(event, MxtRegistryKeys.REALM_STAGE, RealmStageDefinition.CODEC);
        register(event, MxtRegistryKeys.ELEMENT, ElementDefinition.CODEC);
        register(event, MxtRegistryKeys.SPIRIT_ROOT, SpiritRootDefinition.CODEC);
        register(event, MxtRegistryKeys.PHYSIQUE, PhysiqueDefinition.CODEC);
        register(event, MxtRegistryKeys.ABILITY, AbilityDefinition.CODEC);
        register(event, MxtRegistryKeys.CURSE, CurseDefinition.CODEC);
        register(event, MxtRegistryKeys.FORGING_METHOD, ForgingMethodDefinition.CODEC);
        register(event, MxtRegistryKeys.FORGING_BLUEPRINT,
                ForgingBlueprintDefinition.codec(RegistryFixedCodec.create(MxtRegistryKeys.FORGING_METHOD)));
        register(event, MxtRegistryKeys.CULTIVATION_TECHNIQUE, CultivationTechniqueDefinition.CODEC);
        register(event, MxtRegistryKeys.CULTIVATE_ACTION, CultivateActionDefinition.CODEC);
        register(event, MxtRegistryKeys.ITEM_ARCHETYPE, ItemArchetypeDefinition.CODEC);
        register(event, MxtRegistryKeys.SPIRIT_HERB, SpiritHerbDefinition.CODEC);
        register(event, MxtRegistryKeys.ALCHEMY_RECIPE, AlchemyRecipeDefinition.CODEC);
        register(event, MxtRegistryKeys.FORMATION, FormationDefinition.CODEC);
        register(event, MxtRegistryKeys.TRIBULATION, TribulationDefinition.CODEC);
        register(event, MxtRegistryKeys.CREATURE_PROFILE, CreatureProfileDefinition.CODEC);
        register(event, MxtRegistryKeys.CONTRACT_TYPE, ContractTypeDefinition.CODEC);
        register(event, MxtRegistryKeys.TITLE, TitleDefinition.CODEC);
        register(event, MxtRegistryKeys.MATERIAL_GRADE, MaterialGradeDefinition.CODEC);
        register(event, MxtRegistryKeys.SECT, SectDefinition.CODEC);
        register(event, MxtRegistryKeys.REALM_INSTANCE, RealmInstanceDefinition.CODEC);
        register(event, MxtRegistryKeys.CURRENCY, CurrencyValueDefinition.CODEC);
        register(event, MxtRegistryKeys.ITEM, ItemDefinition.CODEC);
        register(event, MxtRegistryKeys.PILL, ItemDefinition.CODEC);
        register(event, MxtRegistryKeys.WEAPON, ItemDefinition.CODEC);
        register(event, MxtRegistryKeys.ITEM_EFFECT, ItemEffectDefinition.CODEC);
        register(event, MxtRegistryKeys.ITEM_BINDING, ItemBindingDefinition.CODEC);
        register(event, MxtRegistryKeys.AURA_ZONE, AuraZoneDefinition.CODEC);
        register(event, MxtRegistryKeys.BLOCK_AURA, BlockAuraDefinition.CODEC);
    }

    public static List<ResourceKey<? extends Registry<?>>> registries() {
        return KEYS;
    }

    public static <T> Optional<T> get(ResourceKey<? extends Registry<T>> key, Identifier id) {
        return isDisabled(key, id) ? Optional.empty() : registry(key).getOptional(id);
    }

    /**
     * Resolves a definition from its explicit data-driven item category.
     */
    public static Optional<ItemDefinition> get(ItemDefinitionReference reference) {
        return get(itemRegistry(reference.registry()), reference.id());
    }

    /**
     * Returns all category-qualified definitions with the supplied ID.
     */
    public static Stream<ItemDefinitionReference> itemReferences(Identifier id) {
        return Stream.of(ItemDefinitionRegistry.values())
                .filter(category -> get(itemRegistry(category), id).isPresent())
                .map(category -> new ItemDefinitionReference(category, id));
    }

    public static ResourceKey<Registry<ItemDefinition>> itemRegistry(ItemDefinitionRegistry category) {
        return switch (category) {
            case OTHER -> ITEM;
            case PILL -> PILL;
            case WEAPON -> WEAPON;
        };
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

    /**
     * Resolves a category-qualified item definition from a client registry lookup.
     */
    public static Optional<ItemDefinition> get(Provider access, ItemDefinitionReference reference) {
        return get(access, itemRegistry(reference.registry()), reference.id());
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
