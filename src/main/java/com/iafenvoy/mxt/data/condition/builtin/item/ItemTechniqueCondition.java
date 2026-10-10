package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.data.item.TechniqueBinding;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.item.ItemBindingService;
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
 * True when the technique the stack teaches is one of the listed ones, asked through the same reading the learning
 * path takes: the stack's own {@code mxt:technique} component, or a {@code technique_binding} declaration that claims
 * the item, which teaches that technique without any component. An entry may be an id or a {@code #tag}.
 */
public record ItemTechniqueCondition(
        List<Either<Holder<Technique>, TagKey<Technique>>> techniques) implements ItemCondition {
    public static final MapCodec<ItemTechniqueCondition> CODEC = RecordCodecBuilder.<ItemTechniqueCondition>mapCodec(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.TECHNIQUE).fieldOf("techniques").forGetter(ItemTechniqueCondition::techniques)
    ).apply(i, ItemTechniqueCondition::new)).validate(ItemTechniqueCondition::validate);

    // An empty list can never match, so it is a condition that silently never passes: refused at load.
    private static DataResult<ItemTechniqueCondition> validate(ItemTechniqueCondition condition) {
        return condition.techniques().isEmpty()
                ? DataResult.error(() -> "mxt:item_technique needs at least one technique to ask about")
                : DataResult.success(condition);
    }

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        return ItemBindingService.technique(ctx.holder().level().registryAccess(), ctx.stack())
                .map(TechniqueBinding::technique)
                .map(taught -> RegistryCodecs.matches(this.techniques, taught))
                .orElse(false);
    }

    @Override
    public @NonNull MapCodec<ItemTechniqueCondition> codec() {
        return CODEC;
    }
}
