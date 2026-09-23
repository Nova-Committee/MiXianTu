package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What one press is asked about: who is pressing, which ability it is, and the stack it came from when that is
 * how the ability is held. The stack travels with the ability because the ownership and the contents an item-side
 * ability reads are written on the stack rather than on the definition.
 */
public record ToggleContext(LivingEntity holder, @Nullable ItemStack carrier, Holder<Ability> ability) {
    public FormulaContext formula() {
        return FormulaContext.of(this.holder);
    }
}
