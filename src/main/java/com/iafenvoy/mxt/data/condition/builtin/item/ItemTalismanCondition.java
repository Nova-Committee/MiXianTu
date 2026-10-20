package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.data.talisman.Talisman;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.talisman.TalismanService;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * True when one of the listed talismans is inscribed on the carrier: a carrier carries a single inscription, so the
 * question is whether that one is among these rather than whether the stack is one of them. Read through
 * {@link TalismanService}, so a stack carrying no {@code mxt:talisman} component is a blank carrier and answers no.
 */
public record ItemTalismanCondition(
        List<Either<Holder<Talisman>, TagKey<Talisman>>> talismans) implements ItemCondition {
    public static final MapCodec<ItemTalismanCondition> CODEC = RecordCodecBuilder.<ItemTalismanCondition>mapCodec(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.TALISMAN).fieldOf("talismans").forGetter(ItemTalismanCondition::talismans)
    ).apply(i, ItemTalismanCondition::new)).validate(ItemTalismanCondition::validate);

    // An empty list can never match, so it is a condition that silently never passes: refused at load.
    private static DataResult<ItemTalismanCondition> validate(ItemTalismanCondition condition) {
        return condition.talismans().isEmpty()
                ? DataResult.error(() -> "mxt:item_talisman needs at least one talisman to ask about")
                : DataResult.success(condition);
    }

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        Holder<Talisman> inscribed = TalismanService.inscribed(ctx.stack());
        return inscribed != null && RegistryCodecs.matches(this.talismans, inscribed);
    }

    @Override
    public @NonNull MapCodec<ItemTalismanCondition> codec() {
        return CODEC;
    }
}
