package com.iafenvoy.mxt.data.creature;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.NoOpAction;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.Element;
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
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Datapack profile applied to tagged creature types; it does not create an entity type. Extra drops are vanilla
 * loot tables, so the profile only contributes the inner core stack and the single action run when it is written.
 */
public record CreatureProfile(List<Either<Holder<EntityType<?>>, TagKey<EntityType<?>>>> entities,
                              EntityAction spawnAction, NumberProvider intelligence,
                              EntityCondition condition, Optional<ItemStack> innerCore,
                              List<Either<Holder<Element>, TagKey<Element>>> preferredAuraElements,
                              Map<Holder<Aura>, NumberProvider> minimumAura) {
    public static final Codec<CreatureProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.holderOrTagList(Registries.ENTITY_TYPE).optionalFieldOf("entities", List.of()).forGetter(CreatureProfile::entities),
            EntityAction.SINGLE_CODEC.optionalFieldOf("spawn_action", NoOpAction.INSTANCE).forGetter(CreatureProfile::spawnAction),
            NumberProvider.CODEC.optionalFieldOf("intelligence", new Constant(0.0D)).forGetter(CreatureProfile::intelligence),
            EntityCondition.optionalCodec("condition").forGetter(CreatureProfile::condition),
            ItemStack.CODEC.optionalFieldOf("inner_core").forGetter(CreatureProfile::innerCore),
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).optionalFieldOf("preferred_aura_elements", List.of()).forGetter(CreatureProfile::preferredAuraElements),
            CollectionCodecs.map(Aura.CODEC, NumberProvider.CODEC).optionalFieldOf("minimum_aura", Map.of()).forGetter(CreatureProfile::minimumAura)
    ).apply(i, CreatureProfile::new));
}
