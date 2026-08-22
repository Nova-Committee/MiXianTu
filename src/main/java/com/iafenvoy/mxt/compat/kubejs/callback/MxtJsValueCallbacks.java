package com.iafenvoy.mxt.compat.kubejs.callback;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.ResourceHolderComponent;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;

/**
 * Callback storage for JavaScript numeric and resource-value providers.
 */
public final class MxtJsValueCallbacks {
    private static final Map<String, BiFunction<FormulaContext, JsonObject, Double>> NUMBER = new ConcurrentHashMap<>();
    private static final Map<String, QuadFunction<ResourceHolderComponent, Holder<Resource>, FormulaContext, JsonObject, Double>> RESOURCE = new ConcurrentHashMap<>();

    private MxtJsValueCallbacks() {
    }

    public static void registerNumber(String id, BiFunction<FormulaContext, JsonObject, Double> callback) {
        NUMBER.put(id, callback);
    }

    public static void registerResource(String id, QuadFunction<ResourceHolderComponent, Holder<Resource>, FormulaContext, JsonObject, Double> callback) {
        RESOURCE.put(id, callback);
    }

    public static double number(String id, FormulaContext context, JsonObject params) {
        BiFunction<FormulaContext, JsonObject, Double> callback = NUMBER.get(id);
        if (callback == null) return unknown("number provider", id);
        try {
            return finite("number provider", id, callback.apply(context, params));
        } catch (Exception exception) {
            return failed("number provider", id, exception);
        }
    }

    public static double resource(String id, ResourceHolderComponent holder, Holder<Resource> resource, FormulaContext context, JsonObject params) {
        QuadFunction<ResourceHolderComponent, Holder<Resource>, FormulaContext, JsonObject, Double> callback = RESOURCE.get(id);
        if (callback == null) return unknown("resource value provider", id);
        try {
            return finite("resource value provider", id, callback.apply(holder, resource, context, params));
        } catch (Exception exception) {
            return failed("resource value provider", id, exception);
        }
    }

    public static void clear() {
        NUMBER.clear();
        RESOURCE.clear();
    }

    private static double finite(String type, String id, Double value) {
        if (value != null && Double.isFinite(value)) return value;
        MiXianTu.LOGGER.warn("KubeJS {} '{}' returned a non-finite value; using 0", type, id);
        return 0.0D;
    }

    private static double unknown(String type, String id) {
        MiXianTu.LOGGER.warn("Unknown KubeJS {} '{}'", type, id);
        return 0.0D;
    }

    private static double failed(String type, String id, Exception exception) {
        MiXianTu.LOGGER.error("KubeJS {} '{}' failed", type, id, exception);
        return 0.0D;
    }
}
