package com.iafenvoy.mxt.data.storage;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Content that declares storage: the definition lists the kinds it keeps, and each kind's class is the value it
 * fills. The values themselves live with the instance that owns them, in a {@link DataStorageHolder} carried by
 * that family's attachment.
 */
public interface DataStorageDeclaration {
    // The data-pack key of this list stays "components".
    List<DataStorage> storages();

    default boolean declares(DataStorage value) {
        return this.storages().stream().anyMatch(kind -> kind.getClass() == value.getClass());
    }

    // One value per (host id, kind) means a kind can be declared once: the first one listed twice is a pack mistake.
    default Optional<DataStorage> duplicateKind() {
        Set<Class<?>> seen = new HashSet<>();
        for (DataStorage kind : this.storages()) if (!seen.add(kind.getClass())) return Optional.of(kind);
        return Optional.empty();
    }
}
