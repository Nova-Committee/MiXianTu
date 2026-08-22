package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.creature.CreatureSpawnCondition;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsGameplayCallbacks;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Java-owned spawn predicates selected by creature-profile IDs.
 */
public final class MxtCreatureSpawnConditions {
    public static final DeferredRegister<CreatureSpawnCondition> REGISTRY = DeferredRegister.create(MxtRegistries.CREATURE_SPAWN_CONDITION, MiXianTu.MOD_ID);

    static {
        REGISTRY.register("always", () -> (creature, context) -> true);
        REGISTRY.register("js", () -> MxtJsGameplayCallbacks::testCreatureSpawn);
    }

    private MxtCreatureSpawnConditions() {
    }
}
