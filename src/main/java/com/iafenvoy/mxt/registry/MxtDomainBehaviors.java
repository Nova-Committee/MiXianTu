package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.behavior.DomainBehavior;
import net.minecraft.core.Registry;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Java-only lifecycle behaviour IDs, analogous to vanilla's code-owned loot extension points.
 */
public final class MxtDomainBehaviors {
    public static final DeferredRegister<DomainBehavior> FORGING = register(MxtTypeRegistries.FORGING_COMPLETION_BEHAVIOR);
    public static final DeferredRegister<DomainBehavior> FORMATION = register(MxtTypeRegistries.FORMATION_LIFECYCLE_BEHAVIOR);
    public static final DeferredRegister<DomainBehavior> TRIBULATION = register(MxtTypeRegistries.TRIBULATION_STAGE_BEHAVIOR);
    public static final DeferredRegister<DomainBehavior> CULTIVATION = register(MxtTypeRegistries.CULTIVATION_OUTCOME_BEHAVIOR);
    public static final DeferredRegister<DomainBehavior> CONTRACT = register(MxtTypeRegistries.CONTRACT_LIFECYCLE_BEHAVIOR);
    public static final DeferredRegister<DomainBehavior> ALCHEMY = register(MxtTypeRegistries.ALCHEMY_OUTCOME_BEHAVIOR);
    public static final DeferredRegister<DomainBehavior> REALM = register(MxtTypeRegistries.REALM_LIFECYCLE_BEHAVIOR);
    public static final DeferredRegister<DomainBehavior> ARTIFACT = register(MxtTypeRegistries.ARTIFACT_LIFECYCLE_BEHAVIOR);

    private MxtDomainBehaviors() {
    }

    private static DeferredRegister<DomainBehavior> register(Registry<DomainBehavior> registry) {
        DeferredRegister<DomainBehavior> result = DeferredRegister.create(registry, MiXianTu.MOD_ID);
        result.register("no_op", () -> _ -> {
        });
        return result;
    }
}
