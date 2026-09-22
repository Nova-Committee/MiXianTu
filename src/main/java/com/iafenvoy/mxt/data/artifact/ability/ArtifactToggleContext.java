package com.iafenvoy.mxt.data.artifact.ability;

import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * What one capability is being asked about: whose it is, the stack that declares the artifact, and the definition
 * behind both.
 *
 * <p>The stack travels with the definition because a capability is declared by a definition but applied through
 * one particular stack: flight carries its holder through the stack it started from, the storage writes into the
 * stack's own contents, and the ownership every capability reads is written on the stack rather than on the
 * definition.</p>
 */
public record ArtifactToggleContext(LivingEntity holder, ItemStack stack, Holder<Artifact> artifact) {
    /** The holder's own numbers, which is what a capability's formulas are evaluated against. */
    public FormulaContext formula() {
        return FormulaContext.of(this.holder);
    }
}
