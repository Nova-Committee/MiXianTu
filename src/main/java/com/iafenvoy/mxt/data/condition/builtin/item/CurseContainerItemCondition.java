package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.action.builtin.entity.ApplyCurseAction;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.data.curse.CurseContainerComponent;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.codec.RegistryCodecs;
import com.iafenvoy.mxt.util.formula.NumberRange;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Optional;

/**
 * Asks which curses the stack carries in its {@code mxt:curse_container}, or whether it carries any when the
 * list is empty. Asks the component rather than the holder, so an unequipped container still answers.
 */
public record CurseContainerItemCondition(List<Either<Holder<Curse>, TagKey<Curse>>> curses,
                                          Optional<NumberRange> stacks) implements ItemCondition {
    public static final MapCodec<CurseContainerItemCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryCodecs.holderOrTagList(MxtResourceKeys.CURSE).optionalFieldOf("curse", List.of()).forGetter(CurseContainerItemCondition::curses),
            NumberRange.CODEC.optionalFieldOf("stacks").forGetter(CurseContainerItemCondition::stacks)
    ).apply(i, CurseContainerItemCondition::new));

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        CurseContainerComponent container = ctx.stack().get(MxtDataComponents.CURSE_CONTAINER.get());
        if (container == null) return false;
        for (ApplyCurseAction action : container.curses()) {
            if (!this.curses.isEmpty() && !RegistryCodecs.matches(this.curses, action.curse())) continue;
            // The window is read on the stacks the entry would apply, which is a formula the same context resolves.
            if (this.stacks.map(range -> range.test(action.stacks().evaluate(ctx.formula()), ctx.formula())).orElse(true))
                return true;
        }
        return false;
    }

    @Override
    public @NonNull MapCodec<CurseContainerItemCondition> codec() {
        return CODEC;
    }
}
