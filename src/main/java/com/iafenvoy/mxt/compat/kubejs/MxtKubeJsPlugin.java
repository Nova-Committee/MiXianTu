package com.iafenvoy.mxt.compat.kubejs;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsCallbacks;
import com.iafenvoy.mxt.event.AbilityUseEvent;
import com.iafenvoy.mxt.event.AuraZoneEvent;
import com.iafenvoy.mxt.event.CurseApplyEvent;
import com.iafenvoy.mxt.event.ResourceConsumeEvent;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.neoforged.neoforge.common.NeoForge;

/**
 * KubeJS entry point, discovered through {@code kubejs.plugins.txt}.
 */
public final class MxtKubeJsPlugin implements KubeJSPlugin {
    @Override
    public void init() {
        MxtKubeJsEvents.installDispatcher(MxtKubeJsEventDispatcher.INSTANCE);
        registerEventForwarders();
        MiXianTu.LOGGER.info("MXT KubeJS bridge initialized");
    }

    /**
     * Installs the concrete event listeners while the optional KubeJS plugin is
     * initialized.  Keeping this here makes the lifecycle explicit and avoids
     * registering listeners lazily from script-facing API calls.
     */
    private static void registerEventForwarders() {
        // NeoForge does not permit listeners for abstract event parents.
        NeoForge.EVENT_BUS.addListener(AbilityUseEvent.Pre.class, MxtKubeJsEvents::postAbility);
        NeoForge.EVENT_BUS.addListener(AbilityUseEvent.Post.class, MxtKubeJsEvents::postAbility);
        NeoForge.EVENT_BUS.addListener(CurseApplyEvent.Pre.class, MxtKubeJsEvents::postCurse);
        NeoForge.EVENT_BUS.addListener(CurseApplyEvent.Post.class, MxtKubeJsEvents::postCurse);
        NeoForge.EVENT_BUS.addListener(ResourceConsumeEvent.Pre.class, MxtKubeJsEvents::postResource);
        NeoForge.EVENT_BUS.addListener(ResourceConsumeEvent.Post.class, MxtKubeJsEvents::postResource);
        NeoForge.EVENT_BUS.addListener(AuraZoneEvent.Enter.class, MxtKubeJsEvents::postAura);
        NeoForge.EVENT_BUS.addListener(AuraZoneEvent.Leave.class, MxtKubeJsEvents::postAura);
        NeoForge.EVENT_BUS.addListener(AuraZoneEvent.Tick.class, MxtKubeJsEvents::postAura);
        NeoForge.EVENT_BUS.addListener(AuraZoneEvent.Override.class, MxtKubeJsEvents::postAura);
    }

    @Override
    public void registerBindings(BindingRegistry registry) {
        registry.add("Mxt", MxtKubeJsBindings.INSTANCE);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(MxtKubeJsEventDispatcher.EVENTS);
    }

    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType == ScriptType.SERVER) MxtJsCallbacks.clear();
    }
}
