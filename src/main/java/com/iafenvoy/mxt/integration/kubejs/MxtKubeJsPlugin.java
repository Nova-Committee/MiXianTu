package com.iafenvoy.mxt.integration.kubejs;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.integration.MxtKubeJsEvents;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;

/**
 * KubeJS entry point, discovered through {@code kubejs.plugins.txt}.
 */
public final class MxtKubeJsPlugin implements KubeJSPlugin {
    @Override
    public void init() {
        MxtKubeJsEvents.installDispatcher(MxtKubeJsEventDispatcher.INSTANCE);
        MiXianTu.LOGGER.info("MXT KubeJS bridge initialized");
    }

    @Override
    public void registerBindings(BindingRegistry registry) {
        registry.add("Mxt", MxtKubeJsBindings.INSTANCE);
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(MxtKubeJsEventDispatcher.EVENTS);
    }
}
