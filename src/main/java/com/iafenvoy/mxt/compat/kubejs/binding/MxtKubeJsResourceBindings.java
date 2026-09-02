package com.iafenvoy.mxt.compat.kubejs.binding;

import com.google.gson.JsonElement;
import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtKubeJsDataCodec;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Common resource operations exposed as {@code MxtResources}.
 */
public final class MxtKubeJsResourceBindings {
    @Info("Consumes a datapack-format resource-cost array through the normal atomic transaction path.")
    public Object consume(Entity entity, JsonElement costs) {
        List<ResourceCost> decoded = MxtKubeJsDataCodec.decode(ResourceCost.LIST_CODEC, costs, entity.level().registryAccess());
        return MxtKubeJsApi.tryConsumeResources(entity, decoded, FormulaContext.of(entity));
    }
}
