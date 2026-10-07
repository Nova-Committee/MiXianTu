package com.iafenvoy.mxt.data.talisman;

import com.iafenvoy.mxt.data.Talisman;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * One invocation of one inscription: who is using the carrier, where from, and which definition is being invoked.
 * The definition travels with it because a thrown carrier's effect lands later, when the projectile had to remember
 * only an id.
 */
public record TalismanUse(LivingEntity user, FormulaContext formula, Vec3 origin, Holder<Talisman> definition) {
}
