package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.cultivation.SpiritRoot;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * True when the entity holds a spirit root the field names, as an entry or as a tag.
 *
 * <p>The field is a list because roots are grouped with native tags: "any of the fire roots" is one tag, and a
 * data pack that adds a root to that tag does not have to be known here. A disabled root is not held as far as
 * this condition is concerned, which is the same rule every other runtime reader of a definition follows.</p>
 */
public record HasSpiritRootEntityCondition(List<Either<Holder<SpiritRoot>, TagKey<SpiritRoot>>> spiritRoots) implements EntityCondition {
    public static final MapCodec<HasSpiritRootEntityCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.SPIRIT_ROOT).fieldOf("spirit_root").forGetter(HasSpiritRootEntityCondition::spiritRoots)
    ).apply(i, HasSpiritRootEntityCondition::new));

    @Override
    public boolean test(@NonNull EntityConditionContext ctx) {
        return ctx.entity().getData(MxtAttachments.SPIRIT_IDENTITY).spiritRoots().stream()
                .filter(held -> !MxtDatapackRegistries.isDisabled(MxtResourceKeys.SPIRIT_ROOT, held))
                .anyMatch(held -> RegistryCodecs.matches(this.spiritRoots, held));
    }

    @Override
    public @NonNull MapCodec<HasSpiritRootEntityCondition> codec() {
        return CODEC;
    }
}
