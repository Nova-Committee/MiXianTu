package com.iafenvoy.mxt.integration.kubejs.binding;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.attachment.ResourceHolderComponent;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.integration.kubejs.callback.MxtJsValueCallbacks;
import com.iafenvoy.mxt.integration.kubejs.callback.QuadFunction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.core.Holder;

import java.util.function.BiFunction;

/**
 * KubeJS registrations for script-backed number and resource value providers.
 */
public final class MxtKubeJsValueBindings {
    @Info("Registers a number provider. Datapack type: mxt:js")
    public void number(String id, BiFunction<FormulaContext, JsonObject, Double> callback) {
        MxtJsValueCallbacks.registerNumber(id, callback);
    }

    @Info("Registers a resource value provider. Datapack type: mxt:js")
    public void resourceValue(String id, QuadFunction<ResourceHolderComponent, Holder<Resource>, FormulaContext, JsonObject, Double> callback) {
        MxtJsValueCallbacks.registerResource(id, callback);
    }
}
