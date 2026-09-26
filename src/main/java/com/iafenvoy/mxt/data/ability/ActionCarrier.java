package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.iafenvoy.mxt.data.context.action.EntityActionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The four action fields and what running them means, shared by every type that carries them. The type owns the
 * moment - a press, a signal, a pulse, a cadence - and this is only the part that is the same wherever it happens:
 * the caster's action first, then one bi-entity action per selected target, with one failing action never stopping
 * the rest.
 */
public interface ActionCarrier extends AbilityEffect {
    EntityAction entityAction();

    TargetSelector targetSelector();

    BiEntityCondition targetCondition();

    BiEntityAction biEntityAction();

    @Override
    default void execute(Entity actor, FormulaContext context, @Nullable Vec3 origin) {
        try {
            this.entityAction().execute(new EntityActionContext(actor, context, origin));
        } catch (RuntimeException exception) {
            MiXianTu.LOGGER.error("Ability entity action failed", exception);
        }
        try {
            this.targetSelector().select(actor, context, origin)
                    .forEach(target -> this.executeOn(actor, target, context, origin));
        } catch (RuntimeException exception) {
            MiXianTu.LOGGER.error("Ability target selection failed", exception);
        }
    }

    // The place travels with the activation: a bi-entity action that moves an endpoint to "the actor" moves it to
    // where the ability happened - a stand's ward pulls to the stand.
    @Override
    default void executeOn(Entity actor, Entity target, FormulaContext context, @Nullable Vec3 origin) {
        try {
            FormulaContext targetContext = actor instanceof LivingEntity caster && target instanceof LivingEntity livingTarget
                    ? FormulaContexts.forEntities(caster, livingTarget, context) : context;
            if (this.targetCondition().test(actor, target, targetContext))
                this.biEntityAction().execute(actor, target, new BiEntityActionContext(actor, target, targetContext, origin));
        } catch (RuntimeException exception) {
            MiXianTu.LOGGER.error("Ability target action failed", exception);
        }
    }
}
