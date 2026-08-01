package com.iafenvoy.mxt.registry;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.advancement.IdentifierCriterionTrigger;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Vanilla-recognised advancement criteria for major MXT lifecycle outcomes.
 */
public final class MxtCriteriaTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> REGISTRY = DeferredRegister.create(BuiltInRegistries.TRIGGER_TYPES, MiXianTu.MOD_ID);
    public static final DeferredHolder<CriterionTrigger<?>, IdentifierCriterionTrigger> BREAKTHROUGH = register("breakthrough");
    public static final DeferredHolder<CriterionTrigger<?>, IdentifierCriterionTrigger> ABILITY = register("ability");
    public static final DeferredHolder<CriterionTrigger<?>, IdentifierCriterionTrigger> ALCHEMY = register("alchemy");
    public static final DeferredHolder<CriterionTrigger<?>, IdentifierCriterionTrigger> TRIBULATION = register("tribulation");

    private MxtCriteriaTriggers() {
    }

    private static DeferredHolder<CriterionTrigger<?>, IdentifierCriterionTrigger> register(String id) {
        return REGISTRY.register(id, IdentifierCriterionTrigger::new);
    }
}
