package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.data.context.condition.ItemConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.NonNull;

/**
 * Tests whether an item stack has a particular data component.
 */
public record HasComponentCondition(Holder<DataComponentType<?>> component) implements ItemCondition {
    public static final MapCodec<HasComponentCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryFixedCodec.create(Registries.DATA_COMPONENT_TYPE).fieldOf("component").forGetter(HasComponentCondition::component)
    ).apply(i, HasComponentCondition::new));

    @Override
    public boolean test(@NonNull ItemConditionContext ctx) {
        Entity holder = ctx.holder();
        ItemStack stack = ctx.stack();
        FormulaContext context = ctx.formula();
        return stack.has(this.component.value());
    }

    @Override
    public @NonNull MapCodec<HasComponentCondition> codec() {
        return CODEC;
    }
}
