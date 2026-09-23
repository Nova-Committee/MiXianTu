package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * Element-independent innate or acquired physique. Intentionally has no element field: a definition that also
 * declares one or a spirit-root field keeps loading and that key is silently ignored, because the record codec
 * reads only the keys named below. The two damage multipliers are read by the damage pipeline next to the element
 * relations rather than instead of them; several physiques multiply together, and both default to 1.
 */
public record Physique(Component name, Component description, List<AttributeEntry> attributeModifiers,
                       List<Either<Holder<Ability>, TagKey<Ability>>> grantedAbilities, EntityCondition holderCondition,
                       List<Identifier> exclusiveTags, String rarity, boolean allowStacking,
                       NumberProvider damageDealtMultiplier, NumberProvider damageTakenMultiplier)
        implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.PHYSIQUE.identifier());
    public static final Codec<Holder<Physique>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.PHYSIQUE);

    public static final Codec<Physique> DIRECT_CODEC =
            RecordCodecBuilder.<Physique>mapCodec(i -> i.group(
                    ContextNameCodec.name(CATEGORY).forGetter(Physique::name),
                    ContextNameCodec.description(CATEGORY).forGetter(Physique::description),
                    AttributeEntry.CODEC.listOf().optionalFieldOf("attribute_modifiers", List.of()).forGetter(Physique::attributeModifiers),
                    RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(Physique::grantedAbilities),
                    EntityCondition.optionalCodec("holder_condition").forGetter(Physique::holderCondition),
                    Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(Physique::exclusiveTags),
                    Codec.STRING.optionalFieldOf("rarity", "common").forGetter(Physique::rarity),
                    Codec.BOOL.optionalFieldOf("allow_stacking", false).forGetter(Physique::allowStacking),
                    NumberProvider.CODEC.optionalFieldOf("damage_dealt_multiplier", new Constant(1.0D)).forGetter(Physique::damageDealtMultiplier),
                    NumberProvider.CODEC.optionalFieldOf("damage_taken_multiplier", new Constant(1.0D)).forGetter(Physique::damageTakenMultiplier)
            ).apply(i, Physique::new)).validate(Physique::validate).codec();

    // A written number is checked here; a formula only when it runs, which the damage pipeline does.
    private static DataResult<Physique> validate(Physique physique) {
        for (NumberProvider provider : List.of(physique.damageDealtMultiplier(), physique.damageTakenMultiplier()))
            if (provider instanceof Constant(double value) && (!Double.isFinite(value) || value < 0.0D))
                return DataResult.error(() -> "A physique damage multiplier must be finite and non-negative: " + value);
        return DataResult.success(physique);
    }
}
