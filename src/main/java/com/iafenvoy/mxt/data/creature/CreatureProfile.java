package com.iafenvoy.mxt.data.creature;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Datapack profile applied to tagged creature types; it does not create an entity type.
 */
public record CreatureProfile(List<Holder<RealmStage>> realmStages, NumberProvider intelligence,
                              EntityCondition condition, Optional<Identifier> innerCore,
                              Optional<Identifier> lootTable, List<Identifier> contractTags,
                              List<Either<Holder<EntityType<?>>, TagKey<EntityType<?>>>> entityTypeTags,
                              List<Either<Holder<Element>, TagKey<Element>>> preferredAuraElements,
                              Map<Holder<Resource>, NumberProvider> minimumAura) {
    public static final Codec<CreatureProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            RealmStage.CODEC.listOf().optionalFieldOf("realm_stages", List.of()).forGetter(CreatureProfile::realmStages),
            NumberProvider.CODEC.optionalFieldOf("intelligence", new Constant(0.0D)).forGetter(CreatureProfile::intelligence),
            EntityCondition.optionalCodec("condition").forGetter(CreatureProfile::condition),
            Identifier.CODEC.optionalFieldOf("inner_core").forGetter(CreatureProfile::innerCore),
            Identifier.CODEC.optionalFieldOf("loot_table").forGetter(CreatureProfile::lootTable),
            Identifier.CODEC.listOf().optionalFieldOf("contract_tags", List.of()).forGetter(CreatureProfile::contractTags),
            RegistryCodecs.holderOrTagList(Registries.ENTITY_TYPE).optionalFieldOf("entity_type_tags", List.of()).forGetter(CreatureProfile::entityTypeTags),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("preferred_aura_elements", List.of()).forGetter(CreatureProfile::preferredAuraElements),
            CollectionCodecs.map(Resource.CODEC, NumberProvider.CODEC).optionalFieldOf("minimum_aura", Map.of()).forGetter(CreatureProfile::minimumAura)
    ).apply(i, CreatureProfile::new));
}
