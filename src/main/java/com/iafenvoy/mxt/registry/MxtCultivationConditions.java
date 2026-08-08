package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.cultivation.CultivationCondition;
import com.iafenvoy.mxt.integration.kubejs.callback.MxtJsGameplayCallbacks;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Java-owned condition IDs for data-driven cultivation actions.
 */
public final class MxtCultivationConditions {
    public static final DeferredRegister<CultivationCondition> REGISTRY = DeferredRegister.create(MxtTypeRegistries.CULTIVATION_CONDITION, MiXianTu.MOD_ID);

    static {
        REGISTRY.register("always", () -> (entity, context) -> true);
        REGISTRY.register("js", () -> MxtJsGameplayCallbacks::testCultivation);
    }

    private MxtCultivationConditions() {
    }
}
