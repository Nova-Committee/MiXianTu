package com.iafenvoy.mxt.data.condition.builtin.damage;

import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.context.condition.DamageConditionContext;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.damage.DamageElements;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Set;

/**
 * True when any element the strike is made of - {@link DamageElements#strike}: the damage type's claimants, or the
 * attacker's spirit roots when nobody claims it - is one of the listed entries or tags.
 */
public record ElementDamageCondition(
        List<Either<Holder<Element>, TagKey<Element>>> elements) implements DamageCondition {
    public static final MapCodec<ElementDamageCondition> CODEC = RecordCodecBuilder.<ElementDamageCondition>mapCodec(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).fieldOf("elements").forGetter(ElementDamageCondition::elements)
    ).apply(i, ElementDamageCondition::new)).validate(ElementDamageCondition::validate);

    // An empty list can never match, so it is a condition that silently never passes: refused at load.
    private static DataResult<ElementDamageCondition> validate(ElementDamageCondition condition) {
        return condition.elements().isEmpty()
                ? DataResult.error(() -> "mxt:element needs at least one element to ask about")
                : DataResult.success(condition);
    }

    @Override
    public boolean test(@NonNull DamageConditionContext ctx) {
        Set<Holder<Element>> strike = DamageElements.strike(ctx.source());
        return strike.stream().anyMatch(element -> RegistryCodecs.matches(this.elements, element));
    }

    @Override
    public @NonNull MapCodec<ElementDamageCondition> codec() {
        return CODEC;
    }
}
