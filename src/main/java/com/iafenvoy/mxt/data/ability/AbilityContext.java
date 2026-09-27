package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.attachment.AbilityAttachment;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;

/**
 * What a type's lifecycle hooks are handed: the holder the ability is granted to, everything the runtime already
 * looked up for this pass, and the tick it is running on. A hook that needs the definition reads it from the holder.
 */
public record AbilityContext(LivingEntity holder, Holder<Ability> ability, AbilityAttachment abilities,
                             ResourceHolderAttachment resources, FormulaContext formula, long gameTime) {
    public Ability definition() {
        return this.ability.value();
    }
}
