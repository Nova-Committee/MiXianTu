package com.iafenvoy.mxt.compat.kubejs.binding;

import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsGameplayCallbacks;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.BiPredicate;

/**
 * KubeJS registrations for direct-ID server gameplay extension points.
 */
public final class MxtKubeJsGameplayBindings {
    @Info("Registers mxt:js for cultivation conditions")
    public void cultivationCondition(BiPredicate<LivingEntity, FormulaContext> callback) {
        MxtJsGameplayCallbacks.registerCultivation(callback);
    }

}
