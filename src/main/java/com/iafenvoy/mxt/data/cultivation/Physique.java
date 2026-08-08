package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.AttributeModifier;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.AlwaysTrueEntityCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * Element-independent innate or acquired physique. Intentionally has no element field.
 */
public record Physique(List<AttributeModifier> attributeModifiers,
                       List<Either<Holder<Ability>, TagKey<Ability>>> grantedAbilities, EntityCondition holderCondition,
                       List<Identifier> exclusiveTags, String rarity, boolean allowStacking) {
    public static final Codec<Holder<Physique>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.PHYSIQUE);
    public static final Codec<Physique> DIRECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AttributeModifier.CODEC.listOf().optionalFieldOf("attribute_modifiers", List.of()).forGetter(Physique::attributeModifiers),
            RegistryCodecs.holderOrTagList(MxtRegistryKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(Physique::grantedAbilities),
            EntityCondition.CODEC.optionalFieldOf("holder_condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(Physique::holderCondition),
            Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(Physique::exclusiveTags),
            Codec.STRING.optionalFieldOf("rarity", "common").forGetter(Physique::rarity),
            Codec.BOOL.optionalFieldOf("allow_stacking", false).forGetter(Physique::allowStacking)
    ).apply(instance, Physique::new));
}
