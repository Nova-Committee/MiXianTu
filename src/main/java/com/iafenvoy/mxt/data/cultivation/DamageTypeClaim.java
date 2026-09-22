package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * One damage type an element claims, and what a strike of that type leaves behind. A claim is what makes a
 * damage type mean an element, and it is the one place where "how much does this kind of hit leave" can be said
 * per kind of hit rather than per element. Accepts a bare id/`#tag`, or an object carrying its own
 * {@code damage_attachment} - an explicitly written {@code 0} is a real answer, not "unset".
 */
public record DamageTypeClaim(Either<Holder<DamageType>, TagKey<DamageType>> type, Optional<Double> damageAttachment) {
    private static final MapCodec<DamageTypeClaim> OBJECT_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.holderOrTag(Registries.DAMAGE_TYPE).fieldOf("damage_type").forGetter(DamageTypeClaim::type),
            MiscCodecs.NON_NEGATIVE.optionalFieldOf("damage_attachment").forGetter(DamageTypeClaim::damageAttachment)
    ).apply(i, DamageTypeClaim::new));

    // Order matters: a bare string or #tag is tried as the plain form, an object as the form with its own number.
    public static final Codec<DamageTypeClaim> CODEC = Codec
            .either(RegistryCodecs.holderOrTag(Registries.DAMAGE_TYPE), OBJECT_CODEC.codec())
            .xmap(value -> value.map(type -> new DamageTypeClaim(type, Optional.empty()), Function.identity()),
                    claim -> claim.damageAttachment().isEmpty() ? Either.left(claim.type()) : Either.right(claim));

    public static DamageTypeClaim of(Either<Holder<DamageType>, TagKey<DamageType>> type) {
        return new DamageTypeClaim(type, Optional.empty());
    }

    public double attachment(double elementDefault) {
        return this.damageAttachment.orElse(elementDefault);
    }

    public boolean matches(Holder<DamageType> candidate) {
        return RegistryCodecs.matches(List.of(this.type), candidate);
    }
}
