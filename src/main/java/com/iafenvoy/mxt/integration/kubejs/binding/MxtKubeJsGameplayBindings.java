package com.iafenvoy.mxt.integration.kubejs.binding;

import com.iafenvoy.mxt.integration.kubejs.callback.MxtJsGameplayCallbacks;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.function.BiPredicate;

/**
 * KubeJS registrations for direct-ID server gameplay extension points.
 */
public final class MxtKubeJsGameplayBindings {
    @Info("Registers mxt:js for creature spawn conditions")
    public void creatureSpawn(BiPredicate<Mob, FormulaContext> callback) {
        MxtJsGameplayCallbacks.registerCreatureSpawn(callback);
    }

    @Info("Registers mxt:js for cultivation conditions")
    public void cultivationCondition(BiPredicate<LivingEntity, FormulaContext> callback) {
        MxtJsGameplayCallbacks.registerCultivation(callback);
    }

}
