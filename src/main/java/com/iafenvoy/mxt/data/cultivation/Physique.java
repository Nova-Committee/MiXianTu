package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.AttributeModifier;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.runtime.cultivation.CultivationCondition;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * Element-independent innate or acquired physique. Intentionally has no element field.
 */
public record Physique(List<AttributeModifier> attributeModifiers,
                       List<Either<Holder<Ability>, TagKey<Ability>>> grantedAbilities,
                       List<CultivationCondition> holderConditions, List<Identifier> exclusiveTags, String rarity,
                       boolean allowStacking) {
    public static final Codec<Physique> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AttributeModifier.CODEC.listOf().optionalFieldOf("attribute_modifiers", List.of()).forGetter(Physique::attributeModifiers),
            RegistryCodecs.holderOrTagList(MxtRegistryKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(Physique::grantedAbilities),
            AutoIgnoreListCodec.create(MxtTypeRegistries.CULTIVATION_CONDITION.byNameCodec()).optionalFieldOf("holder_conditions", List.of()).forGetter(Physique::holderConditions),
            Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(Physique::exclusiveTags),
            Codec.STRING.optionalFieldOf("rarity", "common").forGetter(Physique::rarity),
            Codec.BOOL.optionalFieldOf("allow_stacking", false).forGetter(Physique::allowStacking)
    ).apply(instance, Physique::new));
}
