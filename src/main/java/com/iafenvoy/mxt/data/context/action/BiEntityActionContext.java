package com.iafenvoy.mxt.data.context.action;

import com.iafenvoy.mxt.data.context.Context;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * An action about to run for one pair of entities, and the place it happens at when that is not the actor's
 * own - the same arrangement {@link EntityActionContext} makes, carried as the same extension data.
 */
public class BiEntityActionContext extends Context {
    private final Entity actor;
    private final Entity target;
    private final FormulaContext formula;

    public BiEntityActionContext(Entity actor, Entity target, FormulaContext formula) {
        this(actor, target, formula, null);
    }

    public BiEntityActionContext(Entity actor, Entity target, FormulaContext formula, @Nullable Vec3 origin) {
        this.actor = actor;
        this.target = target;
        this.formula = formula;
        this.origin(origin);
    }

    public Entity actor() {
        return this.actor;
    }

    public Entity target() {
        return this.target;
    }

    /**
     * Where this action happens: the place it was given, or the acting entity's own position. An action that
     * moves an endpoint "to the actor" moves it here.
     */
    public Vec3 position() {
        return this.origin().orElseGet(this.actor::position);
    }

    @Override
    public FormulaContext formula() {
        return this.formula;
    }
}
