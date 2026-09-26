package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.data.storage.DataStorageDeclaration;
import com.iafenvoy.mxt.data.storage.DataStorageHolder;
import com.iafenvoy.mxt.registry.MxtDataStorageHosts;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * The read side of the addressing {@code mxt:modify_storage} writes with: one host, named by its data-pack
 * registry family and id. Only a kind the host declares may be read; anything else means "nothing to read".
 */
record StorageConditionReading(Identifier id, DataStorageHolder holder, DataStorageDeclaration definition) {
    static Optional<StorageConditionReading> of(Entity entity, Identifier family, Identifier id) {
        final DataStorageDeclaration definition;
        try {
            definition = MxtDataStorageHosts.definition(family, id).orElse(null);
        } catch (IllegalStateException exception) {
            // No server means no data-pack registries, which is the one case this lookup can fail in.
            return Optional.empty();
        }
        if (definition == null) return Optional.empty();
        return MxtDataStorageHosts.holder(entity, family).map(holder -> new StorageConditionReading(id, holder, definition));
    }

    <T extends DataStorage> Optional<T> declared(Class<T> kind) {
        return this.definition.storages().stream().filter(kind::isInstance).map(kind::cast).findFirst();
    }

    // Empty when the host declares this kind but has never written it, which callers read as "not set".
    <T extends DataStorage> Optional<T> stored(Class<T> kind) {
        return this.holder.get(this.id, kind);
    }
}
