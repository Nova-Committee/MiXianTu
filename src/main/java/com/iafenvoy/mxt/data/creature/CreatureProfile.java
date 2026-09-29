package com.iafenvoy.mxt.data.creature;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.progression.Progression;
import com.iafenvoy.mxt.data.progression.ProgressionConfig;
import com.iafenvoy.mxt.data.progression.ProgressionOwner;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Datapack profile applied to tagged creature types; it does not create an entity type. Extra drops are vanilla
 * loot tables, and the core is a {@link ItemStackTemplate} because registries parse before item components bind.
 * It is also a progression owner: a profile that names a chain gives every creature wearing it that chain, and
 * its entry level's abilities are what the creature is born with.
 */
public record CreatureProfile(List<Either<Holder<EntityType<?>>, TagKey<EntityType<?>>>> entities,
                              EntityAction spawnAction, NumberProvider intelligence,
                              EntityCondition condition, Optional<ItemStackTemplate> innerCore,
                              List<Either<Holder<Element>, TagKey<Element>>> preferredAuraElements,
                              Map<Holder<Aura>, NumberProvider> minimumAura,
                              Optional<Holder<Progression>> defaultLevel,
                              Optional<Holder<Resource>> masteryResource,
                              Map<Holder<Progression>, ProgressionConfig> configuration)
        implements ProgressionOwner {
    public static final Codec<CreatureProfile> CODEC = RecordCodecBuilder.<CreatureProfile>create(i -> i.group(
            RegistryCodecs.holderOrTagList(Registries.ENTITY_TYPE).optionalFieldOf("entities", List.of()).forGetter(CreatureProfile::entities),
            EntityAction.SINGLE_CODEC.optionalFieldOf("spawn_action", NoOpAction.INSTANCE).forGetter(CreatureProfile::spawnAction),
            NumberProvider.CODEC.optionalFieldOf("intelligence", new Constant(0.0D)).forGetter(CreatureProfile::intelligence),
            EntityCondition.optionalCodec("condition").forGetter(CreatureProfile::condition),
            ItemStackTemplate.CODEC.optionalFieldOf("inner_core").forGetter(CreatureProfile::innerCore),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("preferred_aura_elements", List.of()).forGetter(CreatureProfile::preferredAuraElements),
            CollectionCodecs.map(Aura.CODEC, NumberProvider.CODEC).optionalFieldOf("minimum_aura", Map.of()).forGetter(CreatureProfile::minimumAura),
            Progression.CODEC.optionalFieldOf("default_level").forGetter(CreatureProfile::defaultLevel),
            Resource.CODEC.optionalFieldOf("mastery_resource").forGetter(CreatureProfile::masteryResource),
            Codec.unboundedMap(Progression.CODEC, ProgressionConfig.CODEC)
                    .optionalFieldOf("configuration", Map.of()).forGetter(CreatureProfile::configuration)
    ).apply(i, CreatureProfile::new)).validate(CreatureProfile::validate);

    private static DataResult<CreatureProfile> validate(CreatureProfile profile) {
        if (profile.defaultLevel().isEmpty() && !profile.configuration().isEmpty())
            return DataResult.error(() -> "configuration needs default_level to name the progression chain it belongs to");
        if (profile.defaultLevel().isEmpty() && profile.masteryResource().isPresent())
            return DataResult.error(() -> "mastery_resource needs default_level to name the progression chain it measures");
        return DataResult.success(profile);
    }

    @Override
    public Optional<Holder<Progression>> entryLevel() {
        return this.defaultLevel;
    }

    @Override
    public Map<Holder<Progression>, ProgressionConfig> levels() {
        return this.configuration;
    }
}
