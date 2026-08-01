package com.iafenvoy.mxt.util.formula;

import com.iafenvoy.mxt.registry.MxtAttachments;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds formula variables only from authoritative entity state.
 */
public final class FormulaContexts {
    private FormulaContexts() {
    }

    public static FormulaContext forEntity(LivingEntity entity) {
        return forEntity(entity, Map.of());
    }

    public static FormulaContext forEntity(LivingEntity entity, Map<String, Double> extra) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        if (entity != null) populate(values, "caster_", entity);
        extra.forEach((key, value) -> {
            if (value != null && Double.isFinite(value)) values.put(key, value);
        });
        return new FormulaContext(values);
    }

    public static FormulaContext forEntities(LivingEntity caster, LivingEntity target, Map<String, Double> extra) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>();
        if (caster != null) populate(values, "caster_", caster);
        if (target != null) populate(values, "target_", target);
        extra.forEach((key, value) -> {
            if (value != null && Double.isFinite(value)) values.put(key, value);
        });
        return new FormulaContext(values);
    }

    private static void populate(Map<String, Double> target, String prefix, LivingEntity entity) {
        target.put(prefix + "health", (double) entity.getHealth());
        target.put(prefix + "max_health", (double) entity.getMaxHealth());
        target.put(prefix + "level", entity instanceof ServerPlayer player ? (double) player.experienceLevel : 0.0D);
        entity.getData(MxtAttachments.RESOURCE_HOLDER).values().forEach((id, value) -> target.put(prefix + name(id), value));
        for (AttributeInstance instance : entity.getAttributes().getSyncableAttributes()) {
            Identifier id = BuiltInRegistries.ATTRIBUTE.getKey(instance.getAttribute().value());
            if (id != null) target.put(prefix + name(id), instance.getValue());
        }
    }

    private static String name(Identifier id) {
        return (id.getNamespace() + "_" + id.getPath()).replace('/', '_').replace('.', '_').replace('-', '_');
    }
}
