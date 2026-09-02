package com.iafenvoy.mxt.compat.kubejs.binding;

import com.google.gson.JsonObject;
import com.iafenvoy.mxt.attachment.ResourceHolderAttachment;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.compat.kubejs.callback.MxtJsValueCallbacks;
import com.iafenvoy.mxt.compat.kubejs.callback.QuadFunction;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtKubeJsDataCodec;
import com.iafenvoy.mxt.data.resource.ResourceValueProvider;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

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
    public void resourceValue(String id, QuadFunction<ResourceHolderAttachment, Holder<Resource>, FormulaContext, JsonObject, Double> callback) {
        MxtJsValueCallbacks.registerResource(id, callback);
    }

    @Info("Decodes and evaluates any registered number provider definition.")
    public double evaluateNumber(Entity entity, JsonObject definition) {
        NumberProvider provider = MxtKubeJsDataCodec.decode(NumberProvider.CODEC, definition, entity.level().registryAccess());
        double value = provider.evaluate(FormulaContext.of(entity));
        return provider.assertFinite(value) ? value : 0.0D;
    }

    @Info("Decodes and evaluates any registered resource-value provider definition.")
    public double evaluateResource(LivingEntity entity, String resource, JsonObject definition) {
        Identifier id = Identifier.tryParse(resource);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + resource);
        Holder<Resource> holder = MxtDatapackRegistries.holder(MxtResourceKeys.RESOURCE, id)
                .orElseThrow(() -> new IllegalArgumentException("Unknown MXT resource: " + resource));
        ResourceValueProvider provider = MxtKubeJsDataCodec.decode(ResourceValueProvider.CODEC, definition, entity.level().registryAccess());
        return provider.resolve(entity, holder, FormulaContext.of(entity));
    }
}
