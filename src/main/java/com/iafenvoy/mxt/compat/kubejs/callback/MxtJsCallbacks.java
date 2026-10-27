package com.iafenvoy.mxt.compat.kubejs.callback;

import com.iafenvoy.saturn.compat.kubejs.SaturnJsWarnings;
import com.iafenvoy.saturn.compat.kubejs.callback.*;

/**
 * Coordinates KubeJS callback invalidation during script reloads.
 */
public final class MxtJsCallbacks {
    private MxtJsCallbacks() {
    }

    public static void clear() {
        SaturnJsActionCallbacks.clear();
        SaturnJsConditionCallbacks.clear();
        SaturnJsValueCallbacks.clear();
        MxtJsTriggerCallbacks.clear();
        SaturnJsTriggerMatchers.clear();
        SaturnJsCostCallbacks.clear();
        SaturnJsSelectorCallbacks.clear();
        MxtJsLootCallbacks.clear();
        SaturnJsWarnings.clear();
    }
}
