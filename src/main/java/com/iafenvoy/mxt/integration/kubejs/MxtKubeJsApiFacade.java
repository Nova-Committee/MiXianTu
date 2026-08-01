package com.iafenvoy.mxt.integration.kubejs;

import com.google.gson.JsonParser;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.integration.MxtKubeJsApi;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.FormulaContexts;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Instance API exposed to KubeJS as {@code Mxt.api()}.
 */
public final class MxtKubeJsApiFacade {
    public boolean addCultivation(LivingEntity entity, double amount) {
        return MxtKubeJsApi.addCultivation(entity, amount);
    }

    public boolean reclaimSoul(Entity entity) {
        return MxtKubeJsApi.reclaimSoul(entity);
    }

    public Object useAbility(Entity entity, String ability) {
        Identifier id = id(ability);
        FormulaContext context = entity instanceof LivingEntity living ? FormulaContexts.forEntity(living) : FormulaContext.EMPTY;
        return MxtKubeJsApi.useAbility(entity, id, context);
    }

    public Object tryBreakthrough(LivingEntity entity, String realm) {
        return MxtKubeJsApi.tryBreakthrough(entity, id(realm), FormulaContexts.forEntity(entity));
    }

    public boolean removeCurse(Entity entity, String curse) {
        return MxtKubeJsApi.removeCurse(entity, id(curse));
    }

    /**
     * Applies a named curse through the same transactional path used by abilities and items.
     */
    public Object applyCurse(Entity entity, String curse, int stacks, String source) {
        if (stacks < 1) throw new IllegalArgumentException("Curse stacks must be positive");
        if (source == null || source.isBlank()) throw new IllegalArgumentException("Curse source must not be blank");
        FormulaContext context = entity instanceof LivingEntity living ? FormulaContexts.forEntity(living) : FormulaContext.EMPTY;
        return MxtKubeJsApi.applyCurse(entity, id(curse), stacks, source, context);
    }

    /**
     * Parses the same {@code costs: [{id, amount}]} shape used by datapack definitions.
     */
    public Object tryConsumeResources(Entity entity, String costs) {
        try {
            List<ResourceCost> decoded = ResourceCost.CODEC.listOf().parse(JsonOps.INSTANCE, JsonParser.parseString(costs))
                    .getOrThrow(error -> new IllegalArgumentException("Invalid resource costs: " + error));
            FormulaContext context = entity instanceof LivingEntity living ? FormulaContexts.forEntity(living) : FormulaContext.EMPTY;
            return MxtKubeJsApi.tryConsumeResources(entity, decoded, context);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid MXT resource costs", exception);
        }
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }
}
