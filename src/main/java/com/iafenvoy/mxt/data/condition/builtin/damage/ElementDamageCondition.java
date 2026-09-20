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
 * Matches a strike by the elements it is made of: true when any element the strike belongs to is one of the
 * listed ones (entry or tag).
 *
 * <p>Where that answer comes from is the whole point. The strike's elements are read by
 * {@link DamageElements#strike(net.minecraft.world.damagesource.DamageSource)} - the damage type's claimants,
 * or the attacker's spirit roots when nobody claims it - which is the same rule the damage pipeline applies.
 * A condition therefore cannot disagree with the number the target actually lost, and it covers a hit this mod
 * never dealt as readily as its own: {@code {"type": "mxt:element", "elements": ["example:fire"]}} answers
 * for a lava tick once that element claims {@code minecraft:lava}.</p>
 */
public record ElementDamageCondition(List<Either<Holder<Element>, TagKey<Element>>> elements) implements DamageCondition {
    public static final MapCodec<ElementDamageCondition> CODEC = RecordCodecBuilder.<ElementDamageCondition>mapCodec(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).fieldOf("elements").forGetter(ElementDamageCondition::elements)
    ).apply(i, ElementDamageCondition::new)).validate(ElementDamageCondition::validate);

    /**
     * An empty list can never match, so it is a condition that silently never passes: refused at load.
     */
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
