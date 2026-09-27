package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
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
 * Asks which tier the stack resolves to, through the same order the quality gate and the tooltip read, so a
 * definition's own default tier counts as the stack's.
 */
public record ItemQualityCondition(List<Either<Holder<ItemQuality>, TagKey<ItemQuality>>> qualities) implements ItemCondition {
    public static final MapCodec<ItemQualityCondition> CODEC = RecordCodecBuilder.<ItemQualityCondition>mapCodec(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ITEM_QUALITY).fieldOf("quality").forGetter(ItemQualityCondition::qualities)
    ).apply(i, ItemQualityCondition::new)).validate(ItemQualityCondition::validate);

    // An empty list can never match, so it is a condition that silently never passes: refused at load.
    private static DataResult<ItemQualityCondition> validate(ItemQualityCondition condition) {
        return condition.qualities().isEmpty()
                ? DataResult.error(() -> "mxt:item_quality needs at least one quality to ask about")
                : DataResult.success(condition);
    }

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        return ItemQualityService.find(ctx.holder().level().registryAccess(), ctx.stack())
                .map(quality -> RegistryCodecs.matches(this.qualities, quality)).orElse(false);
    }

    @Override
    public @NonNull MapCodec<ItemQualityCondition> codec() {
        return CODEC;
    }
}
