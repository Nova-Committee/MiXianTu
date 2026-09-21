package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.AttributeEntry;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * Element-independent innate or acquired physique. Intentionally has no element field.
 *
 * <p>"Intentionally" is a statement about the registry rather than a check on the file: a definition that also
 * declares an element or a spirit-root field keeps loading and that key is ignored, because the record codec
 * reads only the keys named below. Everything a physique may say is either a vanilla attribute, a granted
 * ability, a condition, an exclusive tag, a display rarity or a stacking rule - which is exactly the list
 * below.</p>
 *
 * <p>The two damage multipliers are the physique's own contribution to one strike, and they are the reason a
 * physique can be about fighting without being about elements: {@code damage_dealt_multiplier} scales what its
 * holder deals and {@code damage_taken_multiplier} scales what it receives, both element-independent, both
 * read by the two layers of the damage pipeline next to the element relations rather than instead of them.
 * Several physiques multiply together, because each one is its own source of the effect, and the defaults are
 * {@code 1} so a physique that says nothing about damage changes nothing.</p>
 */
public record Physique(List<AttributeEntry> attributeModifiers,
                       List<Either<Holder<Ability>, TagKey<Ability>>> grantedAbilities, EntityCondition holderCondition,
                       List<Identifier> exclusiveTags, String rarity, boolean allowStacking,
                       NumberProvider damageDealtMultiplier, NumberProvider damageTakenMultiplier) {
    public static final Codec<Holder<Physique>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.PHYSIQUE);

    public static final Codec<Physique> DIRECT_CODEC =
            RecordCodecBuilder.<Physique>mapCodec(i -> i.group(
                    AttributeEntry.CODEC.listOf().optionalFieldOf("attribute_modifiers", List.of()).forGetter(Physique::attributeModifiers),
                    RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("granted_abilities", List.of()).forGetter(Physique::grantedAbilities),
                    EntityCondition.optionalCodec("holder_condition").forGetter(Physique::holderCondition),
                    Identifier.CODEC.listOf().optionalFieldOf("exclusive_tags", List.of()).forGetter(Physique::exclusiveTags),
                    Codec.STRING.optionalFieldOf("rarity", "common").forGetter(Physique::rarity),
                    Codec.BOOL.optionalFieldOf("allow_stacking", false).forGetter(Physique::allowStacking),
                    NumberProvider.CODEC.optionalFieldOf("damage_dealt_multiplier", new Constant(1.0D)).forGetter(Physique::damageDealtMultiplier),
                    NumberProvider.CODEC.optionalFieldOf("damage_taken_multiplier", new Constant(1.0D)).forGetter(Physique::damageTakenMultiplier)
            ).apply(i, Physique::new)).validate(Physique::validate).codec();

    /**
     * A written number can be checked while the pack loads; a formula can only be checked when it runs, which
     * the damage pipeline does.
     */
    private static DataResult<Physique> validate(Physique physique) {
        for (NumberProvider provider : List.of(physique.damageDealtMultiplier(), physique.damageTakenMultiplier()))
            if (provider instanceof Constant(double value) && (!Double.isFinite(value) || value < 0.0D))
                return DataResult.error(() -> "A physique damage multiplier must be finite and non-negative: " + value);
        return DataResult.success(physique);
    }
}
