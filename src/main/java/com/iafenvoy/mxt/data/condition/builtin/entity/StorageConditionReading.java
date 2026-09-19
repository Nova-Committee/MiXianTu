package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.storage.DataStorage;
import com.iafenvoy.mxt.data.storage.DataStorageDeclaration;
import com.iafenvoy.mxt.data.storage.DataStorageHolder;
import com.iafenvoy.mxt.registry.MxtDataStorageHosts;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * The read side of the addressing {@code mxt:modify_storage} writes with: one host, named by the data-pack
 * registry it lives in and its id, resolved against the entity a condition is testing. Only a kind the host
 * declares may be read, the same rule the writer enforces, so a family that keeps no storage, an id that is not
 * a host of it and a kind the host does not declare all mean "nothing to read" instead of an error.
 *
 * <p>The declaration lives in a data-pack registry, which only exists while a server runs, so a client
 * evaluating a condition without one - a tooltip on a multiplayer client - reads nothing as well.</p>
 */
record StorageConditionReading(Identifier id, DataStorageHolder holder, DataStorageDeclaration definition) {
    /**
     * Resolves one host on this entity; empty when there is nothing that could be read.
     */
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

    /**
     * The kind this host declares, which is what makes a read legal; empty when it declares no such kind.
     */
    <T extends DataStorage> Optional<T> declared(Class<T> kind) {
        return this.definition.storages().stream().filter(kind::isInstance).map(kind::cast).findFirst();
    }

    /**
     * The stored value of a kind; empty when the host declares it but has never written it.
     */
    <T extends DataStorage> Optional<T> stored(Class<T> kind) {
        return this.holder.get(this.id, kind);
    }

    /**
     * The tick that kind was last written on this host, which is when its countdown started; {@code -1} when the
     * host declares it but has never written it.
     */
    long changedAt(Class<? extends DataStorage> kind) {
        return this.holder.changedAt(this.id, kind);
    }
}
