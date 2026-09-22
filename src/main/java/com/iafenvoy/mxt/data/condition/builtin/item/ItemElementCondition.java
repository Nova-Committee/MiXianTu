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
import java.util.Set;

/**
 * True when the item carries one of the listed elements.
 *
 * <p>The item-side counterpart of {@code mxt:has_element}, and the reading is
 * {@link ItemElements}: what the weapon, item or artifact definition claims for the stack, or - when none of
 * them declares anything - the element named by the aura the stack stores or declares. An element or an
 * element tag is accepted on both sides, so "a fire weapon" stays right when a later pack adds another way for
 * a weapon to be fire.</p>
 */
public record ItemElementCondition(List<Either<Holder<Element>, TagKey<Element>>> elements) implements ItemCondition {
    public static final MapCodec<ItemElementCondition> CODEC = RecordCodecBuilder.<ItemElementCondition>mapCodec(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.ELEMENT).fieldOf("elements").forGetter(ItemElementCondition::elements)
    ).apply(i, ItemElementCondition::new)).validate(ItemElementCondition::validate);

    /**
     * An empty list can never match, so it is a condition that silently never passes: refused at load.
     */
    private static DataResult<ItemElementCondition> validate(ItemElementCondition condition) {
        return condition.elements().isEmpty()
                ? DataResult.error(() -> "mxt:item_element needs at least one element to ask about")
                : DataResult.success(condition);
    }

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        Set<Holder<Element>> carried = ItemElements.of(ctx.holder().level().registryAccess(), ctx.stack());
        return carried.stream().anyMatch(element -> RegistryCodecs.matches(this.elements, element));
    }

    @Override
    public @NonNull MapCodec<ItemElementCondition> codec() {
        return CODEC;
    }
}
