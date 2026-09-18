package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Map;

/**
 * One inscribed talisman a carrier can hold. {@code abilities} is what invoking it grants, and it is the only
 * effect field: every skill-like effect in this mod already lands on an {@link Ability}, so a talisman needs no
 * effect vocabulary of its own.
 * <p>
 * {@code aura_cost} is the aura bill invoking it pays, one entry per aura with a {@link NumberProvider}
 * amount, so a cost may be a constant or a formula over the invoking context. Auras are pooled one value per
 * concrete aura, which is why this map takes ids only and not the tags {@code abilities} accepts. An absent or
 * empty map is a talisman that costs nothing.
 * <p>
 * The bill is also what the carrier has to be filled with before it fires: pouring a talisman full is how its
 * invocation is loaded, one aura at a time in the order they are written here, and the moment the last of it
 * arrives the carrier spends itself. See {@code runtime/talisman} for the invocation, and
 * {@code docs/数据包格式.md} for what a carrier does with the field.
 * <p>
 * There is deliberately no field here for whether the carrier fires on its own: that belongs to the stack, which
 * is what {@code mxt:talisman}'s mode carries. The same inscriptions can be written onto one carrier that fires
 * the moment it is full and another that waits to be told, so the choice is the player's rather than the
 * definition's.
 * <p>
 * Further fields (charges, a use condition) are added as optional fields, which keeps the files written today
 * parsing. The display name is deliberately not a field: like every other definition it resolves from the entry
 * id through {@link com.iafenvoy.mxt.util.DefinitionText}, as {@code talisman.<namespace>.<path>}.
 */
public record Talisman(List<Either<Holder<Ability>, TagKey<Ability>>> abilities,
                       Map<Holder<Aura>, NumberProvider> auraCost) {
    public static final Codec<Holder<Talisman>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.TALISMAN);
    public static final Codec<Talisman> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ABILITY).optionalFieldOf("abilities", List.of()).forGetter(Talisman::abilities),
            CollectionCodecs.map(Aura.CODEC, NumberProvider.CODEC).optionalFieldOf("aura_cost", Map.of()).forGetter(Talisman::auraCost)
    ).apply(i, Talisman::new));
}
