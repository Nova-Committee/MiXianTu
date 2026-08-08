package com.iafenvoy.mxt.integration.kubejs.callback;

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
        MxtJsGameplayCallbacks.clear();
    }
}
