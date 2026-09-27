package com.iafenvoy.mxt.compat.kubejs.binding;

import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.runtime.cultivation.TechniqueService;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Runtime technique operations exposed as {@code MxtTechniques}. A technique is learned as a whole and carries its
 * own skill stage; learning grants its attributes and abilities, forgetting takes both away. Everything goes through
 * the services the item and the data pack actions use, so a script cannot skip the learn condition or the
 * exclusive-tag conflict rules. Reads work on either side; state changes are server-only and say so in the result.
 */
public final class MxtKubeJsTechniqueBindings {
    @Info("Every technique the entity has learned, sorted, whether or not the definition still exists.")
    public List<String> list(Entity entity) {
        return MxtKubeJsApi.techniques(entity);
    }

    @Info("Whether the entity has learned that technique.")
    public boolean has(Entity entity, String technique) {
        return MxtKubeJsApi.hasTechnique(entity, id(technique));
    }

    @Info("The skill stage that technique is at, or null when it is not learned or has no stage record yet.")
    public @Nullable String stage(Entity entity, String technique) {
        Identifier stage = MxtKubeJsApi.techniqueStage(entity, id(technique));
        return stage == null ? null : stage.toString();
    }

    @Info("Learns a technique through the authoritative service and answers what it did: {changed, failure}, where failure is DISABLED, ALREADY_LEARNED, CONFLICT, CONDITIONS, CANCELLED or SERVER_ONLY.")
    public TechniqueService.Result learn(LivingEntity entity, String technique) {
        return MxtKubeJsApi.learnTechnique(entity, id(technique));
    }

    @Info("Forgets a technique and its own stage record, rebuilding what it granted; the realm, the progress, the resources and the running method are untouched. {changed, failure} with ABSENT when the entity does not have it.")
    public TechniqueService.Result forget(LivingEntity entity, String technique) {
        return MxtKubeJsApi.forgetTechnique(entity, id(technique));
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }
}
