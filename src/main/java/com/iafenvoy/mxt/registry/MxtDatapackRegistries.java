package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.alchemy.*;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.data.aura.BlockAura;
import com.iafenvoy.mxt.data.aura.ItemAura;
import com.iafenvoy.mxt.data.creature.ContractType;
import com.iafenvoy.mxt.data.creature.CreatureProfile;
import com.iafenvoy.mxt.data.cultivation.*;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.data.economy.CurrencyValue;
import com.iafenvoy.mxt.data.forging.BlueprintBinding;
import com.iafenvoy.mxt.data.forging.ForgingBlueprint;
import com.iafenvoy.mxt.data.forging.ForgingMethod;
import com.iafenvoy.mxt.data.forging.ToolBinding;
import com.iafenvoy.mxt.data.formation.Formation;
import com.iafenvoy.mxt.data.item.*;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.quality.DefaultQuality;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.data.talisman.Talisman;
import com.iafenvoy.mxt.data.trigger.TriggerRule;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.DataPackRegistryEvent.NewRegistry;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Native Minecraft datapack registries. Reloading and client synchronization belong to the vanilla registry
 * system, so this class never stores a registry snapshot; a definition a pack wants out of the world is kept out
 * of the pack (or gated by a {@code neoforge:conditions} block, see {@code docs/数据包格式.md}), so an entry that
 * did load is always live and nothing here filters.
 */
@EventBusSubscriber
public final class MxtDatapackRegistries {
    private static final List<ResourceKey<? extends Registry<?>>> KEYS = new LinkedList<>();

    @SubscribeEvent
    public static void newDatapackRegistries(NewRegistry event) {
        register(event, MxtResourceKeys.RESOURCE, Resource.DIRECT_CODEC);
        register(event, MxtResourceKeys.AURA, Aura.DIRECT_CODEC);
        register(event, MxtResourceKeys.REALM_STAGE, RealmStage.DIRECT_CODEC);
        register(event, MxtResourceKeys.ELEMENT, Element.DIRECT_CODEC);
        register(event, MxtResourceKeys.ELEMENT_REACTION, ElementReaction.DIRECT_CODEC);
        register(event, MxtResourceKeys.SPIRIT_ROOT, SpiritRoot.DIRECT_CODEC);
        register(event, MxtResourceKeys.PHYSIQUE, Physique.DIRECT_CODEC);
        register(event, MxtResourceKeys.ABILITY, Ability.DIRECT_CODEC);
        register(event, MxtResourceKeys.CURSE, Curse.DIRECT_CODEC);
        register(event, MxtResourceKeys.FORGING_METHOD, ForgingMethod.DIRECT_CODEC);
        register(event, MxtResourceKeys.FORGING_BLUEPRINT, ForgingBlueprint.DIRECT_CODEC);
        register(event, MxtResourceKeys.TOOL_BINDING, ToolBinding.DIRECT_CODEC);
        register(event, MxtResourceKeys.BLUEPRINT_BINDING, BlueprintBinding.DIRECT_CODEC);
        register(event, MxtResourceKeys.TECHNIQUE, Technique.DIRECT_CODEC);
        register(event, MxtResourceKeys.PROGRESSION, Progression.DIRECT_CODEC);
        register(event, MxtResourceKeys.CULTIVATION, Cultivation.DIRECT_CODEC);
        register(event, MxtResourceKeys.ARTIFACT, Artifact.DIRECT_CODEC);
        register(event, MxtResourceKeys.MEDICINAL_PROPERTY, MedicinalProperty.DIRECT_CODEC);
        register(event, MxtResourceKeys.SPIRIT_HERB, SpiritHerb.DIRECT_CODEC);
        register(event, MxtResourceKeys.ALCHEMY_FURNACE, AlchemyFurnaceDefinition.DIRECT_CODEC);
        register(event, MxtResourceKeys.ALCHEMY_WALL_MATERIAL, AlchemyWallMaterial.DIRECT_CODEC);
        register(event, MxtResourceKeys.HEAT_SOURCE, HeatSource.DIRECT_CODEC);
        register(event, MxtResourceKeys.FORMATION, Formation.DIRECT_CODEC);
        register(event, MxtResourceKeys.TRIBULATION, Tribulation.DIRECT_CODEC);
        register(event, MxtResourceKeys.PILL, Pill.DIRECT_CODEC);
        register(event, MxtResourceKeys.PILL_BINDING, PillBinding.DIRECT_CODEC);
        register(event, MxtResourceKeys.AURA_ZONE, AuraZone.DIRECT_CODEC);
        register(event, MxtResourceKeys.ITEM_AURA, ItemAura.DIRECT_CODEC);
        register(event, MxtResourceKeys.ITEM_QUALITY, ItemQuality.DIRECT_CODEC);
        register(event, MxtResourceKeys.DEFAULT_QUALITY, DefaultQuality.DIRECT_CODEC);
        register(event, MxtResourceKeys.TRIGGER, TriggerRule.DIRECT_CODEC);
        register(event, MxtResourceKeys.TALISMAN, Talisman.DIRECT_CODEC);
        register(event, MxtResourceKeys.CREATURE_PROFILE, CreatureProfile.DIRECT_CODEC);
        register(event, MxtResourceKeys.CONTRACT_TYPE, ContractType.DIRECT_CODEC);
        register(event, MxtResourceKeys.SECRET_REALM, SecretRealm.DIRECT_CODEC);

        register(event, MxtResourceKeys.BLOCK_AURA, BlockAura.CODEC);
        register(event, MxtResourceKeys.CURRENCY, CurrencyValue.CODEC);
        register(event, MxtResourceKeys.ITEM_BINDING, ItemBinding.CODEC);
        register(event, MxtResourceKeys.WEAPON_BINDING, WeaponBinding.CODEC);
        register(event, MxtResourceKeys.TECHNIQUE_BINDING, TechniqueBinding.CODEC);
        register(event, MxtResourceKeys.INCOMPLETE, IncompleteMarker.CODEC);
    }

    private static <T> void register(NewRegistry event, ResourceKey<Registry<T>> key, Codec<T> codec) {
        event.dataPackRegistry(key, codec, codec);
        KEYS.add(key);
    }

    public static List<ResourceKey<? extends Registry<?>>> registries() {
        return KEYS;
    }

    public static <T> Stream<Reference<T>> holders(Provider access, ResourceKey<? extends Registry<T>> key) {
        return access.lookupOrThrow(key).listElements();
    }

    // Widened to Holder on purpose: every caller wants the entry, not the reference, and Optional is invariant so the
    // widening has to happen here rather than at each call site.
    public static <T> Optional<Holder<T>> holder(Provider access, ResourceKey<? extends Registry<T>> key, Identifier id) {
        return access.get(ResourceKey.create(key, id)).map(holder -> holder);
    }

    public static <T> Optional<T> get(Provider access, ResourceKey<? extends Registry<T>> key, Identifier id) {
        return holder(access, key, id).map(Holder::value);
    }
}
