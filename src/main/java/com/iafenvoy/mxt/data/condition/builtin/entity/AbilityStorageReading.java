package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.data.storage.DataStorageHolder;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * The read side of the addressing the ability storage action writes with: one ability, named by its own id. Only a
 * kind the ability declares may be read; anything else means "nothing to read".
 */
record AbilityStorageReading(Identifier id, Optional<DataStorageHolder> holder, Ability definition) {
    static Optional<AbilityStorageReading> of(Entity entity, Identifier id) {
        final Ability definition;
        try {
            definition = MxtDatapackRegistries.get(MxtResourceKeys.ABILITY, id).orElse(null);
        } catch (IllegalStateException exception) {
            // No server means no data-pack registries, which is the one case this lookup can fail in.
            return Optional.empty();
        }
        if (definition == null) return Optional.empty();
        // Read-only, so an entity that never had ability state is not given an empty attachment to carry around.
        // Missing state is not a failure: every kind reads as "nothing written yet" instead.
        return Optional.of(new AbilityStorageReading(id,
                entity.getExistingData(MxtAttachments.ABILITY_HOLDER).map(attachment -> attachment.storage()),
                definition));
    }

    <T extends DataStorage> Optional<T> declared(Class<T> kind) {
        return this.definition.storages().stream().filter(kind::isInstance).map(kind::cast).findFirst();
    }

    // Empty when the ability declares this kind but has never written it, which callers read as "not set".
    <T extends DataStorage> Optional<T> stored(Class<T> kind) {
        return this.holder.flatMap(storage -> storage.get(this.id, kind));
    }
}
