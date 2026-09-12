package com.iafenvoy.mxt.compat.kubejs.callback;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.trigger.TriggerSignal;
import com.iafenvoy.mxt.util.formula.FormulaContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Callback storage for the JavaScript trigger matcher type.
 *
 * <p>A trigger matcher only decides whether a signal that already reached the owning subscription
 * should fire it, so it runs on the server and receives the signal, whose context carries the same
 * payload values a built-in trigger would read.</p>
 */
public final class MxtJsTriggerMatchers {
    private static final Map<String, TriPredicate<TriggerSignal, JsonObject, FormulaContext>> MATCHERS = new ConcurrentHashMap<>();

    private MxtJsTriggerMatchers() {
    }

    public static void register(String id, TriPredicate<TriggerSignal, JsonObject, FormulaContext> callback) {
        MATCHERS.put(id, callback);
    }

    public static boolean matches(String id, TriggerSignal signal, JsonObject params) {
        TriPredicate<TriggerSignal, JsonObject, FormulaContext> callback = MATCHERS.get(id);
        if (callback == null) return unknown(id);
        try {
            return callback.test(signal, params, signal.context().formula());
        } catch (Exception exception) {
            return failed(id, exception);
        }
    }

    public static void clear() {
        MATCHERS.clear();
    }

    private static boolean unknown(String id) {
        MiXianTu.LOGGER.warn("Unknown KubeJS trigger '{}'; the trigger does not match", id);
        return false;
    }

    private static boolean failed(String id, Exception exception) {
        MiXianTu.LOGGER.error("KubeJS trigger '{}' failed; the trigger does not match", id, exception);
        return false;
    }
}
