package com.iafenvoy.mxt.data.artifact.ability;

import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * What one capability is being asked about: whose it is, the stack that declares the artifact, and the definition
 * behind both. The stack travels with the definition because the ownership every capability reads is written on
 * the stack rather than on the definition.
 */
public record ArtifactToggleContext(LivingEntity holder, ItemStack stack, Holder<Artifact> artifact) {
    public FormulaContext formula() {
        return FormulaContext.of(this.holder);
    }
}
