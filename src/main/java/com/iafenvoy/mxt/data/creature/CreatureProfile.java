package com.iafenvoy.mxt.data.creature;

import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.iafenvoy.mxt.data.cultivation.RealmStage;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.runtime.creature.CreatureSpawnCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import java.util.List;
import java.util.Optional;

/**
 * Datapack profile applied to tagged creature types; it does not create an entity type.
 */
public record CreatureProfile(Optional<Holder<RealmStage>> realmStage, NumberProvider intelligence,
                              List<CreatureSpawnCondition> spawnConditions, Optional<Identifier> innerCore,
                              Optional<Identifier> lootTable, List<Identifier> contractTags,
                              List<Either<Holder<EntityType<?>>, TagKey<EntityType<?>>>> entityTypeTags,
                              List<Either<Holder<Element>, TagKey<Element>>> preferredAuraElements,
                              double minimumAura) {
    public static final Codec<CreatureProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            RealmStage.CODEC.optionalFieldOf("realm_stage").forGetter(CreatureProfile::realmStage),
            NumberProvider.CODEC.optionalFieldOf("intelligence", new Constant(0.0D)).forGetter(CreatureProfile::intelligence),
            AutoIgnoreListCodec.create(MxtTypeRegistries.CREATURE_SPAWN_CONDITION.byNameCodec()).optionalFieldOf("spawn_conditions", List.of()).forGetter(CreatureProfile::spawnConditions),
            Identifier.CODEC.optionalFieldOf("inner_core").forGetter(CreatureProfile::innerCore),
            Identifier.CODEC.optionalFieldOf("loot_table").forGetter(CreatureProfile::lootTable),
            Identifier.CODEC.listOf().optionalFieldOf("contract_tags", List.of()).forGetter(CreatureProfile::contractTags),
            RegistryCodecs.holderOrTagList(Registries.ENTITY_TYPE).optionalFieldOf("entity_type_tags", List.of()).forGetter(CreatureProfile::entityTypeTags),
            RegistryCodecs.holderOrTagList(MxtRegistryKeys.ELEMENT).optionalFieldOf("preferred_aura_elements", List.of()).forGetter(CreatureProfile::preferredAuraElements),
            Codec.DOUBLE.optionalFieldOf("minimum_aura", 0.0D).forGetter(CreatureProfile::minimumAura)
    ).apply(instance, CreatureProfile::new));
}
