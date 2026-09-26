package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.ItemElements;
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
 * True when the item carries one of the listed elements, as read by {@link ItemElements}: what the weapon, item or
 * artifact definition claims for the stack, or the element named by the aura the stack stores or declares.
 */
public record ItemElementCondition(List<Either<Holder<Element>, TagKey<Element>>> elements) implements ItemCondition {
    public static final MapCodec<ItemElementCondition> CODEC = RecordCodecBuilder.<ItemElementCondition>mapCodec(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).fieldOf("elements").forGetter(ItemElementCondition::elements)
    ).apply(i, ItemElementCondition::new)).validate(ItemElementCondition::validate);

    // An empty list can never match, so it is a condition that silently never passes: refused at load.
    private static DataResult<ItemElementCondition> validate(ItemElementCondition condition) {
        return condition.elements().isEmpty()
                ? DataResult.error(() -> "mxt:item_element needs at least one element to ask about")
                : DataResult.success(condition);
    }

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        return ItemElements.of(ctx.holder().level().registryAccess(), ctx.stack()).stream().anyMatch(element -> RegistryCodecs.matches(this.elements, element));
    }

    @Override
    public @NonNull MapCodec<ItemElementCondition> codec() {
        return CODEC;
    }
}
