package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A type that has something to run at a moment the runtime hands it. It is a separate interface because running is
 * not part of being active: a type that never runs anything simply does not implement it, and every caller goes
 * through the two static entries below instead of asking what class the type is.
 */
public interface AbilityEffect {
    void execute(Entity actor, FormulaContext context, @Nullable Vec3 origin);

    // One target, for the types that reach entities by something other than their own action (mxt:aura reaches by
    // radius). A type that has no one-target part answers with nothing.
    default void executeOn(Entity actor, Entity target, FormulaContext context, @Nullable Vec3 origin) {
    }

    static void run(AbilityType type, Entity actor, FormulaContext context, @Nullable Vec3 origin) {
        if (type instanceof AbilityEffect effect) effect.execute(actor, context, origin);
    }

    static void runOn(AbilityType type, Entity actor, Entity target, FormulaContext context, @Nullable Vec3 origin) {
        if (type instanceof AbilityEffect effect) effect.executeOn(actor, target, context, origin);
    }
}
