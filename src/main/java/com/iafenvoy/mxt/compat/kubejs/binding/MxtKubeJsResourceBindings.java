package com.iafenvoy.mxt.compat.kubejs.binding;

import com.google.gson.JsonElement;
import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.compat.kubejs.codec.MxtKubeJsDataCodec;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Common resource operations exposed as {@code MxtResources}.
 */
public final class MxtKubeJsResourceBindings {
    @Info("Simulates the whole cost array, then attempts each deduction in order; real failures warn and continue.")
    public Object consume(Entity entity, JsonElement costs) {
        List<Cost> decoded = MxtKubeJsDataCodec.decodeCached(Cost.LIST_CODEC, costs, entity.level().registryAccess());
        return MxtKubeJsApi.tryConsumeResources(entity, decoded, FormulaContext.of(entity));
    }
}
