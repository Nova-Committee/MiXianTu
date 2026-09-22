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
 * One damage type an element claims, and what a strike of that type leaves behind.
 *
 * <p>A claim is what makes a damage type mean an element ({@code DamageElements} reads the reverse index), and
 * it is also the one place where "how much of this element does this kind of hit leave" can be said per kind of
 * hit rather than per element. Two shapes are accepted, and they are the same claim written differently:</p>
 *
 * <pre>{@code
 * "minecraft:magic"                                        // the element's own damage_attachment decides
 * { "damage_type": "minecraft:lava", "damage_attachment": 2.0 }   // this kind of hit leaves its own amount
 * }</pre>
 *
 * <p>The plain form exists because most elements mean one number for everything they claim, and a pack that
 * writes one keeps reading exactly as it did before. The object form is how a pack says "a lava bath builds up
 * half as fast as a fireball" without splitting the element in two: the element stays one element, and the
 * damage types it claims become groups that carry their own number.</p>
 *
 * <p>{@code damage_attachment} is optional even in the object form, and an explicitly written {@code 0} is a
 * real answer rather than "unset": a group that never accumulates. The tag form ({@code "#minecraft:is_fire"})
 * is accepted in both shapes and hands the same number to every type it expands to.</p>
 */
public record DamageTypeClaim(Either<Holder<DamageType>, TagKey<DamageType>> type, Optional<Double> damageAttachment) {
    private static final MapCodec<DamageTypeClaim> OBJECT_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.holderOrTag(Registries.DAMAGE_TYPE).fieldOf("damage_type").forGetter(DamageTypeClaim::type),
            MiscCodecs.NON_NEGATIVE.optionalFieldOf("damage_attachment").forGetter(DamageTypeClaim::damageAttachment)
    ).apply(i, DamageTypeClaim::new));

    /**
     * The two shapes, in the order a JSON value is tried against them: a bare string or {@code #tag} is the
     * plain form, an object is the one that carries its own number.
     */
    public static final Codec<DamageTypeClaim> CODEC = Codec
            .either(RegistryCodecs.holderOrTag(Registries.DAMAGE_TYPE), OBJECT_CODEC.codec())
            .xmap(value -> value.map(type -> new DamageTypeClaim(type, Optional.empty()), Function.identity()),
                    claim -> claim.damageAttachment().isEmpty() ? Either.left(claim.type()) : Either.right(claim));

    /**
     * The claim written the way that says the least: no number of its own, so the element's {@code
     * damage_attachment} answers.
     */
    public static DamageTypeClaim of(Either<Holder<DamageType>, TagKey<DamageType>> type) {
        return new DamageTypeClaim(type, Optional.empty());
    }

    /**
     * What one strike of this claimed type leaves on the target: this claim's own number when it wrote one, and
     * the element's {@code damage_attachment} otherwise.
     */
    public double attachment(double elementDefault) {
        return this.damageAttachment.orElse(elementDefault);
    }

    /**
     * Whether this claim is about the given damage type, tags included.
     */
    public boolean matches(Holder<DamageType> candidate) {
        return RegistryCodecs.matches(List.of(this.type), candidate);
    }
}
