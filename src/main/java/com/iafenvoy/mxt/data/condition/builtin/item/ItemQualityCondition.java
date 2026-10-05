package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.data.quality.QualityRequirement;
import com.iafenvoy.mxt.runtime.item.QualityRequirements;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jspecify.annotations.NonNull;

/**
 * Asks which tier the stack resolves to, through the same order the quality gate and the tooltip read, so a
 * definition's own default tier counts as the stack's. {@code quality} is membership and {@code min_quality} is a
 * position on that tier's own chain, which is why a cross-chain minimum can never pass.
 */
public record ItemQualityCondition(QualityRequirement requirement) implements ItemCondition {
    public static final MapCodec<ItemQualityCondition> CODEC = RecordCodecBuilder.<ItemQualityCondition>mapCodec(i -> i.group(
            QualityRequirement.QUALITIES_FIELD.forGetter(condition -> condition.requirement().qualities()),
            QualityRequirement.MIN_QUALITY_FIELD.forGetter(condition -> condition.requirement().minQuality())
    ).apply(i, (qualities, minimum) -> new ItemQualityCondition(new QualityRequirement(qualities, minimum))))
            .validate(ItemQualityCondition::validate);

    // A condition asking for neither half would silently always pass, so it is refused at load.
    private static DataResult<ItemQualityCondition> validate(ItemQualityCondition condition) {
        return condition.requirement().isEmpty()
                ? DataResult.error(() -> "mxt:item_quality needs a quality list or a min_quality")
                : DataResult.success(condition);
    }

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        return QualityRequirements.test(ctx.holder().level().registryAccess(), ctx.stack(), this.requirement);
    }

    @Override
    public @NonNull MapCodec<ItemQualityCondition> codec() {
        return CODEC;
    }
}
