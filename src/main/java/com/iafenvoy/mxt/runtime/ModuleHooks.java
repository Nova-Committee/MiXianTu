package com.iafenvoy.mxt.runtime;

import com.iafenvoy.mxt.runtime.ability.AbilityEventBridge;
import com.iafenvoy.mxt.runtime.artifact.ArtifactHoldService;
import com.iafenvoy.mxt.runtime.creature.CreatureHooks;
import com.iafenvoy.mxt.runtime.cultivation.CultivationHooks;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueItemService;
import com.iafenvoy.mxt.runtime.curse.CurseTriggerSubscriptions;
import com.iafenvoy.mxt.runtime.progression.ProgressionTickStage;
import com.iafenvoy.mxt.runtime.resource.ResourceRegenStage;
import com.iafenvoy.mxt.runtime.spirit.SpiritChargeService;
import com.iafenvoy.mxt.runtime.trigger.CultivationTriggerService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a module states what it contributes. The capability interface it registers under is the key, so a new kind
 * of hook needs no registry of its own and one object may serve several of them.
 *
 * <p>Registration happens while the mod is constructed and reading happens on the game thread afterwards, so
 * {@link #all} needs no lock. {@link #initialize} is the only place the modules are listed, and that list names
 * modules rather than business rules - each module's own {@code register()} says what it contributes.
 */
public final class ModuleHooks {
    private static final Map<Class<?>, List<Object>> HOOKS = new LinkedHashMap<>();
    private static boolean initialized;

    private ModuleHooks() {
    }

    public static synchronized <T> void register(Class<T> capability, T hook) {
        HOOKS.computeIfAbsent(capability, ignored -> new ArrayList<>()).add(hook);
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> all(Class<T> capability) {
        List<Object> hooks = HOOKS.get(capability);
        return hooks == null ? List.of() : (List<T>) List.copyOf(hooks);
    }

    // Called once from the mod constructor, before anything can load a world.
    public static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        AbilityEventBridge.register();
        CultivationHooks.register();
        CreatureHooks.register();
        CultivationTriggerService.register();
        CurseTriggerSubscriptions.register();
        TechniqueItemService.register();
        SpiritChargeService.register();
        ArtifactHoldService.register();
        ResourceRegenStage.register();
        ProgressionTickStage.register();
    }
}
