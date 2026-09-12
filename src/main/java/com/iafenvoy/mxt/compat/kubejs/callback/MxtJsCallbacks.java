package com.iafenvoy.mxt.compat.kubejs.callback;

import com.iafenvoy.mxt.compat.kubejs.MxtJsWarnings;

/**
 * Coordinates KubeJS callback invalidation during script reloads.
 */
public final class MxtJsCallbacks {
    private MxtJsCallbacks() {
    }

    public static void clear() {
        MxtJsActionCallbacks.clear();
        MxtJsConditionCallbacks.clear();
        MxtJsValueCallbacks.clear();
        MxtJsTriggerCallbacks.clear();
        MxtJsTriggerMatchers.clear();
        MxtJsCostCallbacks.clear();
        MxtJsSelectorCallbacks.clear();
        MxtJsLootCallbacks.clear();
        MxtJsWarnings.clear();
    }
}
