package com.iafenvoy.mxt.compat.kubejs;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * Instance API exposed to KubeJS as {@code Mxt.api()}.
 */
public final class MxtKubeJsApiFacade {
    public boolean addCultivation(LivingEntity entity, String resource, double amount) {
        return MxtKubeJsApi.addCultivation(entity, id(resource), amount);
    }

    public boolean reclaimSoul(Entity entity) {
        return MxtKubeJsApi.reclaimSoul(entity);
    }

    public Object useAbility(Entity entity, String ability) {
        Identifier id = id(ability);
        FormulaContext context = FormulaContext.of(entity);
        return MxtKubeJsApi.useAbility(entity, id, context);
    }

    public Object tryBreakthrough(LivingEntity entity, String resource) {
        return MxtKubeJsApi.tryBreakthrough(entity, id(resource), FormulaContext.of(entity));
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
        FormulaContext context = FormulaContext.of(entity);
        return MxtKubeJsApi.applyCurse(entity, id(curse), stacks, source, context);
    }

    /**
     * Parses the same {@code costs: [{id, amount}]} shape used by datapack definitions.
     */
    public Object tryConsumeResources(Entity entity, String costs) {
        try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, entity.level().registryAccess());
            List<ResourceCost> decoded = ResourceCost.LIST_CODEC.parse(ops, JsonParser.parseString(costs))
                    .getOrThrow(error -> new IllegalArgumentException("Invalid resource costs: " + error));
            FormulaContext context = FormulaContext.of(entity);
            return MxtKubeJsApi.tryConsumeResources(entity, decoded, context);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid MXT resource costs", exception);
        }
    }

    public Object getAura(Level level, BlockPos position) {
        return MxtKubeJsApi.aura(level, position);
    }

    public String addAuraBox(Level level, String zone, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int priority) {
        return MxtKubeJsApi.addAuraBox(level, id(zone), minX, minY, minZ, maxX, maxY, maxZ, priority);
    }

    public boolean removeAuraArea(Level level, String area) {
        return MxtKubeJsApi.removeAuraArea(level, area);
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }
}
