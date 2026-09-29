package com.iafenvoy.mxt.compat.kubejs.binding;

import com.iafenvoy.mxt.compat.kubejs.MxtKubeJsApi;
import com.iafenvoy.mxt.runtime.progression.ProgressionAdminService;
import com.iafenvoy.mxt.runtime.progression.ProgressionMastery;
import dev.latvian.mods.kubejs.typings.Info;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Progression operations exposed as {@code MxtProgression}, asked by owner: a learned technique, a creature's
 * profile or anything else owning a chain answers the same way. Reads work on either side; the write goes through
 * the service the operator command uses, so a script cannot store a level a promotion would not.
 */
public final class MxtKubeJsProgressionBindings {
    @Info("The level recorded for that owner, or null when the entity has no record for it yet - it may still stand on the owner's entry level, which current answers.")
    public @Nullable String level(Entity entity, String owner) {
        Identifier level = MxtKubeJsApi.progressionLevel(entity, id(owner));
        return level == null ? null : level.toString();
    }

    @Info("The level the entity effectively stands on for that owner: the record, or the owner's entry level while it never advanced. Null when the entity does not hold that owner.")
    public @Nullable String current(Entity entity, String owner) {
        Identifier level = MxtKubeJsApi.progressionCurrent(entity, id(owner));
        return level == null ? null : level.toString();
    }

    @Info("The level after the current one on the same chain, or null at the top of the chain and when the entity does not hold that owner.")
    public @Nullable String next(Entity entity, String owner) {
        Identifier level = MxtKubeJsApi.progressionNext(entity, id(owner));
        return level == null ? null : level.toString();
    }

    @Info("How far the next level is: {have, required, resource}. Null when the chain has no next level, the owner names no mastery resource, or the entity does not hold that owner.")
    public @Nullable ProgressionMastery mastery(Entity entity, String owner) {
        return MxtKubeJsApi.progressionMastery(entity, id(owner));
    }

    @Info("Stores a level and rebuilds what it grants, the same way an operator does it. {changed, failure}, where failure is UNKNOWN_OWNER, FOREIGN_LEVEL, SAME_LEVEL, UNKNOWN_LEVEL or SERVER_ONLY.")
    public ProgressionAdminService.Result setLevel(LivingEntity entity, String owner, String level) {
        return MxtKubeJsApi.setProgressionLevel(entity, id(owner), id(level), false);
    }

    private static Identifier id(String raw) {
        Identifier id = Identifier.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("Invalid MXT identifier: " + raw);
        return id;
    }
}
